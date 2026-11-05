"""Bothost entrypoint for the VoidSMP Telegram bot.

Bothost installs the root requirements.txt and runs this file. The actual package
lives in Void2FA/bot so the same repository also builds the Paper plugin.
"""
from __future__ import annotations
import sys
from pathlib import Path

BOT_DIR = Path(__file__).resolve().parent / "Void2FA" / "bot"
sys.path.insert(0, str(BOT_DIR))

from voidsmp_bot.__main__ import main  # noqa: E402

if __name__ == "__main__":
    main()
