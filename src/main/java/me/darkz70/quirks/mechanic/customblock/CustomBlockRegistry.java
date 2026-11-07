package me.darkz70.quirks.mechanic.customblock;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/** Реестр зарегистрированных типов кастомных блоков VoidQuirks. */
public final class CustomBlockRegistry {

    private static final Map<String, CustomBlockType> TYPES = new LinkedHashMap<>();

    static {
        register(CustomBlockType.SCULK_WARD);
        register(CustomBlockType.SCULK_LOG);
        register(CustomBlockType.SCULK_LEAVES);
        register(CustomBlockType.SCULK_PLANKS);
    }

    private CustomBlockRegistry() {}

    public static void register(CustomBlockType type) {
        TYPES.put(type.id(), type);
    }

    @Nullable
    public static CustomBlockType get(String id) {
        return TYPES.get(id.toLowerCase(java.util.Locale.ROOT));
    }

    public static Collection<CustomBlockType> all() {
        return TYPES.values();
    }
}
