# VoidSMP

Плагины сервера VoidSMP. Этот репозиторий сейчас содержит **VoidSpawnRates** —
зоны скорости спавна мобов, которые привязываются к координатам и переживают
рестарт сервера.

VoidSMP Plugin © 2026 by darkz70 is licensed under CC BY-ND 4.0.
To view a copy of this license, visit https://creativecommons.org/licenses/by-nd/4.0/

---

## VoidSpawnRates

Зона = кубоид в мире + набор правил: общий множитель спавна, правила по
конкретным мобам, лимит мобов внутри зоны и (опционально) удаление лишних.
Всё это сразу после изменения пишется в SQLite, поэтому «место, где зону
сделали», остаётся зоной после `stop`/`start` сервера, `/reload` и выгрузки
чанков.

### Сборка

```bash
gradle clean build        # Java 25, артефакт в build/libs/
```

Требуется Paper 26.2 (`api-version: '26.2'`). Внешних зависимостей нет:
JDBC-драйвер SQLite кладёт на свой classloader сам сервер, поэтому jar не
жирнеет и shade не нужен.

### Быстрый старт

```
/spawnrate wand                      — палка выделения (как в WorldEdit)
ЛКМ по блоку → точка 1, ПКМ → точка 2
/spawnrate create farm                — создать зону из выделения
/spawnrate set farm multiplier 0.2    — в пятер times меньше мобов
/spawnrate set farm cap 30            — не больше 30 мобов в зоне
/spawnrate mob farm zombie 0          — запретить конкретно зомби
/spawnrate show farm                  — рамка зоны частицами
```

Зона сохранена в `plugins/VoidSpawnRates/data.db` сразу после команды: следующий
рестарт её не сотрёт. `save`/`reload` нужны только чтобы посмотреть статус
хранилища или перечитать конфиг.

### Команды

| Команда | Что делает | Право |
| --- | --- | --- |
| `/spawnrate wand` | выдаёт палку выделения | admin |
| `/spawnrate pos1 [x y z [мир]]`, `pos2` | задаёт точки без палки | admin |
| `/spawnrate create <имя>` | создаёт зону из выделения | admin |
| `/spawnrate resize <имя>` | применяет выделение к существующей зоне | admin |
| `/spawnrate delete <имя>` | удаляет зону и запись из базы | admin |
| `/spawnrate toggle <имя>` | включает/выключает зону, не удаляя настройки | admin |
| `/spawnrate set <имя> multiplier <0.0-N>` | множитель спавна | admin |
| `/spawnrate set <имя> cap <N\|off>` | максимум мобов в зоне | admin |
| `/spawnrate set <имя> filter <all\|monsters\|animals\|water\|ambient\|misc>` | кого регулировать | admin |
| `/spawnrate set <имя> spawners <true\|false>` | влиять ли на спавнеры | admin |
| `/spawnrate set <имя> despawn <true\|false>` | удалять лишних мобов при превышении cap | admin |
| `/spawnrate set <имя> priority <N>` | кто побеждает при пересечении зон | admin |
| `/spawnrate mob <имя> <тип\|all> <множитель\|remove>` | правило по конкретному моду | admin |
| `/spawnrate mobs <имя>` | список правил по мобам | view |
| `/spawnrate list [страница]`, `info <имя>`, `near`, `stats` | просмотр | view |
| `/spawnrate show <имя>`, `hide` | рамка зоны частицами | view |
| `/spawnrate tp <имя>` | телепорт в центр зоны | teleport |
| `/spawnrate save`, `reload`, `debug [true\|false]` | обслуживание | admin |

Права: `voidspawnrates.admin`, `voidspawnrates.view`, `voidspawnrates.teleport`
(по умолчанию — OP).

### Как считается «спавн рейт»

* `0.0` — спавна нет. Работает через `PreCreatureSpawnEvent`: моб даже не
  создаётся, а `setShouldAbortSpawn` просит ваниллу не перебирать дальше точки
  этого чанка.
* `0.0 < m < 1.0` — пропускается `m`·100% попыток, остальное отменяется.
* `m > 1.0` — `floor(m − 1)` + дробная часть мобов того же типа добавляются
  рядом (`spawn.extra-spawns`). Отменой спавна «чаще» сделать нельзя, поэтому
  здесь именно добавка сущностей.
* `cap` — лимит живых мобов в зоне. Счётчик обновляется раз в
  `cap.check-interval-ticks`, поэтому лимит приближённый, а не мгновенный;
  с `despawn: true` лишние мобы ещё и удаляются (с краёв зоны, именованные и
  седлаемые — нет).
* Яйца спавна, `/summon`, разведение и построенные големы не затрагиваются:
  в обработку попадают только причины из `spawn.reasons`.
* Если точка попадает сразу в несколько зон, их сводит `spawn.overlap-policy`:
  `PRIORITY` — решает самая приоритетная действующая зона (приоритет 10 с
  множителем `1.0` перекрывает запрет с приоритетом 0 внутри неё);
  `MULTIPLY` — множители перемножаются; `STRICTEST` — берётся наименьший.
  `filter` зоны и её отключение (`toggle`) снимают зону с рассмотрения целиком.

### Хранение

`plugins/VoidSpawnRates/data.db` — SQLite, схема `vsr_zones` + `vsr_zone_rules`,
WAL, запись из отдельного потока, синхронный flush при выключении плагина.
`storage.type: AUTO` при недоступном драйвере или битом файле сам переходит на
`zones.yml`, так что зоны не теряются и в этом случае; `SQLITE` — только база
(не получилось — плагин честно отключится вместо работы без сохранения).
`zones.yml`, найденный рядом с пустой базой, импортируется один раз и
переименовывается в `zones.yml.imported`.

Сообщения — в `plugins/VoidSpawnRates/messages.yml`, формат MiniMessage
(`<green>`, `<#a78bfa>`, плейсхолдеры вида `{name}`).
