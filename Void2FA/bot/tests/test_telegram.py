import time
import aiohttp
from aiohttp import web, test_utils
import pytest
from voidsmp_bot.telegram import Telegram, TelegramError


async def test_telegram_methods_and_no_implicit_webhook_delete(tmp_path):
    calls=[]; offset=10
    async def api(request):
        method=request.match_info["method"]; body=await request.json(); calls.append((method,body))
        if method=="getMe": result={"id":999,"username":"voidsmp_test_bot"}
        elif method=="getWebhookInfo": result={"url":""}
        elif method=="getUpdates": result=[{"update_id":offset}]
        elif method=="sendMessage": result={"message_id":12}
        else: result=True
        return web.json_response({"ok":True,"result":result})
    app=web.Application(); app.router.add_post("/bot{token}/{method}",api)
    async with test_utils.TestServer(app) as server, aiohttp.ClientSession() as session:
        tg=Telegram(session,str(server.make_url("/")).rstrip("/"),"test-token")
        await tg.connect()
        assert tg.bot_id==999 and tg.ready
        assert await tg.updates(10)==[{"update_id":10}]
        assert await tg.send(123,"hello",[[{"text":"OK","callback_data":"v2fa:ok:A"}]])==12
        await tg.edit(123,12,"done"); await tg.answer("cb","ok")
        assert await tg.set_commands() is True
        assert [m for m,_ in calls]==["getMe","getWebhookInfo","getUpdates","sendMessage","editMessageText","answerCallbackQuery","setMyCommands"]
        menu={c["command"] for c in calls[6][1]["commands"]}
        assert {"help","start","kick","revoke","devices","untrust","lock","unlock","link","status","unlink"} <= menu
        assert all(c["description"] and len(c["description"])<=256 for c in calls[6][1]["commands"])
        assert calls[2][1]["allowed_updates"]==["message","callback_query"]
        assert calls[4][1]["reply_markup"]=={"inline_keyboard":[]}


async def test_webhook_conflict_does_not_delete_or_poll():
    calls=[]
    async def api(request):
        method=request.match_info["method"]; calls.append(method)
        result={"id":999,"username":"test_bot"} if method=="getMe" else {"url":"https://example.org/hook"}
        return web.json_response({"ok":True,"result":result})
    app=web.Application(); app.router.add_post("/bot{token}/{method}",api)
    async with test_utils.TestServer(app) as server, aiohttp.ClientSession() as session:
        tg=Telegram(session,str(server.make_url("/")).rstrip("/"),"test-token")
        with pytest.raises(RuntimeError): await tg.connect()
        assert not tg.ready
        assert calls==["getMe","getWebhookInfo"]


async def test_errors_do_not_expose_token_or_description(caplog):
    async def api(request):
        return web.json_response({"ok":False,"description":"secret-code-123456"},status=401)
    app=web.Application(); app.router.add_post("/bot{token}/{method}",api)
    async with test_utils.TestServer(app) as server, aiohttp.ClientSession() as session:
        tg=Telegram(session,str(server.make_url("/")).rstrip("/"),"sensitive-token")
        with pytest.raises(TelegramError) as exc: await tg.call("getMe",{})
        assert "sensitive-token" not in str(exc.value)
        assert "secret-code" not in str(exc.value)


async def test_command_menu_failure_is_not_fatal():
    async def api(request):
        method=request.match_info["method"]
        if method=="setMyCommands":
            return web.json_response({"ok":False,"description":"nope"},status=400)
        return web.json_response({"ok":True,"result":True})
    app=web.Application(); app.router.add_post("/bot{token}/{method}",api)
    async with test_utils.TestServer(app) as server, aiohttp.ClientSession() as session:
        tg=Telegram(session,str(server.make_url("/")).rstrip("/"),"test-token")
        assert await tg.set_commands() is False


async def test_network_failure_reports_cause_not_just_http_zero():
    """HTTP 0 must say what broke: a timeout is a hosting/network problem, not a bad token."""
    import asyncio
    from voidsmp_bot.telegram import network_reason

    async def api(request):
        await asyncio.sleep(5)
        return web.json_response({"ok": True, "result": True})

    app = web.Application(); app.router.add_post("/bot{token}/{method}", api)
    async with test_utils.TestServer(app) as server, aiohttp.ClientSession() as session:
        tg = Telegram(session, str(server.make_url("/")).rstrip("/"), "sensitive-token")
        with pytest.raises(TelegramError) as exc:
            await tg.call("getMe", {}, timeout=0.2)
        assert exc.value.status == 0
        assert exc.value.reason == "timeout"
        assert tg.last_error == "timeout"
        assert "sensitive-token" not in str(exc.value)
    assert network_reason(aiohttp.ClientConnectorDNSError(None, OSError())) == "dns"
    assert network_reason(aiohttp.ClientProxyConnectionError(None, OSError())) == "proxy"
    assert network_reason(aiohttp.ClientConnectorError(None, OSError())) == "unreachable"
    assert network_reason(aiohttp.ServerDisconnectedError()) == "disconnected"


async def test_non_json_answer_is_bad_response():
    async def api(request):
        return web.Response(text="<html>captive portal</html>", content_type="text/html")
    app = web.Application(); app.router.add_post("/bot{token}/{method}", api)
    async with test_utils.TestServer(app) as server, aiohttp.ClientSession() as session:
        tg = Telegram(session, str(server.make_url("/")).rstrip("/"), "test-token")
        with pytest.raises(TelegramError) as exc:
            await tg.call("getMe", {})
        assert exc.value.status == 0 and exc.value.reason == "bad_response"


async def test_successful_call_clears_last_error():
    async def api(request):
        return web.json_response({"ok": True, "result": True})
    app = web.Application(); app.router.add_post("/bot{token}/{method}", api)
    async with test_utils.TestServer(app) as server, aiohttp.ClientSession() as session:
        tg = Telegram(session, str(server.make_url("/")).rstrip("/"), "test-token")
        tg.last_error = "timeout"
        await tg.call("sendMessage", {})
        assert tg.last_error == ""


def test_proxy_credentials_move_to_proxy_auth():
    from voidsmp_bot.telegram import split_proxy

    assert split_proxy("") == ("", "")
    assert split_proxy("http://127.0.0.1:3128") == ("http://127.0.0.1:3128", "")
    url, auth = split_proxy("http://user:p%40ss@127.0.0.1:3128")
    assert url == "http://127.0.0.1:3128"
    assert auth == aiohttp.encode_basic_auth("user", "p@ss")
    # SOCKS is handled by the session connector, never as a per-request proxy.
    assert split_proxy("socks5://user:pass@127.0.0.1:1080") == ("", "")


async def test_http_proxy_receives_the_telegram_request():
    """The proxy must see the absolute Telegram URL, and credentials must not stay in it."""
    seen = []

    async def proxy_handler(request):
        seen.append((str(request.url), request.headers.get("Proxy-Authorization", "")))
        return web.json_response({"ok": True, "result": {"id": 999, "username": "test_bot"}})

    app = web.Application(); app.router.add_post("/{tail:.*}", proxy_handler)
    async with test_utils.TestServer(app) as server, aiohttp.ClientSession() as session:
        proxy = str(server.make_url("/")).rstrip("/").replace("http://", "http://user:pass@")
        tg = Telegram(session, "http://api.telegram.org", "test-token", proxy)
        assert await tg.call("getMe", {}) == {"id": 999, "username": "test_bot"}
    assert len(seen) == 1
    url, auth = seen[0]
    assert url == "http://api.telegram.org/bottest-token/getMe"
    assert auth == aiohttp.encode_basic_auth("user", "pass")
