# Void2FA external bridge, protocol V2FA1 / version 1

Это документация реализованного моста в исходниках 1.1.0, не описание старого JAR 1.0.0.
Транспорт: HTTPS между машинами (HTTP только loopback/туннель), JSON, POST.
Плагин слушает `telegram.bridge.host:port`; бот инициирует оба запроса. Входящий порт Paper
наружу не требуется. Один секрет — один сервер и один бот.

## Аутентификация

Общий секрет: ровно 32 байта, закодированные 64 hex-символами или strict base64.
Заголовки запроса:

- `X-Void2FA-Timestamp`: Unix seconds; допуск ±30 секунд;
- `X-Void2FA-Nonce`: 22–64 символа `[A-Za-z0-9_-]`, случайный на каждый запрос;
- `X-Void2FA-Signature`: lowercase hex HMAC-SHA256;
- `Content-Type: application/json`.

Подписываемые байты (UTF-8, `\n` — один LF):

```text
V2FA1
request
/v1/poll
1700000000
NONCE
lowercase-hex(SHA256(raw HTTP body))
```

Для ответа второе поле — `response`, timestamp — время ответа, nonce — nonce запроса.
Ответ также содержит `Cache-Control: no-store`. Бот проверяет подпись, свежесть и совпадение nonce.
Плагин проверяет подпись до разбора JSON и принимает nonce один раз в окне 61 секунда.
Повтор подписанного запроса отклоняется. После рестарта Paper меняется `instance_id`,
поэтому старые команды не принимаются. Межмашинный трафик обязательно шифруется TLS/туннелем.

Тело запроса ограничено 65536 байтами. Ошибки 4xx/5xx не подписаны и не содержат данных аккаунта;
клиент принимает только подписанный HTTP 200. Никакой endpoint не принимает Minecraft UUID или команду «авторизовать UUID».

## POST /v1/poll

Запрос бота после `getMe` и проверки отсутствия webhook:

```json
{"version":1,"bot_id":123456,"bot_username":"example_bot","instance_id":"","cursor":0,"telegram_ready":true}
```

Ответ:

```json
{"version":1,"instance_id":"UUID","reset":false,"events":[],"sequence":0}
```

`cursor` — последняя сохранённая ботом позиция outbox. Следующий poll подтверждает предыдущую batch.
`reset=true` при новом instance или пропуске истёкших событий: бот очищает ссылки на старые сообщения,
cursor становится 0 и затем продвигается по `seq` полученных событий. События старше 10 минут удаляются;
очередь ограничена 1000 событиями. Пропуск не разрешает вход: попытка остаётся неподтверждённой до таймаута.
Бот опрашивает bridge раз в секунду; готовность плагина истекает через 15 секунд без poll.

Типы событий в `events`:

- `message`: `event_id`, `seq`, `chat_id`, `text` → `sendMessage`;
- `auth_request`: additionally `auth_id`, `approve_label`, `deny_label` → `sendMessage` с двумя inline buttons;
- `auth_edit`: `auth_id`, `chat_id`, `text` → `editMessageText` и удаление клавиатуры;
- `callback_answer`: `callback_id`, `text` → `answerCallbackQuery`.

Callback data создаётся плагином: `v2fa:ok:AUTH_ID` / `v2fa:no:AUTH_ID`.
AUTH_ID — случайный 16-символьный идентификатор конкретной попытки/высылки, не UUID игрока.
Бот хранит соответствие auth ID → Telegram message ID в SQLite. Все тексты рендерит плагин из `messages`.

## POST /v1/command

Личное текстовое сообщение:

```json
{"version":1,"bot_id":123456,"instance_id":"UUID","cursor":12,"event_id":"tg:123456:789","type":"message","user_id":42,"chat_id":42,"chat_type":"private","issued_at":1700000000,"text":"/link EXAMPLE"}
```

Нажатие кнопки:

```json
{"version":1,"bot_id":123456,"instance_id":"UUID","cursor":12,"event_id":"tg:123456:790","type":"callback","user_id":42,"chat_id":42,"chat_type":"private","issued_at":1700000000,"callback_id":"telegram-callback-id","data":"v2fa:ok:ABCDEFGHJKLMNPQR"}
```

Ответ: `{"version":1,"accepted":true,"duplicate":false}`.

Бот продвигает offset только после принятого ответа. При потере ответа повторяет тот же `event_id`.
Плагин хранит receipts 10 минут (до 20000), поэтому повтор не выполняет link/unlink второй раз.
Команды принимаются только из private chat, `user_id == chat_id > 0`, не от бота, возраст Telegram message ≤120 секунд.
Callback должен относиться к сообщению текущего bot ID. Плагин повторно проверяет private/owner/current attempt.
Произвольные поля вроде Minecraft UUID не используются. Код привязки проверяет AuthManager;
код `/login` вообще не передаётся этим endpoint — игрок вводит его в Minecraft.

## Состояния и безопасность

- Привязка: одноразовый случайный токен, TTL 5 минут, выход игрока отменяет его; не более 10 попыток на Telegram ID/минуту.
- Вход: код создаёт SecureRandom в плагине; срок, лимит ошибок и таймаут проверяются на main thread.
- Resend: не чаще раза в 10 секунд, новый auth ID, лимит неверных попыток не сбрасывается.
- Кнопки: проверяются текущий UUID сессии, привязанный Telegram ID, срок и наличие игрока; после решения запись удаляется.
- Отказ/таймаут/выход/admin reset/plugin disable завершают ожидающую попытку, а не разрешают её.
- `/unlink` запрещён во время ожидающего входа.
- При недоступности бота новые входы по умолчанию fail-open (`lockdown-if-unavailable: false`); это явно сохранённое поведение 1.0.0.

## Telegram API

Сервис использует `getMe`, `getWebhookInfo`, `getUpdates`, `sendMessage`, `editMessageText`,
`answerCallbackQuery`. Он не вызывает `deleteWebhook`: наличие webhook — ошибка запуска,
чтобы не сломать чужую интеграцию молча. `getUpdates` — единственный consumer, offset сохраняется в SQLite.
409 останавливает сервис; 429 учитывает retry_after; сетевые ошибки повторяются с backoff.
Токен только из `BOT_TOKEN`/`BOT_TOKEN_FILE`; HTTP redirects и proxy environment отключены.

## Границы гарантий

Это второй фактор для сервера, а не полноценная защита от кражи Minecraft-сессии или скомпрометированного сервера.
Доставка Telegram-сообщений at-least-once: авария между отправкой и записью SQLite может продублировать сообщение,
но не авторизацию. Код остаётся в истории Telegram до ручного удаления/политики чата.
Мост и SQLite не рассчитаны на multi-server federation. Секреты и коды не логируются приложением;
операционная система, reverse proxy и Telegram имеют собственные политики хранения, которые нужно настроить отдельно.
