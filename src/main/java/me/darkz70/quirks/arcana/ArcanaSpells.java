package me.darkz70.quirks.arcana;

/**
 * Таблица заклинаний Arcana: одинаковая структура слотов для всех 12 стихий
 * (см. задачу #99 — брекеты по уровню), конкретные эффекты берут окраску/партиклы/
 * звук из {@link Arcana}, а «блочные» и ульти — из {@link ArcanaBlockFx}/{@link ArcanaUltimates}.
 */
final class ArcanaSpells {

    private ArcanaSpells() {}

    static ArcanaSpell[] of(Arcana e) {
        String adj = e.adjective();
        return new ArcanaSpell[]{
            // L1 — боевое
            new ArcanaSpell(e.id() + ":bolt", adj + " удар", ArcanaSpellKind.COMBAT, 1, 8, 2.5,
                    (sys, p, el, lvl) -> ArcanaEffects.bolt(p, el, 3 + lvl * 0.4, 20)),
            // L2-3 — защитные
            new ArcanaSpell(e.id() + ":shield", adj + " щит", ArcanaSpellKind.DEFENSE, 2, 14, 16,
                    (sys, p, el, lvl) -> ArcanaEffects.shield(p, el, 6 + lvl / 2, Math.min(3, 1 + lvl / 6))),
            new ArcanaSpell(e.id() + ":dash", adj + " рывок", ArcanaSpellKind.DEFENSE, 3, 10, 10,
                    (sys, p, el, lvl) -> ArcanaEffects.dash(p, el, 1.3 + lvl * 0.03)),
            // L4-5 — временные способности
            new ArcanaSpell(e.id() + ":blessing", "Благословение " + adj.toLowerCase(), ArcanaSpellKind.ABILITY, 4, 16, 20,
                    (sys, p, el, lvl) -> ArcanaEffects.buff(p, el, el.buffA(), 12 + lvl / 2, Math.min(2, lvl / 8))),
            new ArcanaSpell(e.id() + ":gift", "Дар " + adj.toLowerCase(), ArcanaSpellKind.ABILITY, 5, 16, 20,
                    (sys, p, el, lvl) -> ArcanaEffects.buff(p, el, el.buffB(), 12 + lvl / 2, Math.min(2, lvl / 8))),
            // L6-8 — воздействие на блоки/территорию
            new ArcanaSpell(e.id() + ":touch", "Прикосновение " + adj.toLowerCase(), ArcanaSpellKind.BLOCK, 6, 18, 12,
                    (sys, p, el, lvl) -> ArcanaBlockFx.touch(sys.plugin(), p, el)),
            new ArcanaSpell(e.id() + ":wave", "Волна " + adj.toLowerCase(), ArcanaSpellKind.BLOCK, 7, 24, 18,
                    (sys, p, el, lvl) -> ArcanaBlockFx.wave(sys.plugin(), p, el)),
            new ArcanaSpell(e.id() + ":will", "Воля " + adj.toLowerCase(), ArcanaSpellKind.BLOCK, 8, 32, 26,
                    (sys, p, el, lvl) -> ArcanaBlockFx.will(sys.plugin(), p, el)),
            // L20 — уникальное мощное заклинание, открывает доступ ко 2-й стихии
            new ArcanaSpell(e.id() + ":ultimate", "Апогей: " + e.display(), ArcanaSpellKind.ULTIMATE, 20, 60, 90,
                    (sys, p, el, lvl) -> ArcanaUltimates.cast(sys.plugin(), p, el)),
        };
    }
}
