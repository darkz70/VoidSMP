package me.darkz70.quirks.magic;

/** Описание заклинания: имя, стоимость маны, кулдаун (сек), требуемый уровень магии. */
public record Spell(String name, int mana, long cooldownSeconds, int requiredLevel) {

    public static final int[] UNLOCK_LEVELS = {1, 10, 10, 20, 20, 30, 41};

    /** Семь заклинаний каждой стихии. */
    public static Spell[] of(Element element) {
        return switch (element) {
            case FIRE -> new Spell[]{
                new Spell("Искорки", 10, 30, 1),
                new Spell("Большой бум", 50, 30, 10),
                new Spell("Огненная аура", 100, 150, 10),
                new Spell("Огненный резист", 110, 300, 20),
                new Spell("Огненный профи", 200, 300, 20),
                new Spell("Огненный победитель", 250, 600, 30),
                new Spell("Огненный бог", 300, 600, 41)
            };
            case WATER -> new Spell[]{
                new Spell("Водяной шар", 10, 30, 1),
                new Spell("Волна", 50, 45, 10),
                new Spell("Ледяная аура", 100, 150, 10),
                new Spell("Водный резист", 110, 300, 20),
                new Spell("Ледяные шипы", 200, 300, 20),
                new Spell("Целитель глубин", 250, 600, 30),
                new Spell("Водный бог", 300, 600, 41)
            };
            case WIND -> new Spell[]{
                new Spell("Порыв", 10, 15, 1),
                new Spell("Воздушный клинок", 50, 30, 10),
                new Spell("Аура ветра", 100, 90, 10),
                new Spell("Резист ветра", 110, 300, 20),
                new Spell("Вихрь", 200, 240, 20),
                new Spell("Прыжок бури", 250, 450, 30),
                new Spell("Ветряной бог", 300, 600, 41)
            };
            case EARTH -> new Spell[]{
                new Spell("Каменный кулак", 10, 22, 1),
                new Spell("Каменный шар", 50, 30, 10),
                new Spell("Аура камня", 100, 150, 10),
                new Spell("Резист земли", 110, 300, 20),
                new Spell("Каменные шипы", 200, 300, 20),
                new Spell("Стена земли", 250, 450, 30),
                new Spell("Земляной бог", 300, 600, 41)
            };
            case DARK -> new Spell[]{
                new Spell("Тёмный шёпот", 10, 30, 1),
                new Spell("Теневое касание", 50, 30, 10),
                new Spell("Аура тьмы", 100, 150, 10),
                new Spell("Теневая защита", 110, 300, 20),
                new Spell("Теневые копии", 200, 300, 20),
                new Spell("Пожирание", 250, 450, 30),
                new Spell("Тёмный бог", 300, 600, 41)
            };
            case LIGHT -> new Spell[]{
                new Spell("Светящийся шар", 10, 30, 1),
                new Spell("Целительный свет", 50, 45, 10),
                new Spell("Аура света", 100, 150, 10),
                new Spell("Световой резист", 110, 300, 20),
                new Spell("Световой щит", 200, 300, 20),
                new Spell("Кара нежити", 250, 450, 30),
                new Spell("Светлый бог", 300, 600, 41)
            };
        };
    }
}
