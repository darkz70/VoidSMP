import asyncio
import json
import time
import aiohttp
from aiohttp import test_utils
import pytest
from aiohttp import web
from voidsmp_bot.config import Config
from voidsmp_bot.service import Service, normalize
from voidsmp_bot.state import State
from voidsmp_bot.security import signature, verify_response
from voidsmp_bot.telegram import TelegramError

NOW = 2000000000


class FakeTelegram:
    def __init__(self):
        self.ready=True; self.bot_id=999; self.username="voidsmp_test_bot"; self.last_poll=time.monotonic()
        self.sent=[]; self.edits=[]; self.answers=[]; self.batches=[]; self.failures=0
    async def connect(self): self.ready=True
    async def updates(self,offset):
        if self.batches: return self.batches.pop(0)
        await asyncio.sleep(3600); return []
    async def send(self,chat,text,keyboard=None):
        if self.failures:
            self.failures-=1; raise TelegramError(0)
        self.sent.append((chat,text,keyboard)); return len(self.sent)
    async def edit(self,chat,mid,text): self.edits.append((chat,mid,text))
    async def answer(self,cid,text): self.answers.append((cid,text))


class FakeBridge:
    def __init__(self): self.connected=True; self.commands=[]; self.failures=0; self.polls=[]
    async def command(self,bot,instance,cursor,event):
        if self.failures:
            self.failures-=1; raise ConnectionError("test")
        self.commands.append(event); return {"version":1,"accepted":True}
    async def poll(self,*args):
        self.polls.append(args)
        return {"version":1,"instance_id":"instance-1","reset":False,"events":[]}


@pytest.fixture
def service(tmp_path):
    s=Service(Config("test-token",bytes(range(32))),None,State(tmp_path/"state.sqlite3"))
    s.telegram=FakeTelegram(); s.bridge=FakeBridge()
    yield s
    s.state.close()


async def test_offset_not_advanced_on_bridge_failure(service):
    u={"update_id":41,"message":{"from":{"id":123,"is_bot":False},"chat":{"id":123,"type":"private"},"text":"/status","date":int(time.time())}}
    service.telegram.batches=[[u]]
    service.bridge.failures=1
    task=asyncio.create_task(service.polling())
    for _ in range(100):
        if service.bridge.polls: break
        await asyncio.sleep(.01)
    service.stop_event.set(); task.cancel()
    try: await task
    except asyncio.CancelledError: pass
    assert service.offset == 0
    assert service.state.get("offset","0") == "0"
    assert service.bridge.commands == []


async def test_command_acceptance_persists_offset_and_retry_is_stable(service):
    date=int(time.time())
    u={"update_id":41,"message":{"from":{"id":123,"is_bot":False},"chat":{"id":123,"type":"private"},"text":"/link ABC234","date":date}}
    service.instance="instance-1"
    event=normalize(u,999,int(time.time()))
    await service.bridge.command(999,"instance-1",0,event)
    service.offset=42; service.state.set("offset","42")
    assert service.state.get("offset") == "42"
    assert service.bridge.commands[0]["event_id"] == "tg:999:41"


async def test_outbox_dedup_edit_and_retry_cursor(service):
    service.instance="instance-1"
    req={"type":"auth_request","event_id":"e1","seq":1,"auth_id":"A"*16,"chat_id":123,"text":"request","approve_label":"OK","deny_label":"NO"}
    edit={"type":"auth_edit","event_id":"e2","seq":2,"auth_id":"A"*16,"chat_id":123,"text":"done"}
    await service.deliver(req); await service.deliver(req); await service.deliver(edit)
    assert len(service.telegram.sent)==1
    assert service.telegram.edits==[(123,1,"done")]
    assert service.state.message("A"*16)==(123,1)
    service.state.set("cursor","2")
    assert service.state.get("cursor")=="2"


async def test_delivery_failure_does_not_mark_processed(service):
    service.telegram.failures=1
    event={"type":"message","event_id":"e1","chat_id":123,"text":"hello"}
    with pytest.raises(TelegramError): await service.deliver(event)
    assert not service.state.processed("e1")
    await service.deliver(event)
    assert service.state.processed("e1")


async def test_instance_reset_invalidates_message_references(service):
    service.state.remember_message("old",123,1)
    service.state.set("cursor","99")
    service.apply_poll({"instance_id":"new","reset":False,"events":[]})
    assert service.instance=="new" and service.cursor==0
    assert service.state.message("old") is None


async def test_group_update_never_reaches_bridge(service):
    u={"update_id":1,"message":{"from":{"id":123,"is_bot":False},"chat":{"id":-999,"type":"group"},"text":"/link ABC234","date":int(time.time())}}
    service.telegram.batches=[[u]]; service.instance="instance-1"
    task=asyncio.create_task(service.polling())
    for _ in range(100):
        if service.offset==2: break
        await asyncio.sleep(.01)
    service.stop_event.set(); await asyncio.wait_for(task,1)
    assert service.bridge.commands==[]


async def test_wire_roundtrip_with_aiohttp(tmp_path):
    secret=bytes(range(32)); received=[]
    async def poll(request):
        body=await request.read(); ts=int(request.headers["X-Void2FA-Timestamp"]); nonce=request.headers["X-Void2FA-Nonce"]
        expected=signature(secret,"request","/v1/poll",ts,nonce,body)
        assert request.headers["X-Void2FA-Signature"]==expected
        received.append(json.loads(body))
        raw=b'{"version":1,"instance_id":"test","reset":false,"events":[]}'
        now=int(time.time())
        return web.Response(body=raw,content_type="application/json",headers={"X-Void2FA-Timestamp":str(now),"X-Void2FA-Nonce":nonce,"X-Void2FA-Signature":signature(secret,"response","/v1/poll",now,nonce,raw)})
    app=web.Application(); app.router.add_post("/v1/poll",poll)
    async with test_utils.TestServer(app) as server:
        origin=str(server.make_url("/")).rstrip("/")
        from voidsmp_bot.bridge import Bridge
        async with aiohttp.ClientSession() as session:
            b=Bridge(session,origin,secret)
            result=await b.poll(999,"test_bot","",0,True)
            assert result["instance_id"]=="test"
            assert received[0]["cursor"]==0
            b.origin += "/missing"
            with pytest.raises(ConnectionError): await b.poll(999,"test_bot","",0,True)  # unsigned 404
