import json
import secrets
import time
import aiohttp
from aiohttp import test_utils
import pytest
from voidsmp_bot.config import Config
from voidsmp_bot.security import signature, verify_response
from voidsmp_bot.server import SyncServer
from voidsmp_bot.service import Service
from voidsmp_bot.state import State


class FakeTelegram:
    ready = True
    bot_id = 999
    username = "voidsmp_test_bot"


@pytest.fixture
def state(tmp_path):
    s = State(tmp_path / "state.sqlite3")
    yield s
    s.close()


@pytest.fixture
def server(state):
    config = Config("test-token", bytes(range(32)))
    service = Service(config, None, state)
    service.telegram = FakeTelegram()
    return SyncServer(config, service, state)


async def call(server, payload, secret=bytes(range(32)), nonce=None, tamper=False):
    body = json.dumps(payload).encode()
    ts = int(time.time())
    nonce = nonce or secrets.token_urlsafe(24)
    sig = signature(secret, "request", "/v1/sync", ts, nonce, body)
    headers = {"X-Void2FA-Timestamp": str(ts), "X-Void2FA-Nonce": nonce, "X-Void2FA-Signature": sig}
    async with test_utils.TestServer(server.app()) as tsrv:
        async with aiohttp.ClientSession() as session:
            async with session.post(str(tsrv.make_url("/v1/sync")), data=body, headers=headers) as r:
                raw = await r.read()
                if r.status == 200 and not tamper:
                    verify_response(secret, "/v1/sync", nonce, r.headers, raw)
                return r.status, json.loads(raw) if raw else {}


async def test_sync_roundtrip_commands_events_and_ack(server, state):
    state.enqueue_command({"event_id": "tg:999:1", "type": "message", "user_id": 123, "chat_id": 123,
                           "chat_type": "private", "issued_at": int(time.time()), "text": "/status"})
    event = {"type": "message", "event_id": "event-01", "chat_id": 123, "text": "hello"}
    status, value = await call(server, {"version": 1, "instance_id": "instance-1", "cursor": 0, "events": [event]})
    assert status == 200
    assert value["bot_username"] == "voidsmp_test_bot"
    assert value["reset"] is False
    assert value["commands"][0]["seq"] == 1
    assert value["accepted"] == ["event-01"]
    assert state.pending_outbox() == [event]
    seq = value["commands"][0]["seq"]
    status, value = await call(server, {"version": 1, "instance_id": "instance-1", "cursor": seq, "events": []})
    assert status == 200
    assert value["reset"] is False
    assert value["commands"] == []
    assert state.commands_after(0) == []


async def test_unsigned_tampered_and_replayed_requests_rejected(server):
    payload = {"version": 1, "instance_id": "instance-1", "cursor": 0, "events": []}
    status, _ = await call(server, payload, secret=bytes(32))
    assert status == 401
    status, _ = await call(server, payload, nonce="a" * 32)
    assert status == 200
    status, _ = await call(server, payload, nonce="a" * 32)
    assert status == 401


async def test_instance_change_resets_queues(server, state):
    state.enqueue_command({"event_id": "tg:999:1", "type": "message", "user_id": 123, "chat_id": 123,
                           "chat_type": "private", "issued_at": int(time.time()), "text": "/status"})
    state.enqueue_events([{"type": "message", "event_id": "event-01", "chat_id": 123, "text": "x"}])
    status, value = await call(server, {"version": 1, "instance_id": "instance-1", "cursor": 0, "events": []})
    assert status == 200 and value["commands"]
    status, value = await call(server, {"version": 1, "instance_id": "instance-2", "cursor": 99, "events": []})
    assert status == 200
    assert value["reset"] is True
    assert value["commands"] == []
    assert state.pending_outbox() == []


async def test_invalid_event_rejected(server):
    bad = {"type": "message", "event_id": "event-01", "chat_id": -1, "text": "hello"}
    status, _ = await call(server, {"version": 1, "instance_id": "instance-1", "cursor": 0, "events": [bad]})
    assert status == 400


async def test_health_exposes_cause_without_secrets(server):
    server.service.telegram.last_error = "timeout"
    async with test_utils.TestServer(server.app()) as tsrv:
        async with aiohttp.ClientSession() as session:
            async with session.get(str(tsrv.make_url("/health"))) as r:
                assert r.status == 200
                value = await r.json()
    assert value["status"] == "ok"
    assert value["telegram_ready"] is True
    assert value["telegram_error"] == "timeout"
    assert value["telegram_api_host"] == "api.telegram.org"
    assert value["proxy_configured"] is False
    assert value["plugin_synced"] is False
    assert "test-token" not in json.dumps(value)


async def test_health_identifies_the_answering_container(server, monkeypatch):
    """After moving to a foreign-IP node the operator must be able to confirm which bot answered."""
    from voidsmp_bot import __version__

    monkeypatch.setenv("BOT_ID", "bot_1793902194_2443_verte82")
    monkeypatch.setenv("DOMAIN", "bot-1793902194-2443-verte82.bothost.tech")
    async with test_utils.TestServer(server.app()) as tsrv:
        async with aiohttp.ClientSession() as session:
            async with session.get(str(tsrv.make_url("/health"))) as r:
                value = await r.json()
    assert value["version"] == __version__
    assert value["hosting_bot_id"] == "bot_1793902194_2443_verte82"
    assert value["hosting_domain"] == "bot-1793902194-2443-verte82.bothost.tech"
