# Отдельный Telegram-бот VoidSMP 1.1.0

**Статус:** исходники и локальные тесты подготовлены. Telegram-аккаунт не создан,
токен не запрашивался, к рабочему Minecraft-серверу никто не подключался.
Старый `release/Void2FA-1.0.0.jar` содержит только встроенный транспорт и **не умеет external**.

## Архитектура

```text
Telegram Bot API <--единственный getUpdates--> Python bot service
                                               |
                              POST /v1/poll, /v1/command
                              HMAC-SHA256, обе стороны подписаны
                                               |
                         Void2FA 1.1.0 bridge (127.0.0.1:8765)
                                               |
                         AuthManager на main thread Paper
                         коды, UUID, привязки, срок, одноразовость
```

Бот не принимает Minecraft UUID от пользователя и не объявляет вход разрешённым.
Он передаёт Telegram user/chat ID, текст личного сообщения или callback текущего сообщения.
Плагин проверяет владельца, срок и состояние попытки. `/login` проверяется только плагином.
Тексты остаются в `config.yml` плагина: бот получает уже подготовленный текст.

В режиме `external` плагин не вызывает Telegram API, не удаляет webhook и не запускает
`getUpdates`. Если в external оставить непустой `telegram.bot-token`, транспорт не запустится.
В режиме `embedded` сохраняется старый вариант, но его нельзя включать одновременно с сервисом.

## 1. Сборка и тесты без Telegram

Python 3.11+:

```bash
cd Void2FA/bot
python3 -m venv .venv
.venv/bin/pip install -r requirements-dev.txt
.venv/bin/python -m pytest -q
```

Java 25 и Gradle с поддержкой Java 25 (в CI используется 9.8.2):

```bash
cd Void2FA
gradle build --no-daemon
# build/libs/Void2FA-1.1.0.jar
```

Для Paper API нужен доступ к `repo.papermc.io`. GitHub Actions `.github/workflows/void2fa.yml`
запускает оба набора проверок и сохраняет JAR как артефакт. Не устанавливай старый 1.0.0 для external.

## 2. Создание аккаунта Telegram

Только владелец Telegram создаёт аккаунт: [@BotFather](https://t.me/BotFather) → `/newbot`.
Имя: `VoidSMP | 2FA`; username — свободный, заканчивается на `bot`.
Тексты `/setdescription`, `/setshortdescription`, `/setcommands` сохранены в [BOT_SETUP.md](BOT_SETUP.md).
Не отправляй токен в чат, issue, Git или логи. Webhook не настраивай: сервис использует long polling.

## 3. Общий секрет моста

Секрет моста — **не** токен BotFather. Сгенерируй 32 случайных байта локально:

```bash
python3 -c 'import secrets; print(secrets.token_hex(32))'
```

Сохрани результат в защищённом локальном секрет-хранилище. Один и тот же секрет нужен
плагину и боту. Не используй пример из тестов. Файлы секретов: права `0600`, владелец сервисного пользователя.

## 4. Установка на тестовую копию Paper

Сначала останови тестовый сервер. Замени старый JAR на собранный `Void2FA-1.1.0.jar`.
Сохрани резервную копию `plugins/Void2FA/config.yml` и `data.yml`.
Новые поля не добавляются автоматически в уже существующий YAML: добавь их вручную.

```yaml
telegram:
  transport: external
  bot-token: ""
  bot-username: ""  # необязательно; после подключения бот сообщит username
  bridge:
    host: "127.0.0.1"
    port: 8765
    secret-file: "bridge-secret.txt"
```

Запиши общий секрет в `plugins/Void2FA/bridge-secret.txt`, права `0600`.
Альтернатива для плагина — `VOID2FA_BRIDGE_SECRET` в окружении процесса Paper; она имеет приоритет.
Не публикуй локальный конфиг и не добавляй секрет в Git.

Запусти тестовый Paper. В логе должно быть сообщение об ожидании bridge, но ещё не о готовности 2FA.
Порт 8765 не открывай в интернет. При отдельной машине используй HTTPS reverse proxy с проверкой
сертификата или SSH-туннель; HMAC не заменяет TLS. Прокси должен сохранять путь `/v1/...` и заголовки
`X-Void2FA-*`. Plain HTTP в боте разрешён только для loopback.

## 5A. systemd на той же машине

Скопируй исходники бота в `/opt/voidsmp-bot`, создай отдельного пользователя и каталог состояния:

```bash
sudo useradd --system --home-dir /opt/voidsmp-bot --shell /usr/sbin/nologin voidsmp-bot
sudo install -d -m 0750 -o voidsmp-bot -g voidsmp-bot /opt/voidsmp-bot /var/lib/voidsmp-bot
sudo install -d -m 0750 /etc/voidsmp-bot
# Скопируй pyproject.toml, requirements.txt и voidsmp_bot/ в /opt/voidsmp-bot
sudo python3 -m venv /opt/voidsmp-bot/.venv
sudo /opt/voidsmp-bot/.venv/bin/pip install -r /opt/voidsmp-bot/requirements.txt
sudo cp -a voidsmp_bot /opt/voidsmp-bot/
sudo cp deploy/voidsmp-bot.service /etc/systemd/system/
sudo cp deploy/env.example /etc/voidsmp-bot/env
sudo chmod 0600 /etc/voidsmp-bot/env
```

Локально отредактируй `/etc/voidsmp-bot/env`: `BOT_TOKEN`, `BRIDGE_SHARED_SECRET`.
Для секрет-файлов вместо значений используй `BOT_TOKEN_FILE` и `BRIDGE_SHARED_SECRET_FILE`;
в этом случае добавь их каталог в `ReadOnlyPaths=` юнита. Не задавай одновременно значение и `_FILE`.
Если Paper работает под другим пользователем, секрет моста должен быть доступен обоим через
общую группу с минимальными правами; не делай файл world-readable.

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now voidsmp-bot
sudo systemctl status voidsmp-bot
curl -fsS http://127.0.0.1:8089/healthz
```

`/healthz` возвращает 200 только когда готовы Telegram и bridge. Логи не должны содержать токен,
секрет, текст команд или коды. Обычные логи содержат только тип/HTTP-код ошибки.

## 5B. Docker Compose на Linux

В `Void2FA/bot` создай ignored-каталог `secrets/`, файлы `bot_token` и `bridge_secret` с правами `0600`.
Compose использует host networking, чтобы достучаться до `127.0.0.1:8765` Paper.
Health публикуется только на `127.0.0.1:8089`; bridge наружу не публикуется.

```bash
docker compose up -d --build
docker compose logs --tail=50 bot
```

На Docker Desktop/не-Linux host networking может не давать доступ к loopback хоста:
используй systemd-вариант или явно настроенный HTTPS/SSH-туннель. Не меняй bridge на публичный `0.0.0.0` без TLS.

## 6. Проверка сценариев

1. В игре `/2fa link`; отправь боту `/link КОД` или открой ссылку `/start LINK_КОД`.
2. `/status` показывает привязку; повторная привязка тем же аккаунтом не создаёт вторую запись.
3. Повторный вход: получен код и кнопки. Верный `/login КОД` подтверждает, неверный — нет.
4. «Отклонить» завершает попытку; старая кнопка не подтверждает следующий вход.
5. Выход игрока и таймаут отменяют попытку. `/2fa resend` выдаёт новый auth ID; старая кнопка не работает.
6. `/unlink` работает только вне незавершённого входа. Команды в группе игнорируются.
7. Останови бота: новые входы ведут себя согласно `lockdown-if-unavailable`.
   **Текущее значение false означает fail-open для новых входов, включая привязанных игроков.**
   После тестов выбери осознанную политику; для строгой защиты установи true и настрой отдельный
   аварийный доступ администратора. Уже ожидающие попытки не разрешаются автоматически.
8. Перезапусти бота: SQLite сохраняет Telegram offset/cursor. Перезапусти Paper: меняется instance ID,
   старые callback/link/auth ID больше не действуют.

Административное восстановление: `/2faadmin reset НИК` с `void2fa.admin`.
Не выдавай `void2fa.bypass` обычным игрокам: оно полностью пропускает 2FA.

## Протокол и ограничения

Подробности: [BRIDGE_PROTOCOL.md](BRIDGE_PROTOCOL.md). Доставка команд —at-least-once через durable
Telegram offset и idempotency key плагина; доставка сообщений — at-least-once через SQLite event IDs.
Между успешной отправкой в Telegram и записью SQLite возможен повтор сообщения при аварийном рестарте.
Это не повторная авторизация: решение и одноразовость остаются в плагине. SQLite хранит offset,
cursor, event IDs и ссылки на сообщения, но не код входа и не токен. Telegram сам хранит отправленный код в чате.

Сервис рассчитан на **один экземпляр на токен и один Minecraft-сервер на секрет/bridge**.
Файл-замок защищает один state-каталог; Telegram 409 останавливает конкурирующий процесс.
Не запускай embedded и external с одним токеном. Смена бота требует отдельного чистого state-каталога.
