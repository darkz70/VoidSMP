from __future__ import annotations
import asyncio
import logging
import time
import aiohttp
from .config import Config
from .state import State
from .telegram import Telegram, TelegramError

log = logging.getLogger("voidsmp.bot")

# A broken network can fail every few seconds for hours. Keep the console readable.
LOG_THROTTLE_SECONDS = 300.0

# What to actually do about a transport failure. Keys come from telegram.network_reason.
NETWORK_HINTS = {
    "timeout": "контейнер не достаёт api.telegram.org (таймаут). Обычно это блокировка исходящего трафика на ноде хостинга",
    "unreachable": "соединение с api.telegram.org не устанавливается (сеть/файрвол ноды)",
    "dns": "не резолвится api.telegram.org (DNS в контейнере)",
    "tls": "TLS-рукопожатие с api.telegram.org не проходит (перехват трафика или неверное время в контейнере)",
    "proxy": "прокси TELEGRAM_PROXY не работает или не пускает к Telegram",
    "disconnected": "api.telegram.org разорвал соединение",
    "bad_response": "вместо ответа Bot API пришёл не-JSON (подмена ответа прокси/порталом)",
    "network": "сетевая ошибка при обращении к api.telegram.org",
}

NETWORK_FIX = ("Что делать: включи в профиле Bothost «Зарубежный IP для API и нейросетей» и сделай повторный деплой, "
               "либо задай TELEGRAM_PROXY (http:// или socks5://) / TELEGRAM_API на своё зеркало Bot API. "
               "Подробности: BOT_DEPLOY.md, раздел «HTTP 0».")


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
        self.telegram = Telegram(session, config.telegram_api, config.bot_token, config.telegram_proxy)
        self.stop_event = asyncio.Event()
        # Set by /v1/sync when the plugin queues work, so delivery does not wait out a timer.
        self.outbox_ready = asyncio.Event()
        self.offset = int(state.get("offset", "0"))
        self._identity_lock = asyncio.Lock()
        # Log the first failure of a streak, then at most one line per LOG_THROTTLE_SECONDS.
        self._warned_at = 0.0
        self._warned_reason = ""
        self._suppressed = 0

    async def identify(self):
        async with self._identity_lock:
            if not self.telegram.ready:
                await self.telegram.connect()
                if self.state.get("bot_id") not in {"", str(self.telegram.bot_id)}:
                    raise RuntimeError("State belongs to another bot. Use a fresh BOT_STATE_PATH/DATA_DIR after explicitly changing bots.")
                self.state.set("bot_id", str(self.telegram.bot_id))
                if not await self.telegram.set_commands():
                    log.warning("Could not publish the Telegram command menu; commands still work via /help")

    def warn_transport(self, exc: TelegramError, prefix: str):
        """Log a Telegram failure with its cause, without flooding the hosting console."""
        reason = exc.reason
        now = time.monotonic()
        if reason == self._warned_reason and now - self._warned_at < LOG_THROTTLE_SECONDS:
            self._suppressed += 1
            return
        repeated = f" (+{self._suppressed} подавлено)" if self._suppressed else ""
        self._warned_at, self._warned_reason, self._suppressed = now, reason, 0
        if exc.status:
            log.warning("%s (HTTP %s)%s; retrying without logging credentials or updates",
                        prefix, exc.status, repeated)
            if exc.status in {401, 403}:
                log.error("Telegram отверг токен. Перевыпусти его в @BotFather и обнови токен в панели Bothost.")
            return
        hint = NETWORK_HINTS.get(reason, NETWORK_HINTS["network"])
        log.warning("%s (HTTP 0, %s): %s%s", prefix, reason, hint, repeated)
        log.warning(NETWORK_FIX)

    def note_recovered(self):
        if self._warned_reason:
            log.info("Telegram снова доступен после ошибки %s", self._warned_reason)
            self._warned_reason, self._suppressed = "", 0

    async def polling(self):
        delay = 1.0
        while not self.stop_event.is_set():
            try:
                await self.identify()
                updates = await self.telegram.updates(self.offset)
                self.note_recovered()
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
                # A non-empty batch means Telegram has more to say: re-poll at once.
                delay = 0.0 if updates else 1.0
            except asyncio.CancelledError:
                raise
            except TelegramError as exc:
                self.telegram.ready = False
                delay = exc.retry_after or (30.0 if exc.status in {401, 403, 409} else min(delay * 2, 30))
                self.warn_transport(exc, "Telegram unavailable")
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
        delay = 0.0
        while not self.stop_event.is_set():
            try:
                await self.identify()
                # Clear first: a signal arriving after the query must still wake the wait below.
                self.outbox_ready.clear()
                events = self.state.pending_outbox(20)
                if not events:
                    delay = 0.0
                    # Sleep until the plugin queues something, or 1s as a safety net.
                    await self.wait_outbox(1.0)
                    continue
                for event in events:
                    if self.stop_event.is_set():
                        return
                    await self.deliver(event)
                    self.state.mark_outbox_delivered(event["event_id"])
                # More may already be queued: keep draining without a pause.
                delay = 0.0
            except asyncio.CancelledError:
                raise
            except TelegramError as exc:
                delay = exc.retry_after or min(delay * 2, 30)
                self.warn_transport(exc, "Telegram delivery failed; outbox retained")
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

    async def wait_outbox(self, timeout: float):
        """Block until /v1/sync signals new work, the timeout expires, or shutdown starts."""
        waiter = asyncio.create_task(self.outbox_ready.wait())
        stopper = asyncio.create_task(self.stop_event.wait())
        try:
            await asyncio.wait({waiter, stopper}, timeout=timeout, return_when=asyncio.FIRST_COMPLETED)
        finally:
            waiter.cancel()
            stopper.cancel()
            await asyncio.gather(waiter, stopper, return_exceptions=True)

    async def sleep(self, seconds: float):
        if seconds <= 0:
            # Yield so a shutdown or a wake-up is observed, without a real delay.
            await asyncio.sleep(0)
            return
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
