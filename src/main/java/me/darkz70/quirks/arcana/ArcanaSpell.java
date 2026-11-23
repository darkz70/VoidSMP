package me.darkz70.quirks.arcana;

/** Одно заклинание стихии: имя, слот, требуемый уровень, стоимость/кулдаун, эффект. */
public record ArcanaSpell(String id, String name, ArcanaSpellKind kind, int requiredLevel,
        double mana, double cooldownSeconds, ArcanaEffect effect) {
}
