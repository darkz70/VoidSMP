# Void2FA external bridge, protocol V2FA1 / version 1

Это документация реализованного моста в исходниках 1.3.1, не описание старого JAR 1.0.0.
Транспорт: HTTPS, JSON, POST. Бот — сервер (`0.0.0.0:$PORT` на Bothost), плагин Paper — клиент.
Плагин инициирует единственный запрос `POST /v1/sync`; входящий порт Minecraft-сервера не нужен.
Один секрет — один сервер и один бот.

## Аутентификация

Общий секрет: ровно 32 байта, закодированные 64 hex-символами или strict base64.
Заголовки запроса:

- `X-Void2FA-Timestamp`: Unix seconds; допуск ±30 секунд;
- `X-Void2FA-Nonce`: 16–64 символа `[A-Za-z0-9_-]`, случайный на каждый запрос;
- `X-Void2FA-Signature`: lowercase hex HMAC-SHA256;
- `Content-Type: application/json`.

Подписываемые байты (UTF-8, `\n` — один LF):

```text
V2FA1
request
/v1/sync
1700000000
NONCE
lowercase-hex(SHA256(raw HTTP body))
```

Для ответа второе поле — `response`, timestamp — время ответа, nonce — nonce запроса.
Ответ также содержит `Cache-Control: no-store`. Плагин проверяет подпись, свежесть и совпадение nonce
до разбора JSON. Бот проверяет подпись запроса и принимает nonce один раз в окне 61 секунда;
nonce хранятся в SQLite, поэтому рестарт бота не открывает окно повтора.
Повтор подписанного запроса отклоняется. После рестарта Paper меняется `instance_id`,
поэтому старые очереди команд/сообщений сбрасываются. Межмашинный трафик обязательно шифруется TLS.

Тело запроса ограничено 262144 байтами, batch — 100 событиями. Ошибки 4xx/5xx не подписаны и не содержат данных аккаунта;
клиент принимает только подписанный HTTP 200. Никакой endpoint не принимает Minecraft UUID или команду «авторизовать UUID».

## POST /v1/sync

Запрос плагина примерно раз в секунду:

```json
{"version":1,"instance_id":"UUID","cursor":12,"events":[]}
```

- `instance_id` — случайный ID процесса плагина;
- `cursor` — последний `seq` команды Telegram, обработанной на main thread Paper;
- `events` — outbox плагина: сообщения/кнопки/редактирования, которые бот должен отправить в Telegram.

Ответ бота:

```json
{"version":1,"bot_id":123456,"bot_username":"example_bot","telegram_ready":true,"reset":false,"cursor":12,"sequence":14,"commands":[],"accepted":[]}
```

- `commands` — личные сообщения и callback-нажатия из Telegram с полем `seq`;
- `accepted` — `event_id` событий, durably принятых в SQLite outbox бота;
- `reset=true` означает новый instance плагина или потерянный курсор: бот очистил старые очереди,
  вернул `cursor=0` и ожидает подтверждения заново. Старые callback/link/auth ID после этого не действуют.

Плагин удаляет из своего outbox только `accepted` события. Он продвигает локальный cursor только после
обработки команды на main thread; следующий sync подтверждает её удалением из очереди бота.
Команды старше 10 минут и очередь длиннее 5000 удаляются; это не разрешает вход, попытка остаётся
неподтверждённой до таймаута. Плагин хранит receipts 10 минут (до 20000), поэтому повтор не выполняет
link/unlink второй раз. Готовность плагина истекает через 15 секунд без успешного sync.

Типы событий в `events`:

- `message`: `event_id`, `chat_id`, `text` → `sendMessage`;
- `auth_request`: additionally `auth_id`, `approve_label`, `deny_label` → `sendMessage` с двумя inline buttons;
- `auth_edit`: `auth_id`, `chat_id`, `text` → `editMessageText` и удаление клавиатуры;
- `callback_answer`: `callback_id`, `text` → `answerCallbackQuery`.

Callback data создаётся плагином: `v2fa:ok:AUTH_ID` / `v2fa:no:AUTH_ID`.
AUTH_ID — случайный 16-символьный идентификатор конкретной попытки/высылки, не UUID игрока.
Бот хранит соответствие auth ID → Telegram message ID в SQLite. Все тексты рендерит плагин из `messages`.

Команды в `commands`:

```json
{"seq":13,"event_id":"tg:123456:789","type":"message","user_id":42,"chat_id":42,"chat_type":"private","issued_at":1700000000,"text":"/link EXAMPLE"}
```

```json
{"seq":14,"event_id":"tg:123456:790","type":"callback","user_id":42,"chat_id":42,"chat_type":"private","issued_at":1700000000,"callback_id":"telegram-callback-id","data":"v2fa:ok:ABCDEFGHJKLMNPQR"}
```

Бот продвигает Telegram offset только после записи команды в SQLite. При потере ответа плагин повторяет
тот же cursor/event_id; очередь и receipts делают обработку идемпотентной.
Команды принимаются только из private chat, `user_id == chat_id > 0`, не от бота, возраст Telegram message ≤120 секунд.
Callback должен относиться к сообщению текущего bot ID. Плагин повторно проверяет private/owner/current attempt.
Произвольные поля вроде Minecraft UUID не используются. Код привязки проверяет AuthManager;
код `/login` вообще не передаётся этим endpoint — игрок вводит его в Minecraft.

## Health

- `GET /health` — liveness для хостинга: 200, пока процесс жив; не требует online Minecraft.
- `GET /healthz` — readiness: 200 только когда Telegram long polling готов и был свежий poll.

## Состояния и безопасность

- Привязка: одноразовый случайный токен, TTL 5 минут, выход игрока отменяет его; не более 10 попыток на Telegram ID/минуту.
- Вход: код создаёт SecureRandom в плагине; срок, лимит ошибок и таймаут проверяются на main thread.
- Resend: не чаще раза в 10 секунд, новый auth ID, лимит неверных попыток не сбрасывается.
- Кнопки: проверяются текущий UUID сессии, привязанный Telegram ID, срок и наличие игрока; после решения запись удаляется.
- Отказ/таймаут/выход/admin reset/plugin disable завершают ожидающую попытку, а не разрешают её.
- `/unlink` запрещён во время ожидающего входа и при заблокированном аккаунте.
- Доверенное устройство — сохранённый IP клиента: после успешного подтверждения входа этот IP пускает без кода,
  другие IP требуют 2FA. Minecraft-сервер не видит аппаратный идентификатор устройства.
- Подтверждения входа и детали устройств не печатаются в Minecraft-чат; они отправляются только в привязанный Telegram.
- `/kick` завершает текущий вход и сбрасывает короткую сессию, сохраняя доверенные устройства.
- `/revoke` и `/untrust all` удаляют все доверенные устройства, сбрасывают сессию, отменяют ожидающий вход и кикают онлайн-игрока.
- Эти команды принимаются только из личного чата и применяются только к Minecraft-аккаунту, привязанному к данному Telegram ID.
- Блокировка аккаунта сильнее `void2fa.bypass` и доверенных устройств: заблокированный аккаунт не пускает никого до разблокировки.
- При недоступности бота новые входы по умолчанию fail-open (`lockdown-if-unavailable: false`); это явно сохранённое поведение 1.0.0.

## Telegram API

Сервис использует `getMe`, `getWebhookInfo`, `setMyCommands`, `getUpdates`, `sendMessage`,
`editMessageText`, `answerCallbackQuery`. `setMyCommands` публикует меню команд при подключении;
его ошибка не останавливает транспорт. Он не вызывает `deleteWebhook`: наличие webhook — ошибка запуска,
чтобы не сломать чужую интеграцию молча. `getUpdates` — единственный consumer, offset сохраняется в SQLite.
409 останавливает сервис; 429 учитывает retry_after; сетевые ошибки повторяются с backoff.
Токен только из `BOT_TOKEN`/`BOT_TOKEN_FILE` (совместимые алиасы Bothost: `API_TOKEN`, `TELEGRAM_BOT_TOKEN`);
HTTP redirects и proxy environment отключены.

## Границы гарантий

Это второй фактор для сервера, а не полноценная защита от кражи Minecraft-сессии или скомпрометированного сервера.
Доставка Telegram-сообщений at-least-once: авария между отправкой и записью SQLite может продублировать сообщение,
но не авторизацию. Код остаётся в истории Telegram до ручного удаления/политики чата.
Мост и SQLite не рассчитаны на multi-server federation. Секреты и коды не логируются приложением;
операционная система, reverse proxy и Telegram имеют собственные политики хранения, которые нужно настроить отдельно.
