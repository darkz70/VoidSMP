from __future__ import annotations
import os
from dataclasses import dataclass, field
from pathlib import Path
from .security import decode_secret


def _secret(env: str, default: str = "") -> str:
    value = os.environ.get(env, "")
    path = os.environ.get(env + "_FILE", "")
    if value and path:
        raise ValueError(f"Set {env} or {env}_FILE, not both")
    if path:
        value = Path(path).read_text(encoding="utf-8").strip()
    return value or default


def _bot_token() -> str:
    # Bothost injects BOT_TOKEN; keep documented aliases for compatibility.
    for name in ("BOT_TOKEN", "TELEGRAM_BOT_TOKEN", "API_TOKEN"):
        value = _secret(name)
        if value:
            return value
    raise ValueError("BOT_TOKEN is required (Bothost provides it from the BotFather token)")


def _state_path() -> Path:
    explicit = os.environ.get("BOT_STATE_PATH", "")
    if explicit:
        return Path(explicit)
    data_dir = os.environ.get("DATA_DIR", "")
    if data_dir:
        return Path(data_dir) / "bot.sqlite3"
    return Path("state/bot.sqlite3")


@dataclass(frozen=True)
class Config:
    bot_token: str = field(repr=False)
    bridge_secret: bytes = field(repr=False)
    state_path: Path = Path("state/bot.sqlite3")
    listen_host: str = "0.0.0.0"
    listen_port: int = 8080
    telegram_api: str = "https://api.telegram.org"

    @classmethod
    def from_env(cls) -> "Config":
        token = _bot_token()
        secret = decode_secret(_secret("BRIDGE_SHARED_SECRET"))
        port = int(os.environ.get("PORT", "8080"))
        if not (1 <= port <= 65535):
            raise ValueError("PORT must be between 1 and 65535")
        return cls(
            bot_token=token,
            bridge_secret=secret,
            state_path=_state_path(),
            listen_host=os.environ.get("HOST", "0.0.0.0"),
            listen_port=port,
            telegram_api=os.environ.get("TELEGRAM_API", "https://api.telegram.org").rstrip("/"),
        )
