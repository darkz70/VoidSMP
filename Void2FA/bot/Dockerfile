FROM python:3.12-slim
ENV PYTHONUNBUFFERED=1 PYTHONDONTWRITEBYTECODE=1
WORKDIR /app
COPY requirements.txt pyproject.toml ./
COPY voidsmp_bot ./voidsmp_bot
RUN pip install --no-cache-dir . && useradd --uid 10001 --create-home bot && mkdir -p /state && chown bot:bot /state
USER bot
VOLUME /state
ENV BOT_STATE_PATH=/state/bot.sqlite3 HEALTH_HOST=0.0.0.0 HEALTH_PORT=8089
EXPOSE 8089
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8089/healthz', timeout=3)"
CMD ["python","-m","voidsmp_bot"]
