package me.darkz70.quirks.task;

import me.darkz70.quirks.Quirk;
import me.darkz70.quirks.VoidQuirksPlugin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * Физика паутины и стен — бежит каждый тик:
 * Паук полностью игнорирует замедление паутины и лазает по стенам,
 * у Инженера в паутине скорость выше (с 1 ур.).
 */
public final class WebPhysicsTask extends BukkitRunnable {

    private final VoidQuirksPlugin plugin;

    public WebPhysicsTask(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR || player.isFlying() || player.isInsideVehicle()) {
                continue;
            }
            int spider = plugin.quirks().levelOf(player, Quirk.SPIDER);
            int engineer = plugin.quirks().levelOf(player, Quirk.ENGINEER);
            if (spider == 0 && engineer == 0) continue;

            boolean inWeb = inCobweb(player);

            // компенсация замедления паутины (горизонтальная скорость) — множители из конфига
            if (inWeb) {
                double spiderMult = plugin.getConfig().getDouble("spider.web-speed-multiplier", 7.0);
                double engMult = plugin.getConfig().getDouble("engineer.web-speed-multiplier", 2.5);
                double mult = spider >= 1 ? spiderMult : (engineer >= 1 ? engMult : 0.0);
                if (mult > 0.0) {
                    Vector v = player.getVelocity();
                    v.setX(v.getX() * mult);
                    v.setZ(v.getZ() * mult);
                    player.setVelocity(v);
                }
            }

            // лазание по стенам: смотри вверх — ползёшь, шифт — висишь, иначе медленно сползаешь
            if (spider >= 1 && !player.isOnGround() && !player.isInWater() && touchingWall(player)) {
                Vector v = player.getVelocity();
                double y;
                if (player.isSneaking()) {
                    y = 0.0;
                } else if (player.getLocation().getPitch() < -45) {
                    y = 0.22;
                } else {
                    y = Math.max(v.getY(), -0.1);
                }
                v.setY(y);
                player.setVelocity(v);
                player.setFallDistance(0f);
            }
        }
    }

    /** Стоит ли игрок телом в блоке паутины (ноги или голова). */
    public static boolean inCobweb(Player player) {
        Block feet = player.getLocation().getBlock();
        if (feet.getType() == Material.COBWEB) return true;
        return feet.getRelative(BlockFace.UP).getType() == Material.COBWEB;
    }

    /** Есть ли твёрдый блок вплотную по горизонтали (стена, к которой можно прислониться). */
    private static boolean touchingWall(Player player) {
        Block feet = player.getLocation().getBlock();
        Block eye = feet.getRelative(BlockFace.UP);
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
            if (feet.getRelative(face).getType().isSolid() || eye.getRelative(face).getType().isSolid()) {
                return true;
            }
        }
        return false;
    }
}
