import asyncio
import time
import pytest
from voidsmp_bot.config import Config
from voidsmp_bot.service import Service, normalize
from voidsmp_bot.state import State
from voidsmp_bot.telegram import TelegramError


class FakeTelegram:
    def __init__(self):
        self.ready = True; self.bot_id = 999; self.username = "voidsmp_test_bot"; self.last_poll = time.monotonic()
        self.sent = []; self.edits = []; self.answers = []; self.batches = []; self.failures = 0
        self.menu_calls = 0; self.menu_ok = True
    async def connect(self): self.ready = True
    async def set_commands(self):
        self.menu_calls += 1
        return self.menu_ok
    async def updates(self, offset):
        if self.batches: return self.batches.pop(0)
        await asyncio.sleep(3600); return []
    async def send(self, chat, text, keyboard=None):
        if self.failures:
            self.failures -= 1; raise TelegramError(0)
        self.sent.append((chat, text, keyboard)); return len(self.sent)
    async def edit(self, chat, mid, text): self.edits.append((chat, mid, text))
    async def answer(self, cid, text): self.answers.append((cid, text))


@pytest.fixture
def service(tmp_path):
    s = Service(Config("test-token", bytes(range(32))), None, State(tmp_path / "state.sqlite3"))
    s.telegram = FakeTelegram()
    yield s
    s.state.close()


async def test_private_command_is_queued_and_offset_persisted(service):
    u = {"update_id": 41, "message": {"from": {"id": 123, "is_bot": False},
                                     "chat": {"id": 123, "type": "private"}, "text": "/status", "date": int(time.time())}}
    service.telegram.batches = [[u]]
    task = asyncio.create_task(service.polling())
    for _ in range(100):
        if service.offset == 42: break
        await asyncio.sleep(.01)
    service.stop_event.set(); task.cancel()
    try: await task
    except asyncio.CancelledError: pass
    assert service.state.get("offset") == "42"
    commands = service.state.commands_after(0)
    assert commands[0]["event_id"] == "tg:999:41"
    assert commands[0]["text"] == "/status"


async def test_group_update_never_queued(service):
    u = {"update_id": 1, "message": {"from": {"id": 123, "is_bot": False},
                                     "chat": {"id": -999, "type": "group"}, "text": "/link ABC234", "date": int(time.time())}}
    service.telegram.batches = [[u]]
    task = asyncio.create_task(service.polling())
    for _ in range(100):
        if service.offset == 2: break
        await asyncio.sleep(.01)
    service.stop_event.set(); await asyncio.wait_for(task, 1)
    assert service.state.commands_after(0) == []


async def test_outbox_dedup_edit_and_delivery_mark(service):
    req = {"type": "auth_request", "event_id": "e1", "auth_id": "A" * 16, "chat_id": 123,
           "text": "request", "approve_label": "OK", "deny_label": "NO"}
    edit = {"type": "auth_edit", "event_id": "e2", "auth_id": "A" * 16, "chat_id": 123, "text": "done"}
    service.state.enqueue_events([req, edit])
    await service.deliver(req); service.state.mark_outbox_delivered("e1")
    await service.deliver(req)
    await service.deliver(edit); service.state.mark_outbox_delivered("e2")
    assert len(service.telegram.sent) == 1
    assert service.telegram.edits == [(123, 1, "done")]
    assert service.state.message("A" * 16) == (123, 1)
    assert service.state.pending_outbox() == []


async def test_delivery_failure_keeps_outbox_pending(service):
    service.telegram.failures = 1
    event = {"type": "message", "event_id": "e1", "chat_id": 123, "text": "hello"}
    service.state.enqueue_events([event])
    with pytest.raises(TelegramError):
        await service.deliver(event)
    assert service.state.pending_outbox() == [event]
    await service.deliver(event)
    service.state.mark_outbox_delivered("e1")
    assert service.state.pending_outbox() == []


async def test_instance_reset_invalidates_message_references(service):
    service.state.remember_message("old", 123, 1)
    service.state.enqueue_events([{"type": "message", "event_id": "e1", "chat_id": 123, "text": "x"}])
    service.state.reset_plugin("new")
    assert service.state.get("plugin_instance") == "new"
    assert service.state.message("old") is None
    assert service.state.pending_outbox() == []


async def test_identify_publishes_command_menu_once(service):
    service.telegram.ready = False
    await service.identify()
    await service.identify()
    assert service.telegram.menu_calls == 1


async def test_menu_failure_does_not_break_identify(service):
    service.telegram.ready = False
    service.telegram.menu_ok = False
    await service.identify()
    assert service.telegram.ready
    assert service.state.get("bot_id") == "999"


def test_repeated_network_failure_is_throttled_and_explains_the_cause(service, caplog):
    """A dead network must not print two warnings every few seconds for hours."""
    import logging
    from voidsmp_bot.service import LOG_THROTTLE_SECONDS

    caplog.set_level(logging.INFO, logger="voidsmp.bot")
    error = TelegramError(0, reason="timeout")
    for _ in range(50):
        service.warn_transport(error, "Telegram unavailable")
    lines = [r.getMessage() for r in caplog.records]
    assert len([m for m in lines if "Telegram unavailable" in m]) == 1
    assert any("timeout" in m and "api.telegram.org" in m for m in lines)
    assert any("Зарубежный IP" in m or "TELEGRAM_PROXY" in m for m in lines)

    # Once the throttle window passes, one summary line reports the suppressed count.
    service._warned_at -= LOG_THROTTLE_SECONDS + 1
    caplog.clear()
    service.warn_transport(error, "Telegram unavailable")
    assert any("подавлено" in r.getMessage() for r in caplog.records)


def test_recovery_is_logged_once(service, caplog):
    import logging

    caplog.set_level(logging.INFO, logger="voidsmp.bot")
    service.warn_transport(TelegramError(0, reason="dns"), "Telegram unavailable")
    caplog.clear()
    service.note_recovered()
    service.note_recovered()
    assert len([r for r in caplog.records if "снова доступен" in r.getMessage()]) == 1


def test_bad_token_says_to_reissue_it(service, caplog):
    import logging

    caplog.set_level(logging.INFO, logger="voidsmp.bot")
    service.warn_transport(TelegramError(401), "Telegram unavailable")
    messages = [r.getMessage() for r in caplog.records]
    assert any("401" in m for m in messages)
    assert any("BotFather" in m for m in messages)
