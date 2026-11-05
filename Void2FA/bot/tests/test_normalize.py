import pytest
from voidsmp_bot.service import normalize

NOW = 2000000000

def message(text="/status", chat="private", uid=123, cid=123, date=NOW):
    return {"update_id": 7, "message": {"from": {"id": uid, "is_bot": False},
            "chat": {"id": cid, "type": chat}, "text": text, "date": date}}


def callback(uid=123, cid=123, chat="private", author=999, data="v2fa:ok:ABCDEFGHJKLMNPQR"):
    return {"update_id": 8, "callback_query": {"id": "cb", "from": {"id": uid, "is_bot": False},
            "data": data, "message": {"from": {"id": author}, "chat": {"id": cid, "type": chat}, "date": NOW}}}


def test_private_message_identity():
    event = normalize(message(), 999, NOW)
    assert event["event_id"] == "tg:999:7"
    assert event["user_id"] == event["chat_id"] == 123
    assert event["type"] == "message"
    assert "uuid" not in event


@pytest.mark.parametrize("change", [{"chat":"group"}, {"chat":"supergroup"}, {"cid":456}, {"uid":0}, {"date":NOW-121}, {"date":NOW+31}])
def test_rejects_groups_spoofed_chat_and_stale_updates(change):
    assert normalize(message(**change), 999, NOW) is None


def test_callback_requires_private_owner_and_our_message():
    assert normalize(callback(), 999, NOW)["type"] == "callback"
    assert normalize(callback(cid=456), 999, NOW) is None
    assert normalize(callback(chat="group"), 999, NOW) is None
    assert normalize(callback(author=123), 999, NOW) is None


def test_non_text_edits_channels_and_bots_ignored():
    assert normalize({"update_id":1,"edited_message":message()["message"]}, 999, NOW) is None
    assert normalize({"update_id":1,"channel_post":message()["message"]}, 999, NOW) is None
    u=message(); u["message"]["from"]["is_bot"]=True
    assert normalize(u,999,NOW) is None
    u=message(); del u["message"]["text"]
    assert normalize(u,999,NOW) is None


@pytest.mark.parametrize("text", ["/kick", "/revoke", "/untrust all"])
def test_owner_security_commands_are_transported_unchanged(text):
    event = normalize(message(text=text), 999, NOW)
    assert event is not None
    assert event["type"] == "message"
    assert event["text"] == text
    assert event["user_id"] == event["chat_id"] == 123
