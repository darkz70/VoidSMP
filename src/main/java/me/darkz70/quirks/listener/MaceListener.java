package me.darkz70.quirks.listener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.mechanic.RelicItems;
import me.darkz70.quirks.util.Msg;
import me.darkz70.quirks.util.ScanUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

/**
 * #99: регулируемый кулдаун булавы (в секундах, задаётся админом) + реликвия
 * «Nevergate's Mace» (урон ×5, тряска экрана, пропадающие блоки у удара) и её
 * одноразовый двойник «nevergate's mace» (маленькая буква — исчезает после удара).
 * АдминПро снимает и кулдаун булавы, и реликвии не касается (у реликвий своего
 * кулдауна нет — они просто бьют всегда сильнее).
 */
public final class MaceListener implements Listener {

    private static final String CD_KEY = "mace-attack";
    private static final String CD_MSG_KEY = "mace-attack-msg";

    private final VoidQuirksPlugin plugin;

    public MaceListener(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMaceHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon.getType() != Material.MACE) return;

        double cdSeconds = plugin.getConfig().getDouble("mace.cooldown-seconds", 0.0);
        if (cdSeconds > 0) {
            long cdMs = Math.round(cdSeconds * 1000.0);
            if (!ScanUtil.tryUse(player.getUniqueId(), CD_KEY, cdMs)) {
                event.setCancelled(true);
                long remaining = ScanUtil.remaining(player.getUniqueId(), CD_KEY, cdMs);
                if (ScanUtil.tryUse(player.getUniqueId(), CD_MSG_KEY, 500)) {
                    player.sendActionBar(Msg.color("&cБулава перезаряжается: &f"
                            + formatSeconds(remaining) + "с"));
                }
                return;
            }
        }

        boolean once = RelicItems.isNevergateMaceOnce(weapon);
        if (!once && !RelicItems.isNevergateMace(weapon)) return;

        event.setDamage(event.getDamage() * 5.0);
        Location hit = event.getEntity().getLocation();
        shakeScreens(hit, 50);
        vanishBlocks(hit, ThreadLocalRandom.current().nextInt(3, 16)); // 3..15 включительно

        if (once) {
            Bukkit.getScheduler().runTask(plugin, () -> consumeMainHand(player));
        }
    }

    private static String formatSeconds(long ms) {
        double s = ms / 1000.0;
        return s == Math.floor(s) ? String.valueOf((long) s) : String.format("%.1f", s);
    }

    private void shakeScreens(Location center, double radius) {
        World world = center.getWorld();
        if (world == null) return;
        double r2 = radius * radius;
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(center) <= r2) {
                p.playHurtAnimation(180f);
            }
        }
    }

    private void vanishBlocks(Location center, int count) {
        World world = center.getWorld();
        if (world == null) return;
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        int r = 3;
        List<Block> candidates = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    Block block = world.getBlockAt(cx + dx, cy + dy, cz + dz);
                    if (canErase(block)) candidates.add(block);
                }
            }
        }
        Collections.shuffle(candidates);
        int n = Math.min(count, candidates.size());
        for (int i = 0; i < n; i++) {
            Block block = candidates.get(i);
            world.spawnParticle(Particle.EXPLOSION, block.getLocation().add(0.5, 0.5, 0.5), 1, 0, 0, 0, 0);
            block.setType(Material.AIR);
        }
        if (n > 0) {
            world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.2f, 0.6f);
        }
    }

    private static boolean canErase(Block block) {
        Material type = block.getType();
        if (type.isAir()) return false;
        return switch (type) {
            case BEDROCK, BARRIER, COMMAND_BLOCK, CHAIN_COMMAND_BLOCK, REPEATING_COMMAND_BLOCK,
                 STRUCTURE_BLOCK, STRUCTURE_VOID, WATER, LAVA, END_PORTAL, END_PORTAL_FRAME,
                 END_GATEWAY, SPAWNER, JIGSAW -> false;
            default -> type.isSolid();
        };
    }

    private void consumeMainHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() != Material.MACE) return;
        if (hand.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        } else {
            hand.setAmount(hand.getAmount() - 1);
        }
        player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 0.6f, 1.4f);
    }
}
