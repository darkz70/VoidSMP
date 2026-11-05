from __future__ import annotations
import sqlite3
from pathlib import Path


class State:
    """SQLite stores offsets and message references, never bot tokens or login/link codes."""
    def __init__(self, path: Path):
        path.parent.mkdir(parents=True, exist_ok=True)
        self.db = sqlite3.connect(path)
        self.db.execute("PRAGMA journal_mode=WAL")
        self.db.executescript("""
            CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY, value TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS messages(auth_id TEXT PRIMARY KEY, chat_id INTEGER NOT NULL, message_id INTEGER NOT NULL);
            CREATE TABLE IF NOT EXISTS processed(event_id TEXT PRIMARY KEY, seen_at INTEGER NOT NULL);
        """)
        self.db.commit()

    def get(self, key: str, default: str = "") -> str:
        row = self.db.execute("SELECT value FROM meta WHERE key=?", (key,)).fetchone()
        return row[0] if row else default

    def set(self, key: str, value: str) -> None:
        with self.db:
            self.db.execute("INSERT INTO meta(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value", (key, str(value)))

    def message(self, auth_id: str):
        return self.db.execute("SELECT chat_id,message_id FROM messages WHERE auth_id=?", (auth_id,)).fetchone()

    def remember_message(self, auth_id: str, chat_id: int, message_id: int) -> None:
        with self.db:
            self.db.execute("INSERT OR REPLACE INTO messages VALUES(?,?,?)", (auth_id, chat_id, message_id))

    def processed(self, event_id: str) -> bool:
        return self.db.execute("SELECT 1 FROM processed WHERE event_id=?", (event_id,)).fetchone() is not None

    def mark_processed(self, event_id: str, now: int) -> None:
        with self.db:
            self.db.execute("INSERT OR IGNORE INTO processed VALUES(?,?)", (event_id, now))
            self.db.execute("DELETE FROM processed WHERE seen_at < ?", (now - 86400,))

    def reset_instance(self, instance: str) -> None:
        with self.db:
            self.db.execute("DELETE FROM messages")
            self.db.execute("DELETE FROM processed")
            self.db.execute("INSERT INTO meta(key,value) VALUES('instance',?) ON CONFLICT(key) DO UPDATE SET value=excluded.value", (instance,))
            self.db.execute("INSERT INTO meta(key,value) VALUES('cursor','0') ON CONFLICT(key) DO UPDATE SET value='0'")

    def close(self):
        self.db.close()
