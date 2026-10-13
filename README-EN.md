# VoidSMP Quirks (VoidQuirks)

A Paper plugin ("SMP creature design" kit): **quirks, elemental magic, custom potions, foliant books,
a soul world, harebringer mobs and a full guidebook menu** — all in one jar.

## Features

- **8 quirks** (Engineer, Cat, Bedrock, Axe, Sculk, Farmer, Amphibian, Spider), each with
  3 upgradeable levels, perks and tradeoffs (e.g. cats scare creepers & phantoms, spiders
  shoot webs, the Bedrock immortality aura, …).
- **Elemental magic**: fire / water / wind / earth / dark / light. 10 spells per element,
  mana, cooldowns, escalating spell books (tiers I–V) crafted with a Focus Crystal.
- **Brewing overhaul**: 30+ custom potions (antidotes, berserk, god, archimage, madness,
  wings, necromancers, plague, …) plugin-ready for survivals lore; tipped arrows mirror
  every arrowable potion (5 s of its effect).
- **Soul world / hub**: `/quirk lab create|tp|back` — a barren void world (or your own
  prebuilt map folder) with remember-exit teleport back.
- **Harebringers**: 1.5× rabbits with five roles (farmer, guard, elite guard, resident,
  tsar). Admin-only spawning: `/quirk zayce colony|<role>|purge`. Their tsar bows to
  the Bedrock quirk and summons elites when hit.
- **Guidebook UI** `/gaid`: rule pages, item spawner for content makers, quality-of-life
  controls — used for SMP event moderation.
- Comes with a **resource pack** (`resourcepack/` → zipped as `VoidQuirks-Pack.zip`) covering
  foliant books, tier books, focus crystal and the soul shard.

## Install

1. Paper **26.2** server (Java 25, Paper API 26.2.build.129+).
2. Drop `VoidQuirks-<version>.jar` into `plugins/`.
3. Zip `resourcepack/` and host it (or use `VoidQuirks-Pack.zip` from the release), then
   put the link into `server.properties → resource-pack=` and `/quirk pack` shows the link.
4. Default language is Russian. **For English**, set `language: en` at the top of
   `plugins/VoidQuirks/config.yml` — the bundled English pack picks up automatically.
   (Full English config template also ships in `docs/config-en.yml` if you prefer files.)

## Commands & permissions

| Command | What it does | Permission |
|---|---|---|
| `/quirk set <player> <quirk> [lvl]`, `/quirk remove <player> [quirk]` | assign/remove quirks (admin or temp) | `quirks.admin` |
| `/quirk info/list/notify/reload` | inspect, list, toggle reminders, reload config | `quirks.admin` (reload) |
| `/quirk item <quirk> <lvl> [player]` | give soul shard of a quirk | `quirks.admin` |
| `/quirk lab create/tp/back` | soul-world create & teleports | `quirks.admin` |
| `/quirk zayce colony/<role>/purge` | harebringer management | `quirks.admin` |
| `/magic [menu]` | player's spellbook menu | — |
| `/magic book|upbook|brew|set …` | give books/styles/potions, tune players | `quirks.admin` |
| `/gaid` | guidebook admin menu | `quirks.admin` |

All timings, radii, cooldowns, mana and the potion matrix are tweakable in `config.yml`.

## Localization

- Russian: build-in defaults (`Msg` defaults + shipped `config.yml`).
- English: set `language: en` in `config.yml` — jar-bundled `messages-en.yml` kicks in
  (159 translated keys). `docs/config-en.yml` is the same content as a plain file.
  Any `messages:` key in your own config.yml still wins over the bundle.

Note: guidebook page prose and some item lores are still RU (contributions welcome).

## License

VoidSMP Plugin © 2026 by darkz70 is licensed under **CC BY-ND 4.0**.
To view a copy of this license, visit https://creativecommons.org/licenses/by-nd/4.0/

Share with attribution; no derivatives may be distributed (see `LICENSE`).
