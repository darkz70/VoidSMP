from __future__ import annotations
import asyncio
import logging
import os
import signal
from pathlib import Path
import aiohttp
from aiohttp import web
from .config import Config
from .service import Service
from .state import State


async def run(config: Config):
    config.state_path.parent.mkdir(parents=True, exist_ok=True)
    # One process per state directory. Telegram 409 is also a fatal conflict guard.
    lock_path = config.state_path.with_suffix(".lock")
    import fcntl
    with open(lock_path, "w") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise RuntimeError("Another bot process owns this state directory") from None
        state = State(config.state_path)
        try:
            async with aiohttp.ClientSession(trust_env=False) as session:
                service = Service(config, session, state)
                loop = asyncio.get_running_loop()
                for sig in (signal.SIGINT, signal.SIGTERM):
                    loop.add_signal_handler(sig, service.stop_event.set)
                async def health(_):
                    return web.json_response({"status": "ready" if service.healthy else "not_ready"},
                                             status=200 if service.healthy else 503)
                app = web.Application()
                app.router.add_get("/healthz", health)
                runner = web.AppRunner(app, access_log=None)
                await runner.setup()
                site = web.TCPSite(runner, config.health_host, config.health_port)
                await site.start()
                logging.getLogger("voidsmp.bot").info("External bot started; health on configured local port")
                try:
                    await service.run()
                finally:
                    await runner.cleanup()
        finally:
            state.close()


def main():
    logging.basicConfig(level=os.environ.get("LOG_LEVEL", "INFO"),
                        format="%(asctime)s %(levelname)s %(name)s %(message)s")
    # aiohttp client exceptions may include token-bearing URLs in other integrations.
    logging.getLogger("aiohttp").setLevel(logging.CRITICAL)
    try:
        asyncio.run(run(Config.from_env()))
    except (ValueError, RuntimeError, OSError) as exc:
        # Config exceptions are written by us and never interpolate secret values.
        logging.getLogger("voidsmp.bot").error("Startup failed: %s", exc)
        raise SystemExit(1) from None


if __name__ == "__main__":
    main()
