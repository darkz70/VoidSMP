package me.darkz70.quirks.task;

import me.darkz70.quirks.PlayerData;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.listener.AxeListener;
import me.darkz70.quirks.mechanic.BedrockLogic;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Периодическая гарантия: пассивные эффекты, раскладка Бедрока,
 * голодный триггер Бедрока и зачистка мечей Топора.
 */
public final class EffectsTask extends BukkitRunnable {

    private final VoidQuirksPlugin plugin;

    public EffectsTask(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.storage().get(player.getUniqueId());
            if (data == null) continue;

            plugin.quirks().ensurePassives(player, data);

            switch (data.quirk()) {
                case BEDROCK -> {
                    BedrockLogic.applyLayout(plugin, player, data.level());
                    BedrockLogic.hungerCheck(plugin, player, data);
                }
                case AXE -> {
                    if (data.level() <= 2) {
                        AxeListener.sweepSwords(plugin, player);
                    }
                }
                default -> { }
            }
        }
    }
}
