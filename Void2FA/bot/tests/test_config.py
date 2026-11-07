from voidsmp_bot.config import Config
import pytest


def setup_env(monkeypatch):
    for key in ["BOT_TOKEN", "BOT_TOKEN_FILE", "TELEGRAM_BOT_TOKEN", "API_TOKEN",
                "BRIDGE_SHARED_SECRET", "BRIDGE_SHARED_SECRET_FILE", "DATA_DIR", "PORT", "HOST", "BOT_STATE_PATH"]:
        monkeypatch.delenv(key, raising=False)
    monkeypatch.setenv("BOT_TOKEN", "test-only-not-a-real-token")
    monkeypatch.setenv("BRIDGE_SHARED_SECRET", "11" * 32)


def test_bothost_defaults(monkeypatch, tmp_path):
    setup_env(monkeypatch)
    monkeypatch.setenv("DATA_DIR", str(tmp_path))
    monkeypatch.setenv("PORT", "9000")
    c = Config.from_env()
    assert c.listen_host == "0.0.0.0"
    assert c.listen_port == 9000
    assert c.state_path == tmp_path / "bot.sqlite3"


def test_bothost_token_aliases(monkeypatch):
    setup_env(monkeypatch)
    monkeypatch.delenv("BOT_TOKEN")
    monkeypatch.setenv("API_TOKEN", "alias-token")
    assert Config.from_env().bot_token == "alias-token"
    monkeypatch.delenv("API_TOKEN")
    monkeypatch.setenv("TELEGRAM_BOT_TOKEN", "alias-token-2")
    assert Config.from_env().bot_token == "alias-token-2"


def test_invalid_port(monkeypatch):
    setup_env(monkeypatch)
    monkeypatch.setenv("PORT", "70000")
    with pytest.raises(ValueError):
        Config.from_env()


def test_missing_bridge_secret_has_actionable_error(monkeypatch):
    setup_env(monkeypatch)
    monkeypatch.delenv("BRIDGE_SHARED_SECRET")
    with pytest.raises(ValueError, match="BRIDGE_SHARED_SECRET is required"):
        Config.from_env()


def test_invalid_bridge_secret_does_not_leak_value(monkeypatch):
    setup_env(monkeypatch)
    monkeypatch.setenv("BRIDGE_SHARED_SECRET", "not-a-secret")
    with pytest.raises(ValueError) as exc:
        Config.from_env()
    assert "invalid format/length" in str(exc.value)
    assert "not-a-secret" not in str(exc.value)


def test_secret_files_and_repr(monkeypatch, tmp_path):
    setup_env(monkeypatch)
    monkeypatch.delenv("BOT_TOKEN"); monkeypatch.delenv("BRIDGE_SHARED_SECRET")
    token = tmp_path / "token"; secret = tmp_path / "secret"
    token.write_text("file-test-token\n"); secret.write_text("22" * 32)
    monkeypatch.setenv("BOT_TOKEN_FILE", str(token)); monkeypatch.setenv("BRIDGE_SHARED_SECRET_FILE", str(secret))
    c = Config.from_env()
    assert c.bot_token == "file-test-token"
    assert "file-test-token" not in repr(c) and "22" * 32 not in repr(c)
    monkeypatch.setenv("BOT_TOKEN", "duplicate")
    with pytest.raises(ValueError):
        Config.from_env()


def test_telegram_proxy_is_optional_and_validated(monkeypatch):
    setup_env(monkeypatch)
    monkeypatch.delenv("TELEGRAM_PROXY", raising=False)
    assert Config.from_env().telegram_proxy == ""
    monkeypatch.setenv("TELEGRAM_PROXY", "http://user:pass@127.0.0.1:3128")
    c = Config.from_env()
    assert c.telegram_proxy == "http://user:pass@127.0.0.1:3128"
    # A proxy may carry credentials, so it must never show up in logs of the config.
    assert "pass" not in repr(c)
    monkeypatch.setenv("TELEGRAM_PROXY", "127.0.0.1:3128")
    with pytest.raises(ValueError, match="TELEGRAM_PROXY must be"):
        Config.from_env()


def test_socks_proxy_without_dependency_has_actionable_error(monkeypatch):
    setup_env(monkeypatch)
    monkeypatch.setenv("TELEGRAM_PROXY", "socks5://127.0.0.1:1080")
    try:
        import aiohttp_socks  # noqa: F401
    except ImportError:
        with pytest.raises(ValueError, match="aiohttp-socks is not installed"):
            Config.from_env()
    else:
        assert Config.from_env().telegram_proxy == "socks5://127.0.0.1:1080"
