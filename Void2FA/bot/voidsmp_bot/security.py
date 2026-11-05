"""HMAC wire authentication shared with the Java plugin (protocol V2FA1)."""
from __future__ import annotations
import base64
import hashlib
import hmac
import re
import secrets
import time

SKEW_SECONDS = 30


def decode_secret(value: str) -> bytes:
    value = value.strip()
    if re.fullmatch(r"[0-9a-fA-F]{64}", value):
        raw = bytes.fromhex(value)
    else:
        try:
            raw = base64.b64decode(value, validate=True)
        except (ValueError, TypeError) as exc:
            raise ValueError("Invalid bridge secret encoding") from exc
    if len(raw) != 32:
        raise ValueError("Bridge secret must contain exactly 32 bytes")
    return raw


def signature(secret: bytes, direction: str, path: str, timestamp: int, nonce: str, body: bytes) -> str:
    digest = hashlib.sha256(body).hexdigest()
    canonical = f"V2FA1\n{direction}\n{path}\n{timestamp}\n{nonce}\n{digest}".encode()
    return hmac.new(secret, canonical, hashlib.sha256).hexdigest()


def fresh(timestamp: int, now: int | None = None) -> bool:
    now = int(time.time()) if now is None else now
    return now - SKEW_SECONDS <= timestamp <= now + SKEW_SECONDS


def headers(secret: bytes, path: str, body: bytes) -> dict[str, str]:
    ts, nonce = int(time.time()), secrets.token_urlsafe(32)
    return {
        "Content-Type": "application/json",
        "X-Void2FA-Timestamp": str(ts),
        "X-Void2FA-Nonce": nonce,
        "X-Void2FA-Signature": signature(secret, "request", path, ts, nonce, body),
    }


def verify_response(secret: bytes, path: str, request_nonce: str, response_headers, body: bytes) -> None:
    try:
        ts = int(response_headers["X-Void2FA-Timestamp"])
        nonce = response_headers["X-Void2FA-Nonce"]
        supplied = response_headers["X-Void2FA-Signature"]
    except (KeyError, ValueError) as exc:
        raise ValueError("Unauthenticated bridge response") from exc
    if not fresh(ts) or nonce != request_nonce:
        raise ValueError("Stale or rebound bridge response")
    expected = signature(secret, "response", path, ts, nonce, body)
    if not hmac.compare_digest(expected, supplied):
        raise ValueError("Invalid bridge response signature")
