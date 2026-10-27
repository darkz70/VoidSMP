package me.darkz70.voidsmp;

import io.papermc.paper.event.block.VaultChangeStateEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Vault;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.loot.LootTable;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Работа с хранилищами (Vault):
 * <ul>
 *   <li>обычным хранилищам ставит свою лут-таблицу (жутким/ominous — не трогает);</li>
 *   <li>запоминает все хранилища, которые игроки уже открывали;</li>
 *   <li>по сбросу снимает с них «открывших» игроков, чтобы можно было открыть снова.</li>
 * </ul>
 * Механику ключей плагин не меняет — открытие остаётся ванильным.
 */
public final class VaultManager implements Listener {

    /** Лут-таблица обычных хранилищ: voidsmp:chests/regular_vault */
    public static final NamespacedKey LOOT_TABLE_KEY = new NamespacedKey("voidsmp", "chests/regular_vault");

    private final JavaPlugin plugin;
    private final Path trackFile;
    private final Set<String> tracked = new LinkedHashSet<>();
    private boolean dirty = false;
    private boolean warnedMissingLoot = false;

    public VaultManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.trackFile = plugin.getDataFolder().toPath().resolve("tracked-vaults.txt");
    }

    // ------------------------------------------------------------------
    // lifecycle
    // ------------------------------------------------------------------

    public void onEnable() {
        loadTracked();
        int applied = scanLoadedChunks();
        plugin.getLogger().info("Хранилищ отслеживается: " + tracked.size() + ", лут применён при сканировании: " + applied);
    }

    public void onDisable() {
        saveTracked();
    }

    /** Периодическое сохранение списка (вызывается из таймера сброса). */
    public void tickSave() {
        saveTracked();
    }

    public int trackedCount() {
        return tracked.size();
    }

    // ------------------------------------------------------------------
    // применение лута
    // ------------------------------------------------------------------

    private int scanLoadedChunks() {
        int applied = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                applied += inspectChunk(chunk);
            }
        }
        return applied;
    }

    private int inspectChunk(Chunk chunk) {
        int applied = 0;
        for (BlockState state : chunk.getTileEntities(block -> block.getType() == Material.VAULT, true)) {
            if (inspectVault(state.getBlock())) {
                applied++;
            }
        }
        return applied;
    }

    /**
     * Проверяет одно хранилище: ставит лут-таблицу (только обычным)
     * и запоминает его, если по нему уже были «открывшие».
     *
     * @return true, если лут-таблица была установлена прямо сейчас
     */
    private boolean inspectVault(Block block) {
        BlockState state = block.getState();
        if (!(state instanceof Vault vault)) {
            return false;
        }

        boolean applied = false;
        org.bukkit.block.data.BlockData blockData = block.getBlockData();
        boolean ominous = blockData instanceof org.bukkit.block.data.type.Vault vaultData && vaultData.isOminous();

        if (!ominous) {
            // Обычное хранилище → наш лут. Жуткое (синее) → не трогаем.
            applied = applyLootTable(state, vault);
        }

        if (!vault.getRewardedPlayers().isEmpty()) {
            // Уже открывалось (обычное или жуткое) — помним до еженедельного сброса.
            track(block);
        }
        return applied;
    }

    private boolean applyLootTable(BlockState state, Vault vault) {
        LootTable table = Bukkit.getLootTable(LOOT_TABLE_KEY);
        if (table == null) {
            if (!warnedMissingLoot) {
                warnedMissingLoot = true;
                plugin.getLogger().warning("LootTable=MISSING: датапак voidsmp:chests/regular_vault не загружен — "
                    + "обычные хранилища остаются на ванильном луте. Проверь config.yml и /voidsmp reload.");
            }
            return false;
        }
        LootTable current = vault.getLootTable();
        if (current != null && LOOT_TABLE_KEY.equals(current.getKey())) {
            return false; // уже стоит
        }
        vault.setLootTable(table);
        state.update(true, false);
        return true;
    }

    private void track(Block block) {
        String key = block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
        if (tracked.add(key)) {
            dirty = true;
        }
    }

    // ------------------------------------------------------------------
    // еженедельный сброс
    // ------------------------------------------------------------------

    /**
     * Сбрасывает все отслеживаемые хранилища (обычные и жуткие):
     * снимает ограничение «игрок уже открыл», чтобы его открыли снова.
     *
     * @return число снятых ограничений
     */
    public int performReset() {
        int vaults = 0;
        int cleared = 0;
        int guard = 0;
        // Обрабатываем по одной записи: при подгрузке чанка обработчик может
        // вернуть запись обратно — тогда мы пройдём по ней ещё раз (к этому
        // моменту награды уже будут сняты, и повторно она не вернётся).
        while (!tracked.isEmpty() && guard++ < 200_000) {
            String key = tracked.iterator().next();
            tracked.remove(key);
            dirty = true;

            String[] parts = key.split(":");
            if (parts.length != 4) {
                continue;
            }
            UUID worldId;
            int x;
            int y;
            int z;
            try {
                worldId = UUID.fromString(parts[0]);
                x = Integer.parseInt(parts[1]);
                y = Integer.parseInt(parts[2]);
                z = Integer.parseInt(parts[3]);
            } catch (IllegalArgumentException ex) {
                continue; // мусорная запись — удалили
            }
            World world = Bukkit.getWorld(worldId);
            if (world == null) {
                continue; // мир удалён — запись тоже удалили
            }
            Chunk chunk = world.getChunkAt(x >> 4, z >> 4);
            BlockState state = chunk.getBlock(x & 15, y, z & 15).getState();
            if (!(state instanceof Vault vault)) {
                continue; // на месте уже нет хранилища
            }
            vaults++;
            for (UUID player : List.copyOf(vault.getRewardedPlayers())) {
                if (vault.removeRewardedPlayer(player)) {
                    cleared++;
                }
            }
            state.update(true, false);
        }
        saveTracked();
        plugin.getLogger().info("Сброс: проверено хранилищ " + vaults + ", снято ограничений " + cleared);
        return cleared;
    }

    // ------------------------------------------------------------------
    // события
    // ------------------------------------------------------------------

    /** Клик по хранилищу — применяем лут и запоминаем (события двойные: main/off hand — берём main). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.VAULT) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        inspectVault(block);
    }

    /** Игрок реально открыл хранилище — запоминаем (для будущего сброса). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onVaultState(VaultChangeStateEvent event) {
        if (event.isCancelled() || event.getPlayer() == null) {
            return;
        }
        if (event.getNewState() != org.bukkit.block.data.type.Vault.State.UNLOCKING) {
            return;
        }
        track(event.getBlock());
    }

    /** Новый чанк загружен — проставляем лут обычным хранилищам сразу, чтобы витрина показывала наш лут. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        inspectChunk(event.getChunk());
    }

    // ------------------------------------------------------------------
    // файл отслеживаемых хранилищ
    // ------------------------------------------------------------------

    private void loadTracked() {
        if (!Files.exists(trackFile)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(trackFile, StandardCharsets.UTF_8)) {
                String s = line.trim();
                if (!s.isEmpty() && !s.startsWith("#")) {
                    tracked.add(s);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Не удалось прочитать " + trackFile.getFileName(), e);
        }
    }

    private void saveTracked() {
        if (!dirty) {
            return;
        }
        try {
            Files.createDirectories(trackFile.getParent());
            Files.write(trackFile, List.copyOf(tracked), StandardCharsets.UTF_8);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Не удалось записать " + trackFile.getFileName(), e);
        }
    }
}
