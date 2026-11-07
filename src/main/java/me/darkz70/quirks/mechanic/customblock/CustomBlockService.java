package me.darkz70.quirks.mechanic.customblock;

import java.util.UUID;
import me.darkz70.quirks.Keys;
import me.darkz70.quirks.VoidQuirksPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Единая точка размещения/снятия "кастомных блоков" VoidQuirks (см. CustomBlockType).
 *
 * Технически — это BlockDisplay (Paper API): НЕ настоящий блок, значит даром
 * получаем "нельзя сломать" (ломать нечего) и "можно пройти насквозь" (нет
 * хитбокса) — ровно то же самое поведение, которого любой подобный плагин
 * (включая ExecutableBlocks) добивается доп. обработчиками событий поверх
 * настоящего блока. Любая будущая механика VoidQuirks может переиспользовать
 * этот сервис для своих собственных кастомных блоков — не только купол.
 */
public final class CustomBlockService {

    private final VoidQuirksPlugin plugin;

    public CustomBlockService(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    /** Поставить кастомный блок, растянутый по осям (1,1,1 = обычный блок). */
    public BlockDisplay place(World world, Location blockLoc, CustomBlockType type,
                               float scaleX, float scaleY, float scaleZ) {
        return world.spawn(blockLoc, BlockDisplay.class, bd -> {
            bd.setBlock(type.base().createBlockData());
            bd.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new AxisAngle4f(),
                    new Vector3f(scaleX, scaleY, scaleZ),
                    new AxisAngle4f()));
            bd.setBrightness(new Display.Brightness(15, 15));
            bd.setShadowRadius(0f);
            bd.setPersistent(true);
            bd.getPersistentDataContainer().set(Keys.customBlockType, PersistentDataType.STRING, type.id());
        });
    }

    public void remove(UUID entityId) {
        Entity e = Bukkit.getEntity(entityId);
        if (e instanceof BlockDisplay bd) bd.remove();
    }

    @Nullable
    public CustomBlockType typeOf(Entity entity) {
        String id = entity.getPersistentDataContainer().get(Keys.customBlockType, PersistentDataType.STRING);
        return id == null ? null : CustomBlockRegistry.get(id);
    }
}
