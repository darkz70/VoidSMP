from __future__ import annotations
import asyncio
import hmac
import json
import re
import time
from aiohttp import web
from .config import Config
from .security import fresh, signature
from .service import Service
from .state import State

MAX_BODY = 262144
MAX_EVENTS = 100
INSTANCE_RE = re.compile(r"^[A-Za-z0-9_-]{8,64}$")
EVENT_ID_RE = re.compile(r"^[A-Za-z0-9:_-]{8,100}$")
NONCE_RE = re.compile(r"^[A-Za-z0-9_-]{16,64}$")
SYNC_PATH = "/v1/sync"


def _reject(status: int) -> web.Response:
    return web.json_response({"error": "request_rejected"}, status=status, headers={"Cache-Control": "no-store"})


def _validate_event(event: object) -> bool:
    if not isinstance(event, dict):
        return False
    event_id, kind = event.get("event_id"), event.get("type")
    if not isinstance(event_id, str) or not EVENT_ID_RE.fullmatch(event_id):
        return False
    if kind in {"message", "auth_request", "auth_edit"}:
        chat_id, text = event.get("chat_id"), event.get("text")
        if not isinstance(chat_id, int) or chat_id <= 0 or not isinstance(text, str) or len(text) > 4096:
            return False
        if kind in {"auth_request", "auth_edit"}:
            auth_id = event.get("auth_id")
            if not isinstance(auth_id, str) or not re.fullmatch(r"[A-Z0-9]{16}", auth_id):
                return False
        if kind == "auth_request":
            for key in ("approve_label", "deny_label"):
                label = event.get(key)
                if not isinstance(label, str) or not (1 <= len(label) <= 64):
                    return False
        return True
    if kind == "callback_answer":
        callback_id, text = event.get("callback_id"), event.get("text")
        return (isinstance(callback_id, str) and 1 <= len(callback_id) <= 256
                and isinstance(text, str) and len(text) <= 200)
    return False


class SyncServer:
    def __init__(self, config: Config, service: Service, state: State):
        self.config, self.service, self.state = config, service, state
        self.lock = asyncio.Lock()

    def app(self) -> web.Application:
        app = web.Application(client_max_size=MAX_BODY)
        app.router.add_post(SYNC_PATH, self.sync)
        app.router.add_get("/health", self.health)
        app.router.add_get("/healthz", self.healthz)
        return app

    async def health(self, _request: web.Request) -> web.Response:
        # Liveness for the hosting platform: do not restart just because Minecraft is offline.
        return web.json_response({"status": "ok", "telegram_ready": self.service.telegram.ready},
                                 headers={"Cache-Control": "no-store"})

    async def healthz(self, _request: web.Request) -> web.Response:
        ready = self.service.healthy
        return web.json_response({"status": "ready" if ready else "not_ready"},
                                 status=200 if ready else 503, headers={"Cache-Control": "no-store"})

    async def sync(self, request: web.Request) -> web.Response:
        try:
            body = await request.content.read(MAX_BODY + 1)
        except (asyncio.TimeoutError, ConnectionError):
            return _reject(400)
        if len(body) > MAX_BODY:
            return _reject(413)
        try:
            ts = int(request.headers.get("X-Void2FA-Timestamp", ""))
        except ValueError:
            return _reject(401)
        nonce = request.headers.get("X-Void2FA-Nonce", "")
        supplied = request.headers.get("X-Void2FA-Signature", "")
        now = int(time.time())
        if (not NONCE_RE.fullmatch(nonce) or not fresh(ts, now)
                or not hmac.compare_digest(signature(self.config.bridge_secret, "request", SYNC_PATH, ts, nonce, body), supplied)):
            return _reject(401)
        if not self.state.accept_nonce(nonce, now):
            return _reject(401)
        try:
            req = json.loads(body)
        except (ValueError, UnicodeDecodeError):
            return _reject(400)
        if not isinstance(req, dict) or req.get("version") != 1:
            return _reject(400)
        instance = req.get("instance_id")
        cursor = req.get("cursor")
        events = req.get("events")
        if (not isinstance(instance, str) or not INSTANCE_RE.fullmatch(instance)
                or not isinstance(cursor, int) or cursor < 0
                or not isinstance(events, list) or len(events) > MAX_EVENTS
                or not all(_validate_event(e) for e in events)):
            return _reject(400)
        async with self.lock:
            sequence = self.state.command_sequence()
            stored_instance = self.state.get("plugin_instance")
            reset = bool(stored_instance and stored_instance != instance) or cursor > sequence
            if reset:
                self.state.reset_plugin(instance)
                cursor = 0
            else:
                if stored_instance != instance:
                    self.state.set("plugin_instance", instance)
                self.state.ack_commands(cursor)
            accepted = self.state.enqueue_events(events)
            commands = self.state.commands_after(cursor, 100)
            sequence = self.state.command_sequence()
        response = {
            "version": 1,
            "bot_id": self.service.telegram.bot_id,
            "bot_username": self.service.telegram.username,
            "telegram_ready": self.service.telegram.ready,
            "reset": reset,
            "cursor": cursor,
            "sequence": sequence,
            "commands": commands,
            "accepted": accepted,
        }
        raw = json.dumps(response, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        response_ts = int(time.time())
        return web.Response(
            body=raw,
            content_type="application/json",
            headers={
                "Cache-Control": "no-store",
                "X-Void2FA-Timestamp": str(response_ts),
                "X-Void2FA-Nonce": nonce,
                "X-Void2FA-Signature": signature(self.config.bridge_secret, "response", SYNC_PATH, response_ts, nonce, raw),
            },
        )
