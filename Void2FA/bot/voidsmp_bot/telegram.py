from __future__ import annotations
import asyncio
import time
from urllib.parse import unquote, urlsplit, urlunsplit
import aiohttp


class TelegramError(Exception):
    """Telegram failure without credentials.

    ``status`` is the HTTP code, or 0 when the request never got a response.
    ``reason`` explains a 0 so the log says what to fix instead of just "HTTP 0".
    """

    def __init__(self, status: int, retry_after: float = 0, reason: str = ""):
        self.status = status
        self.retry_after = retry_after
        self.reason = reason or (f"http_{status}" if status else "network")
        super().__init__(f"Telegram request failed (HTTP {status}; {self.reason})")


def network_reason(exc: BaseException) -> str:
    """Classify a transport failure. Never reads the exception text: it may hold the token URL."""
    # Proxy and TLS/DNS errors subclass ClientConnectorError, so order matters.
    if isinstance(exc, (aiohttp.ClientProxyConnectionError, aiohttp.ClientHttpProxyError)):
        return "proxy"
    if isinstance(exc, (aiohttp.ClientConnectorCertificateError, aiohttp.ClientConnectorSSLError)):
        return "tls"
    if isinstance(exc, aiohttp.ClientConnectorDNSError):
        return "dns"
    if isinstance(exc, asyncio.TimeoutError):
        return "timeout"
    if isinstance(exc, aiohttp.ClientConnectorError):
        return "unreachable"
    if isinstance(exc, aiohttp.ServerDisconnectedError):
        return "disconnected"
    return "network"


def split_proxy(proxy: str) -> tuple[str, str]:
    """aiohttp rejects credentials inside the proxy URL; move them to a header.

    Returns (proxy_url_without_credentials, Proxy-Authorization value).
    SOCKS proxies are handled by the session connector instead, so they are not
    returned here as a per-request proxy.
    """
    if not proxy:
        return "", ""
    parts = urlsplit(proxy)
    if parts.scheme in {"socks5", "socks5h"}:
        return "", ""
    if not parts.username:
        return proxy, ""
    host = parts.hostname or ""
    netloc = f"{host}:{parts.port}" if parts.port else host
    clean = urlunsplit((parts.scheme, netloc, "", "", ""))
    auth = aiohttp.encode_basic_auth(unquote(parts.username), unquote(parts.password or ""))
    return clean, auth


# Bot menu shown in the Telegram chat. Command handling itself stays in the Paper plugin.
BOT_COMMANDS = [
    {"command": "start", "description": "Приветствие и список команд"},
    {"command": "help", "description": "Все команды бота"},
    {"command": "link", "description": "Привязать аккаунт по коду из игры"},
    {"command": "status", "description": "Статус привязки и 2FA"},
    {"command": "lock", "description": "Заблокировать аккаунт"},
    {"command": "unlock", "description": "Разблокировать аккаунт"},
    {"command": "kick", "description": "Завершить текущий вход"},
    {"command": "revoke", "description": "Убрать все устройства и кикнуть"},
    {"command": "devices", "description": "Список доверенных устройств"},
    {"command": "untrust", "description": "Убрать устройство: id или all"},
    {"command": "unlink", "description": "Отвязать аккаунт"},
]


class Telegram:
    """The sole getUpdates consumer in external mode. Never delete a webhook implicitly."""
    def __init__(self, session: aiohttp.ClientSession, api: str, token: str, proxy: str = ""):
        self.session, self.api, self._token = session, api.rstrip("/"), token
        self._proxy, self._proxy_auth = split_proxy(proxy)
        self.bot_id = 0
        self.username = ""
        self.ready = False
        self.last_poll = 0.0
        # Diagnostics for /health and the log. Only short reason codes, never URLs or tokens.
        self.last_error = ""

    async def call(self, method: str, payload: dict, timeout: float = 40):
        # Fixed API origin in production; no redirects that could forward the token.
        options = {}
        if self._proxy:
            options["proxy"] = self._proxy
            if self._proxy_auth:
                options["headers"] = {"Proxy-Authorization": self._proxy_auth}
        try:
            async with self.session.post(f"{self.api}/bot{self._token}/{method}", json=payload,
                                         timeout=aiohttp.ClientTimeout(total=timeout), allow_redirects=False,
                                         **options) as r:
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
        except TelegramError as exc:
            self.last_error = exc.reason
            raise
        except (aiohttp.ClientError, asyncio.TimeoutError) as exc:
            # aiohttp exception text may contain the token-bearing URL.
            self.last_error = network_reason(exc)
            raise TelegramError(0, reason=self.last_error) from None
        except ValueError:
            # Non-JSON body: a captive portal or a proxy answering instead of Telegram.
            self.last_error = "bad_response"
            raise TelegramError(0, reason="bad_response") from None
        if not isinstance(data, dict) or data.get("ok") is not True:
            self.last_error = "bad_response"
            raise TelegramError(0, reason="bad_response")
        self.last_error = ""
        return data.get("result")

    async def connect(self):
        me = await self.call("getMe", {}, 20)
        if not isinstance(me, dict) or not isinstance(me.get("id"), int) or me["id"] <= 0:
            raise TelegramError(0, reason="bad_response")
        self.bot_id, self.username = me["id"], me.get("username", "")
        info = await self.call("getWebhookInfo", {}, 20)
        if not isinstance(info, dict) or info.get("url"):
            self.ready = False
            raise RuntimeError("Telegram webhook is configured; external bot requires one long-polling owner. Resolve it explicitly before startup.")
        self.ready = True

    async def set_commands(self):
        """Publish the command menu. Best-effort: a failure must not stop the transport."""
        try:
            await self.call("setMyCommands", {"commands": BOT_COMMANDS}, 20)
            return True
        except TelegramError:
            return False

    async def updates(self, offset: int):
        result = await self.call("getUpdates", {"offset": offset, "timeout": 25,
                                                "allowed_updates": ["message", "callback_query"]}, 40)
        if not isinstance(result, list):
            raise TelegramError(0, reason="bad_response")
        self.last_poll = time.monotonic()
        return result

    async def send(self, chat_id: int, text: str, keyboard=None):
        body = {"chat_id": chat_id, "text": text, "link_preview_options": {"is_disabled": True}}
        if keyboard is not None:
            body["reply_markup"] = {"inline_keyboard": keyboard}
        result = await self.call("sendMessage", body, 20)
        if not isinstance(result, dict) or not isinstance(result.get("message_id"), int):
            raise TelegramError(0, reason="bad_response")
        return result["message_id"]

    async def edit(self, chat_id: int, message_id: int, text: str):
        try:
            await self.call("editMessageText", {"chat_id": chat_id, "message_id": message_id,
                                                "text": text, "reply_markup": {"inline_keyboard": []}}, 20)
        except TelegramError as exc:
            # Telegram returns 400 for identical text; an idempotent retry is successful.
            if exc.status != 400:
                raise
            self.last_error = ""

    async def answer(self, callback_id: str, text: str):
        await self.call("answerCallbackQuery", {"callback_query_id": callback_id, "text": text}, 20)
