from __future__ import annotations
import json
import sqlite3
import time
from pathlib import Path


class State:
    """SQLite on Bothost DATA_DIR. Stores queues/offsets/message refs, never tokens or login codes."""

    def __init__(self, path: Path):
        path.parent.mkdir(parents=True, exist_ok=True)
        self.db = sqlite3.connect(path, timeout=30)
        self.db.execute("PRAGMA journal_mode=WAL")
        self.db.execute("PRAGMA synchronous=FULL")
        self.db.executescript("""
            CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY, value TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS messages(auth_id TEXT PRIMARY KEY, chat_id INTEGER NOT NULL, message_id INTEGER NOT NULL);
            CREATE TABLE IF NOT EXISTS commands(
                seq INTEGER PRIMARY KEY AUTOINCREMENT,
                event_id TEXT NOT NULL UNIQUE,
                payload TEXT NOT NULL,
                created_at INTEGER NOT NULL
            );
            CREATE TABLE IF NOT EXISTS outbox(
                event_id TEXT PRIMARY KEY,
                payload TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                delivered INTEGER NOT NULL DEFAULT 0
            );
            CREATE TABLE IF NOT EXISTS nonces(nonce TEXT PRIMARY KEY, expires_at INTEGER NOT NULL);
        """)
        self.db.commit()

    def get(self, key: str, default: str = "") -> str:
        row = self.db.execute("SELECT value FROM meta WHERE key=?", (key,)).fetchone()
        return row[0] if row else default

    def set(self, key: str, value: str) -> None:
        with self.db:
            self.db.execute(
                "INSERT INTO meta(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value",
                (key, str(value)),
            )

    # Telegram -> plugin commands
    def enqueue_command(self, event: dict, now: int | None = None) -> bool:
        now = int(time.time()) if now is None else now
        with self.db:
            cur = self.db.execute(
                "INSERT OR IGNORE INTO commands(event_id,payload,created_at) VALUES(?,?,?)",
                (event["event_id"], json.dumps(event, ensure_ascii=False, separators=(",", ":")), now),
            )
            self.db.execute("DELETE FROM commands WHERE created_at < ?", (now - 600,))
            # Bound disk use on small/free hosting plans. Oldest undelivered commands expire first.
            self.db.execute(
                "DELETE FROM commands WHERE seq IN (SELECT seq FROM commands ORDER BY seq DESC LIMIT -1 OFFSET 5000)"
            )
            return cur.rowcount == 1

    def commands_after(self, cursor: int, limit: int = 100) -> list[dict]:
        rows = self.db.execute(
            "SELECT seq,payload FROM commands WHERE seq > ? ORDER BY seq LIMIT ?", (cursor, limit)
        ).fetchall()
        result = []
        for seq, payload in rows:
            item = json.loads(payload)
            item["seq"] = seq
            result.append(item)
        return result

    def command_sequence(self) -> int:
        row = self.db.execute("SELECT seq FROM sqlite_sequence WHERE name='commands'").fetchone()
        if row:
            return int(row[0])
        row = self.db.execute("SELECT COALESCE(MAX(seq),0) FROM commands").fetchone()
        return int(row[0])

    def first_command_seq(self) -> int | None:
        row = self.db.execute("SELECT MIN(seq) FROM commands").fetchone()
        return int(row[0]) if row and row[0] is not None else None

    def ack_commands(self, cursor: int) -> None:
        with self.db:
            self.db.execute("DELETE FROM commands WHERE seq <= ?", (cursor,))

    def reset_plugin(self, instance: str) -> None:
        with self.db:
            self.db.execute("DELETE FROM commands")
            self.db.execute("DELETE FROM outbox")
            self.db.execute("DELETE FROM messages")
            self.db.execute("INSERT INTO meta(key,value) VALUES('plugin_instance',?) ON CONFLICT(key) DO UPDATE SET value=excluded.value", (instance,))

    # Plugin -> Telegram outbox
    def enqueue_events(self, events: list[dict], now: int | None = None) -> list[str]:
        now = int(time.time()) if now is None else now
        accepted: list[str] = []
        with self.db:
            for event in events:
                event_id = event["event_id"]
                self.db.execute(
                    "INSERT OR IGNORE INTO outbox(event_id,payload,created_at,delivered) VALUES(?,?,?,0)",
                    (event_id, json.dumps(event, ensure_ascii=False, separators=(",", ":")), now),
                )
                accepted.append(event_id)
            self.db.execute("DELETE FROM outbox WHERE created_at < ? AND delivered=1", (now - 86400,))
            self.db.execute("DELETE FROM outbox WHERE created_at < ?", (now - 600,))
        return accepted

    def pending_outbox(self, limit: int = 20) -> list[dict]:
        rows = self.db.execute(
            "SELECT payload FROM outbox WHERE delivered=0 ORDER BY rowid LIMIT ?", (limit,)
        ).fetchall()
        return [json.loads(row[0]) for row in rows]

    def mark_outbox_delivered(self, event_id: str) -> None:
        with self.db:
            self.db.execute("UPDATE outbox SET delivered=1 WHERE event_id=?", (event_id,))

    def outbox_delivered(self, event_id: str) -> bool:
        row = self.db.execute("SELECT delivered FROM outbox WHERE event_id=?", (event_id,)).fetchone()
        return bool(row and row[0])

    # Telegram message references for auth prompts
    def message(self, auth_id: str):
        return self.db.execute("SELECT chat_id,message_id FROM messages WHERE auth_id=?", (auth_id,)).fetchone()

    def remember_message(self, auth_id: str, chat_id: int, message_id: int) -> None:
        with self.db:
            self.db.execute("INSERT OR REPLACE INTO messages VALUES(?,?,?)", (auth_id, chat_id, message_id))

    # HMAC replay protection, persistent across container restarts
    def accept_nonce(self, nonce: str, now: int, ttl: int = 61) -> bool:
        with self.db:
            self.db.execute("DELETE FROM nonces WHERE expires_at <= ?", (now,))
            count = self.db.execute("SELECT COUNT(*) FROM nonces").fetchone()[0]
            if count >= 20000:
                return False
            cur = self.db.execute("INSERT OR IGNORE INTO nonces(nonce,expires_at) VALUES(?,?)", (nonce, now + ttl))
            return cur.rowcount == 1

    def close(self) -> None:
        self.db.close()
