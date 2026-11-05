from __future__ import annotations
import asyncio
import logging
import time
import aiohttp
from .bridge import Bridge
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
    def __init__(self, config: Config, session: aiohttp.ClientSession, state: State):
        self.config, self.state = config, state
        self.telegram = Telegram(session, config.telegram_api, config.bot_token)
        self.bridge = Bridge(session, config.plugin_url, config.bridge_secret)
        self.stop_event = asyncio.Event()
        self.instance = state.get("instance")
        self.cursor = int(state.get("cursor", "0"))
        self.offset = int(state.get("offset", "0"))
        self._identity_lock = asyncio.Lock()

    async def identify(self):
        async with self._identity_lock:
            if not self.telegram.ready:
                await self.telegram.connect()
                if self.state.get("bot_id") not in {"", str(self.telegram.bot_id)}:
                    raise RuntimeError("State belongs to another bot. Use a fresh BOT_STATE_PATH after explicitly changing bots.")
                self.state.set("bot_id", str(self.telegram.bot_id))

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
                        await self.ensure_instance()
                        # Do not advance offset until the plugin durably accepted the command.
                        await self.bridge.command(self.telegram.bot_id, self.instance, self.cursor, event)
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
            except ConnectionError:
                self.bridge.connected = False
                log.warning("Plugin bridge unavailable; Telegram offset retained")
                delay = min(delay * 2, 15)
            except RuntimeError as exc:
                log.error("Startup identity/webhook conflict: %s", exc)
                self.stop_event.set()
                return
            await self.sleep(delay)

    async def ensure_instance(self):
        value = await self.bridge.poll(self.telegram.bot_id, self.telegram.username,
                                       self.instance, self.cursor, self.telegram.ready)
        self.apply_poll(value)

    def apply_poll(self, value: dict):
        instance = value.get("instance_id")
        if not isinstance(instance, str) or not isinstance(value.get("events"), list):
            raise ConnectionError("Invalid bridge event envelope")
        if instance != self.instance or value.get("reset") is True:
            self.state.reset_instance(instance)
            self.instance, self.cursor = instance, 0
            # reset=True can also mean an outbox gap within this instance.

    async def outbox(self):
        delay = 1.0
        while not self.stop_event.is_set():
            try:
                await self.identify()
                value = await self.bridge.poll(self.telegram.bot_id, self.telegram.username,
                                               self.instance, self.cursor, self.telegram.ready)
                self.apply_poll(value)
                for event in value["events"]:
                    if self.stop_event.is_set():
                        return
                    await self.deliver(event)
                    seq = event.get("seq")
                    # Plugin currently identifies events by event_id; cursor is server sequence.
                    # Sequence is supplied inside each payload by the plugin.
                    if isinstance(seq, int):
                        self.cursor = seq
                        self.state.set("cursor", str(self.cursor))
                delay = 1.0
                await self.sleep(1.0)
            except asyncio.CancelledError:
                raise
            except TelegramError as exc:
                delay = exc.retry_after or min(delay * 2, 30)
                log.warning("Telegram delivery failed (HTTP %s); cursor retained", exc.status)
            except (ConnectionError, RuntimeError, ValueError, KeyError, TypeError) as exc:
                self.bridge.connected = False
                delay = min(delay * 2, 15)
                log.warning("Bridge/outbox unavailable (%s); cursor retained", type(exc).__name__)
            await self.sleep(delay)

    async def deliver(self, event: dict):
        if not isinstance(event, dict):
            raise ValueError("Invalid event")
        event_id, kind = event.get("event_id"), event.get("type")
        if not isinstance(event_id, str) or not isinstance(kind, str):
            raise ValueError("Invalid event identity")
        if self.state.processed(event_id):
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
        self.state.mark_processed(event_id, int(time.time()))

    async def sleep(self, seconds: float):
        try:
            await asyncio.wait_for(self.stop_event.wait(), timeout=seconds)
        except TimeoutError:
            pass

    @property
    def healthy(self) -> bool:
        return (self.telegram.ready and self.bridge.connected
                and time.monotonic() - self.telegram.last_poll < 60)

    async def run(self):
        async with asyncio.TaskGroup() as tg:
            tg.create_task(self.polling(), name="telegram-updates")
            tg.create_task(self.outbox(), name="plugin-outbox")
            tg.create_task(self.stop_event.wait(), name="shutdown")
            # A fatal consumer conflict stops the whole service, including health checks.
