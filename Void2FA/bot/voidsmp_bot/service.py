from __future__ import annotations
import asyncio
import logging
import time
import aiohttp
from .config import Config
from .state import State
from .telegram import Telegram, TelegramError

log = logging.getLogger("voidsmp.bot")


def normalize(update: dict, bot_id: int, now: int) -> dict | None:
    """Extract Telegram identity facts, never a Minecraft UUID or an auth decision."""
    if not isinstance(update, dict) or not isinstance(update.get("update_id"), int):
        return None
    cb = update.get("callback_query")
    item = cb if isinstance(cb, dict) else update.get("message")
    if not isinstance(item, dict):
        return None
    sender, chat = item.get("from"), item.get("chat")
    message = item.get("message") if cb is not None else item
    if cb is not None:
        chat = message.get("chat") if isinstance(message, dict) else None
    if not isinstance(sender, dict) or not isinstance(chat, dict) or not isinstance(message, dict):
        return None
    uid, cid = sender.get("id"), chat.get("id")
    date = message.get("date")
    if (sender.get("is_bot") is not False or chat.get("type") != "private"
            or not isinstance(uid, int) or uid <= 0 or cid != uid
            or not isinstance(date, int) or date < now - 120 or date > now + 30):
        return None
    base = {"event_id": f"tg:{bot_id}:{update['update_id']}", "user_id": uid,
            "chat_id": cid, "chat_type": "private", "issued_at": date}
    if cb is None:
        text = item.get("text")
        if not isinstance(text, str) or len(text) > 4096:
            return None
        return {**base, "type": "message", "text": text}
    callback_id, data = item.get("id"), item.get("data")
    author = message.get("from")
    if (not isinstance(callback_id, str) or len(callback_id) > 256
            or not isinstance(data, str) or len(data) > 64
            or not isinstance(author, dict) or author.get("id") != bot_id):
        return None
    return {**base, "type": "callback", "callback_id": callback_id, "data": data}


class Service:
    """Telegram transport hosted on Bothost.

    The Minecraft plugin is the HTTP client: it pulls queued Telegram commands and
    pushes outbox events to /v1/sync. This service never decides whether a login is allowed.
    """

    def __init__(self, config: Config, session: aiohttp.ClientSession, state: State):
        self.config, self.state = config, state
        self.telegram = Telegram(session, config.telegram_api, config.bot_token)
        self.stop_event = asyncio.Event()
        self.offset = int(state.get("offset", "0"))
        self._identity_lock = asyncio.Lock()

    async def identify(self):
        async with self._identity_lock:
            if not self.telegram.ready:
                await self.telegram.connect()
                if self.state.get("bot_id") not in {"", str(self.telegram.bot_id)}:
                    raise RuntimeError("State belongs to another bot. Use a fresh BOT_STATE_PATH/DATA_DIR after explicitly changing bots.")
                self.state.set("bot_id", str(self.telegram.bot_id))
                if not await self.telegram.set_commands():
                    log.warning("Could not publish the Telegram command menu; commands still work via /help")

    async def polling(self):
        delay = 1.0
        while not self.stop_event.is_set():
            try:
                await self.identify()
                updates = await self.telegram.updates(self.offset)
                for update in updates:
                    if self.stop_event.is_set():
                        return
                    if not isinstance(update, dict) or not isinstance(update.get("update_id"), int):
                        raise TelegramError(0)
                    uid = update["update_id"]
                    if uid < self.offset:
                        continue
                    event = normalize(update, self.telegram.bot_id, int(time.time()))
                    if event is not None:
                        # Advance the Telegram offset only after the command is durable locally.
                        self.state.enqueue_command(event)
                    self.offset = uid + 1
                    self.state.set("offset", str(self.offset))
                delay = 1.0
            except asyncio.CancelledError:
                raise
            except TelegramError as exc:
                self.telegram.ready = False
                delay = exc.retry_after or (30.0 if exc.status in {401, 403, 409} else min(delay * 2, 30))
                log.warning("Telegram unavailable (HTTP %s); retrying without logging credentials or updates", exc.status)
                if exc.status == 409:
                    # Another consumer owns this token. Stop rather than race it indefinitely.
                    self.stop_event.set()
                    return
            except RuntimeError as exc:
                log.error("Startup identity/webhook conflict: %s", exc)
                self.stop_event.set()
                return
            except Exception as exc:
                log.error("Telegram polling failed (%s); retaining offset", type(exc).__name__)
                delay = min(delay * 2, 30)
            await self.sleep(delay)

    async def outbox(self):
        delay = 1.0
        while not self.stop_event.is_set():
            try:
                await self.identify()
                events = self.state.pending_outbox(20)
                if not events:
                    delay = 1.0
                    await self.sleep(0.5)
                    continue
                for event in events:
                    if self.stop_event.is_set():
                        return
                    await self.deliver(event)
                    self.state.mark_outbox_delivered(event["event_id"])
                delay = 1.0
            except asyncio.CancelledError:
                raise
            except TelegramError as exc:
                delay = exc.retry_after or min(delay * 2, 30)
                log.warning("Telegram delivery failed (HTTP %s); outbox retained", exc.status)
            except Exception as exc:
                delay = min(delay * 2, 15)
                log.warning("Outbox delivery failed (%s); outbox retained", type(exc).__name__)
            await self.sleep(delay)

    async def deliver(self, event: dict):
        if not isinstance(event, dict):
            raise ValueError("Invalid event")
        event_id, kind = event.get("event_id"), event.get("type")
        if not isinstance(event_id, str) or not isinstance(kind, str):
            raise ValueError("Invalid event identity")
        if self.state.outbox_delivered(event_id):
            return
        if kind == "auth_request":
            auth_id = event["auth_id"]
            old = self.state.message(auth_id)
            if old is None:
                keyboard = [[{"text": event["approve_label"], "callback_data": f"v2fa:ok:{auth_id}"},
                             {"text": event["deny_label"], "callback_data": f"v2fa:no:{auth_id}"}]]
                mid = await self.telegram.send(event["chat_id"], event["text"], keyboard)
                self.state.remember_message(auth_id, event["chat_id"], mid)
        elif kind == "auth_edit":
            ref = self.state.message(event["auth_id"])
            if ref and ref[0] == event["chat_id"]:
                await self.telegram.edit(ref[0], ref[1], event["text"])
        elif kind == "message":
            await self.telegram.send(event["chat_id"], event["text"])
        elif kind == "callback_answer":
            await self.telegram.answer(event["callback_id"], event["text"])
        else:
            raise ValueError("Unknown outbox event type")

    async def sleep(self, seconds: float):
        try:
            await asyncio.wait_for(self.stop_event.wait(), timeout=seconds)
        except TimeoutError:
            pass

    @property
    def healthy(self) -> bool:
        return self.telegram.ready and time.monotonic() - self.telegram.last_poll < 60

    async def run(self):
        tasks = [
            asyncio.create_task(self.polling(), name="telegram-updates"),
            asyncio.create_task(self.outbox(), name="plugin-outbox"),
            asyncio.create_task(self.stop_event.wait(), name="shutdown"),
        ]
        try:
            done, _pending = await asyncio.wait(tasks, return_when=asyncio.FIRST_COMPLETED)
            for task in done:
                exc = task.exception()
                if exc is not None:
                    raise exc
        finally:
            self.stop_event.set()
            for task in tasks:
                task.cancel()
            await asyncio.gather(*tasks, return_exceptions=True)
