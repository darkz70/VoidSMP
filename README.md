# VoidSMP

Плагины для сервера VoidSMP (Paper 26.2).

## Модули

- **[Void2FA](Void2FA/)** — двухфакторная аутентификация (2FA) через Telegram-бота.
  Подтверждение входа кодом или кнопками, заморозка до подтверждения, привязка
  аккаунта, режимы `opt-in`/`required`. Готовый `.jar`: [`Void2FA/release/`](Void2FA/release/).
  Тексты и инструкция для подключения своего бота позже: [`Void2FA/BOT_SETUP.md`](Void2FA/BOT_SETUP.md).

## Сборка

Каждый модуль — отдельный Gradle-проект. Пример для Void2FA:

```bash
cd Void2FA
gradle build
```

Требуется JDK 25 и доступ к `repo.papermc.io`.

---

VoidSMP Plugin © 2026 by darkz70 is licensed under CC BY-ND 4.0.
To view a copy of this license, visit https://creativecommons.org/licenses/by-nd/4.0/
