from __future__ import annotations
import asyncio
import time
import aiohttp


class TelegramError(Exception):
    def __init__(self, status: int, retry_after: float = 0):
        super().__init__(f"Telegram request failed (HTTP {status})")
        self.status = status
        self.retry_after = retry_after


class Telegram:
    """The sole getUpdates consumer in external mode. Never delete a webhook implicitly."""
    def __init__(self, session: aiohttp.ClientSession, api: str, token: str):
        self.session, self.api, self._token = session, api.rstrip("/"), token
        self.bot_id = 0
        self.username = ""
        self.ready = False
        self.last_poll = 0.0

    async def call(self, method: str, payload: dict, timeout: float = 40):
        # Fixed API origin in production; no redirects that could forward the token.
        try:
            async with self.session.post(f"{self.api}/bot{self._token}/{method}", json=payload,
                                         timeout=aiohttp.ClientTimeout(total=timeout), allow_redirects=False) as r:
                if r.status != 200:
                    retry = 0.0
                    if r.status == 429:
                        try:
                            data = await r.json()
                            retry = min(60.0, max(1.0, float(data.get("parameters", {}).get("retry_after", 1))))
                        except (ValueError, KeyError, aiohttp.ClientError):
                            retry = 1.0
                    raise TelegramError(r.status, retry)
                data = await r.json(content_type=None)
        except (aiohttp.ClientError, asyncio.TimeoutError) as exc:
            # aiohttp exception text may contain the token-bearing URL.
            raise TelegramError(0) from None
        if not isinstance(data, dict) or data.get("ok") is not True:
            raise TelegramError(0)
        return data.get("result")

    async def connect(self):
        me = await self.call("getMe", {}, 20)
        if not isinstance(me, dict) or not isinstance(me.get("id"), int) or me["id"] <= 0:
            raise TelegramError(0)
        self.bot_id, self.username = me["id"], me.get("username", "")
        info = await self.call("getWebhookInfo", {}, 20)
        if not isinstance(info, dict) or info.get("url"):
            self.ready = False
            raise RuntimeError("Telegram webhook is configured; external bot requires one long-polling owner. Resolve it explicitly before startup.")
        self.ready = True

    async def updates(self, offset: int):
        result = await self.call("getUpdates", {"offset": offset, "timeout": 25,
                                                "allowed_updates": ["message", "callback_query"]}, 40)
        if not isinstance(result, list):
            raise TelegramError(0)
        self.last_poll = time.monotonic()
        return result

    async def send(self, chat_id: int, text: str, keyboard=None):
        body = {"chat_id": chat_id, "text": text, "link_preview_options": {"is_disabled": True}}
        if keyboard is not None:
            body["reply_markup"] = {"inline_keyboard": keyboard}
        result = await self.call("sendMessage", body, 20)
        if not isinstance(result, dict) or not isinstance(result.get("message_id"), int):
            raise TelegramError(0)
        return result["message_id"]

    async def edit(self, chat_id: int, message_id: int, text: str):
        try:
            await self.call("editMessageText", {"chat_id": chat_id, "message_id": message_id,
                                                "text": text, "reply_markup": {"inline_keyboard": []}}, 20)
        except TelegramError as exc:
            # Telegram returns 400 for identical text; an idempotent retry is successful.
            if exc.status != 400:
                raise

    async def answer(self, callback_id: str, text: str):
        await self.call("answerCallbackQuery", {"callback_query_id": callback_id, "text": text}, 20)
