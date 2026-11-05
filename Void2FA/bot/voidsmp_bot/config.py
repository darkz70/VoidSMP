from __future__ import annotations
import os
from dataclasses import dataclass, field
from pathlib import Path
from urllib.parse import urlparse
from .security import decode_secret


def _secret(env: str, default: str = "") -> str:
    value = os.environ.get(env, "")
    path = os.environ.get(env + "_FILE", "")
    if value and path:
        raise ValueError(f"Set {env} or {env}_FILE, not both")
    if path:
        value = Path(path).read_text(encoding="utf-8").strip()
    return value or default


@dataclass(frozen=True)
class Config:
    bot_token: str = field(repr=False)
    bridge_secret: bytes = field(repr=False)
    plugin_url: str = "http://127.0.0.1:8765"
    state_path: Path = Path("state/bot.sqlite3")
    health_host: str = "127.0.0.1"
    health_port: int = 8089
    telegram_api: str = "https://api.telegram.org"

    @classmethod
    def from_env(cls) -> "Config":
        token = _secret("BOT_TOKEN")
        if not token:
            raise ValueError("BOT_TOKEN or BOT_TOKEN_FILE is required")
        secret = decode_secret(_secret("BRIDGE_SHARED_SECRET"))
        url = os.environ.get("PLUGIN_BRIDGE_URL", "http://127.0.0.1:8765").rstrip("/")
        parsed = urlparse(url)
        loopback = parsed.hostname in {"127.0.0.1", "::1", "localhost"}
        if parsed.scheme not in {"http", "https"} or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path:
            raise ValueError("PLUGIN_BRIDGE_URL must be an origin without credentials, path or query")
        if parsed.scheme != "https" and not loopback:
            raise ValueError("Remote bridge requires HTTPS; plain HTTP is allowed only on loopback")
        return cls(token, secret, url, Path(os.environ.get("BOT_STATE_PATH", "state/bot.sqlite3")),
                   os.environ.get("HEALTH_HOST", "127.0.0.1"), int(os.environ.get("HEALTH_PORT", "8089")),
                   os.environ.get("TELEGRAM_API", "https://api.telegram.org").rstrip("/"))
