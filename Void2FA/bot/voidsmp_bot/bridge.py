from __future__ import annotations
import json
import aiohttp
from .security import headers, verify_response


class Bridge:
    def __init__(self, session: aiohttp.ClientSession, origin: str, secret: bytes):
        self.session, self.origin, self.secret = session, origin, secret
        self.connected = False

    async def request(self, endpoint: str, payload: dict) -> dict:
        path = "/v1/" + endpoint
        body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        h = headers(self.secret, path, body)
        try:
            async with self.session.post(self.origin + path, data=body, headers=h,
                                         timeout=aiohttp.ClientTimeout(total=15), allow_redirects=False) as r:
                raw = await r.read()
                if r.status != 200:
                    raise ConnectionError(f"Bridge rejected request (HTTP {r.status})")
                verify_response(self.secret, path, h["X-Void2FA-Nonce"], r.headers, raw)
                value = json.loads(raw)
        except (aiohttp.ClientError, TimeoutError, ValueError, UnicodeError) as exc:
            raise ConnectionError("Bridge unavailable or unauthenticated") from None
        if not isinstance(value, dict) or value.get("version") != 1:
            raise ConnectionError("Invalid bridge protocol response")
        self.connected = True
        return value

    async def poll(self, bot_id: int, username: str, instance: str, cursor: int, ready: bool) -> dict:
        return await self.request("poll", {"version": 1, "bot_id": bot_id, "bot_username": username,
                                           "instance_id": instance, "cursor": cursor, "telegram_ready": ready})

    async def command(self, bot_id: int, instance: str, cursor: int, event: dict) -> dict:
        return await self.request("command", {"version": 1, "bot_id": bot_id, "instance_id": instance,
                                               "cursor": cursor, **event})
