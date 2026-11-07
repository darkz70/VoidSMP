# VoidSMP

Плагины для сервера VoidSMP (Paper 26.2).

## Модули

- **[Void2FA](Void2FA/)** — двухфакторная аутентификация (2FA) через Telegram-бота.
  Подтверждение входа кодом или кнопками, заморозка до подтверждения, привязка
  аккаунта, режимы `opt-in`/`required`, доверенные устройства (IP), блокировка аккаунта,
кик и отзыв доступа со всех устройств через Telegram (`/kick`, `/revoke`).
  Актуальный JAR 1.3.2 собирается CI (артефакт `Void2FA-plugin`); в [`Void2FA/release/`](Void2FA/release/)
  лежит старый 1.0.0. Тексты BotFather: [`Void2FA/BOT_SETUP.md`](Void2FA/BOT_SETUP.md).

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

## Void2FA / Telegram

- [Плагин и сценарии](Void2FA/README.md)
- [Отдельный Python-бот: Bothost и развёртывание](Void2FA/BOT_DEPLOY.md)
- [Тексты BotFather](Void2FA/BOT_SETUP.md)
- [Протокол защищённого моста](Void2FA/BRIDGE_PROTOCOL.md)

Бот-аккаунт создаётся владельцем через BotFather. Токены и секреты не хранятся в репозитории.
