from voidsmp_bot.config import Config
import pytest


def setup_env(monkeypatch):
    for key in ["BOT_TOKEN","BOT_TOKEN_FILE","BRIDGE_SHARED_SECRET","BRIDGE_SHARED_SECRET_FILE","PLUGIN_BRIDGE_URL"]:
        monkeypatch.delenv(key,raising=False)
    monkeypatch.setenv("BOT_TOKEN","test-only-not-a-real-token")
    monkeypatch.setenv("BRIDGE_SHARED_SECRET","11"*32)


def test_remote_requires_tls(monkeypatch):
    setup_env(monkeypatch)
    monkeypatch.setenv("PLUGIN_BRIDGE_URL","http://mc.example:8765")
    with pytest.raises(ValueError): Config.from_env()
    monkeypatch.setenv("PLUGIN_BRIDGE_URL","https://mc.example")
    assert Config.from_env().plugin_url == "https://mc.example"


def test_secret_files_and_repr(monkeypatch,tmp_path):
    setup_env(monkeypatch)
    monkeypatch.delenv("BOT_TOKEN"); monkeypatch.delenv("BRIDGE_SHARED_SECRET")
    token=tmp_path/"token"; secret=tmp_path/"secret"
    token.write_text("file-test-token\n"); secret.write_text("22"*32)
    monkeypatch.setenv("BOT_TOKEN_FILE",str(token)); monkeypatch.setenv("BRIDGE_SHARED_SECRET_FILE",str(secret))
    c=Config.from_env()
    assert c.bot_token == "file-test-token"
    assert "file-test-token" not in repr(c) and "22"*32 not in repr(c)
    monkeypatch.setenv("BOT_TOKEN","duplicate")
    with pytest.raises(ValueError): Config.from_env()
