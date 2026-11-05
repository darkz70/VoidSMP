FROM python:3.12-slim
ENV PYTHONUNBUFFERED=1 PYTHONDONTWRITEBYTECODE=1
WORKDIR /app
COPY requirements.txt pyproject.toml ./
COPY voidsmp_bot ./voidsmp_bot
RUN pip install --no-cache-dir . && useradd --uid 10001 --create-home bot && mkdir -p /data && chown bot:bot /data
USER bot
VOLUME /data
ENV DATA_DIR=/data HOST=0.0.0.0 PORT=8080
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8080/health', timeout=3)"
CMD ["python","-m","voidsmp_bot"]
