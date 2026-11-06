package me.darkz70.quirks.listener;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.darkz70.quirks.Keys;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.mob.ZayceKrol;
import me.darkz70.quirks.util.Msg;
import me.darkz70.quirks.util.ScanUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fox;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Warden;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Причуда «XYZ» — админ-инструмент вселения в мобов (по заявке Лёши: "на примере
 * зайцекролов [кроликов], но со своими приколами"). Shift+ПКМ по живому, ещё не
 * занятому мобу поддерживаемого вида — и админ вселяется в него: невидимый игрок
 * сам управляет перемещением, настоящий моб каждый тик телепортируется к нему
 * (визуально это выглядит так, будто игрок и есть моб), хотбар занят способностями.
 * Только для ОП/quirks.admin — без уровней причуды, доступно сразу всё.
 *
 * Поддерживаемые виды: Кролик (как раньше), Блейз, Варден, Лиса, Пиглин, Житель —
 * у каждого свой набор из 6 способностей + выход.
 */
public final class XyzPossessionListener implements Listener {

    private final VoidQuirksPlugin plugin;

    public XyzPossessionListener(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Поддерживаемые виды мобов для вселения. У шести — свой уникальный набор
     * способностей. GENERIC — вообще ЛЮБОЙ другой живой моб игры (зомби, крипер,
     * корова, дракон Края и т.д.) с тем же универсальным набором, что раньше был
     * только у кролика (бой/рывок/коготь/полёт/взрывы) — Лёша попросил "всех мобов".
     */
    private enum Kind {
        RABBIT, BLAZE, WARDEN, FOX, PIGLIN, VILLAGER, GENERIC
    }

    private static Kind kindOf(Entity e) {
        if (e instanceof Rabbit) return Kind.RABBIT;
        if (e instanceof Blaze) return Kind.BLAZE;
        if (e instanceof Warden) return Kind.WARDEN;
        if (e instanceof Fox) return Kind.FOX;
        if (e instanceof Piglin) return Kind.PIGLIN;
        if (e instanceof Villager) return Kind.VILLAGER;
        if (e instanceof Player) return null; // в других игроков не вселяемся
        if (e instanceof org.bukkit.entity.ArmorStand) return null; // не живой моб
        if (e instanceof LivingEntity) return Kind.GENERIC;
        return null;
    }

    private static final class State {
        final Kind kind;
        final UUID mobId;
        final ItemStack[] savedHotbar;
        final boolean savedAllowFlight;
        final boolean savedFlying;
        BukkitTask followTask;

        State(Kind kind, UUID mobId, ItemStack[] savedHotbar, boolean savedAllowFlight, boolean savedFlying) {
            this.kind = kind;
            this.mobId = mobId;
            this.savedHotbar = savedHotbar;
            this.savedAllowFlight = savedAllowFlight;
            this.savedFlying = savedFlying;
        }
    }

    private final Map<UUID, State> active = new HashMap<>();

    public boolean isPossessing(Player player) {
        return active.containsKey(player.getUniqueId());
    }

    // ---------- описания способностей по видам (иконка, id, имя, лор) ----------

    private record Ability(Material icon, String id, String name, String... lore) { }

    private static final Map<Kind, Ability[]> ABILITIES = new EnumMap<>(Kind.class);

    static {
        ABILITIES.put(Kind.RABBIT, new Ability[]{
            new Ability(Material.BONE, "bite", "&cУкус", "&7Ближний бой: 6 урона + яд."),
            new Ability(Material.RABBIT_FOOT, "ram", "&6Таран-прыжок", "&7Рывок вперёд, сбивает всех на пути."),
            new Ability(Material.PHANTOM_MEMBRANE, "claw", "&dКоготь ярости",
                    "&7Широкий удар по всем рядом + замедление."),
            new Ability(Material.FEATHER, "fly", "&bПолёт", "&7Как в креативе, пока ты в теле моба."),
            new Ability(Material.BLAZE_POWDER, "boom_small", "&6Малый взрыв",
                    "&73×3, 10 урона.", "&8Блоки не трогает."),
            new Ability(Material.NETHER_STAR, "boom_big", "&4Большой взрыв",
                    "&79×9, 15 урона.", "&8Ломает 5×5 блоков и поджигает."),
        });
        ABILITIES.put(Kind.BLAZE, new Ability[]{
            new Ability(Material.FIRE_CHARGE, "fireball", "&6Огненный шар",
                    "&7Запускает снаряд блейза вперёд."),
            new Ability(Material.BLAZE_ROD, "volley", "&6Огненная очередь",
                    "&7Три шара веером."),
            new Ability(Material.MAGMA_CREAM, "flame_slash", "&cОгненный взмах",
                    "&7Удар по всем рядом + поджиг."),
            new Ability(Material.FEATHER, "fly", "&bПолёт", "&7Блейзы и так летают — креатив-полёт."),
            new Ability(Material.BLAZE_POWDER, "fire_pillar", "&6Огненный столб",
                    "&73×3 в точке прицела, поджигает.", "&8Блоки не трогает."),
            new Ability(Material.NETHER_STAR, "meteor", "&4Метеоритный обвал",
                    "&79×9, поджигает площадь.", "&8Ломает 5×5 блоков."),
        });
        ABILITIES.put(Kind.WARDEN, new Ability[]{
            new Ability(Material.ECHO_SHARD, "claw", "&5Коготь", "&7Тяжёлый ближний удар, 12 урона."),
            new Ability(Material.SCULK_SENSOR, "sonic", "&3Соник-рывок",
                    "&7Луч звука вперёд: урон + отброс всем на линии."),
            new Ability(Material.SCULK_SHRIEKER, "roar", "&8Рёв",
                    "&7АоЕ тьма + замедление рядом, без урона."),
            new Ability(Material.SCULK_VEIN, "burrow", "&2Подземный бросок",
                    "&7Рывок сквозь блоки на ~6 блоков вперёд."),
            new Ability(Material.SOUL_LANTERN, "echo", "&bЭхолокация",
                    "&7Подсвечивает всех рядом на 5 секунд."),
            new Ability(Material.NETHER_STAR, "sonic_ultimate", "&4Крик разрушения",
                    "&7Большой соник-взрыв, 9×9.", "&8Ломает 5×5 блоков."),
        });
        ABILITIES.put(Kind.FOX, new Ability[]{
            new Ability(Material.BONE, "bite", "&cУкус", "&74 урона."),
            new Ability(Material.RABBIT_FOOT, "pounce", "&6Прыжок-поимка", "&7Рывок вперёд + сбивает цель."),
            new Ability(Material.SWEET_BERRIES, "scavenge", "&eВорюга",
                    "&7Притягивает к себе все предметы на земле рядом."),
            new Ability(Material.GLOW_BERRIES, "night_vision", "&bНочное зрение", "&7Включить/выключить."),
            new Ability(Material.PHANTOM_MEMBRANE, "ambush", "&dЗасада",
                    "&73×3 в точке прицела, 10 урона.", "&8Блоки не трогает."),
            new Ability(Material.NETHER_STAR, "pack_cry", "&4Крик стаи",
                    "&7АоЕ страх: отброс, слепота и урон 9×9."),
        });
        ABILITIES.put(Kind.PIGLIN, new Ability[]{
            new Ability(Material.GOLDEN_SWORD, "strike", "&6Удар клинком", "&76 урона."),
            new Ability(Material.CROSSBOW, "crossbow", "&eАрбалетный выстрел", "&7Стрела вперёд."),
            new Ability(Material.GOLD_INGOT, "gold_rush", "&6Золотая лихорадка", "&7Рывок вперёд, сбивает путь."),
            new Ability(Material.GOLD_NUGGET, "gold_sense", "&eНюх на золото",
                    "&7Подсвечивает золотые руды/блоки рядом на 5 сек."),
            new Ability(Material.BLAZE_POWDER, "barter", "&6Бартер-взрыв",
                    "&73×3, 10 урона.", "&8Блоки не трогает."),
            new Ability(Material.NETHER_STAR, "horde", "&4Ярость орды",
                    "&79×9, 15 урона.", "&8Ломает 5×5 блоков."),
        });
        ABILITIES.put(Kind.VILLAGER, new Ability[]{
            new Ability(Material.BREAD, "slap", "&eУдар подносом", "&73 урона (несерьёзно)."),
            new Ability(Material.EMERALD, "haggle", "&aТорговля", "&7Реген + поглощение себе на 8 сек."),
            new Ability(Material.BELL, "scream", "&fКрик жителя", "&7АоЕ слепота + отброс, без урона."),
            new Ability(Material.GOLDEN_APPLE, "cure", "&dИсцеление",
                    "&7Снять весь дебаф + подлечиться."),
            new Ability(Material.BLAZE_POWDER, "panic", "&6Паническая волна",
                    "&73×3, 8 урона.", "&8Блоки не трогает."),
            new Ability(Material.IRON_INGOT, "golem", "&bПризыв голема",
                    "&7Временный железный голем-союзник на 30 сек."),
        });
        // GENERIC — любой другой живой моб игры (зомби, корова, дракон Края и т.д.):
        // тот же набор, что был у кролика с самого начала.
        ABILITIES.put(Kind.GENERIC, ABILITIES.get(Kind.RABBIT));
    }

    // ---------- вход/выход ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEntityEvent event) {
        Player admin = event.getPlayer();
        Kind kind = kindOf(event.getRightClicked());
        if (kind == null) return;
        if (!admin.isSneaking()) return;
        if (!admin.isOp() && !admin.hasPermission("quirks.admin")) return;
        LivingEntity mob = (LivingEntity) event.getRightClicked();
        if (kind == Kind.RABBIT && ZayceKrol.isZayce((Rabbit) mob)) return; // у зайцекролов своё меню
        if (mob.getPersistentDataContainer().has(Keys.noLoot, PersistentDataType.BYTE)) {
            return; // временный призванный моб (наш голем, саммоны магии и т.п.) — не для вселения
        }
        if (mob.getPersistentDataContainer().has(Keys.xyzPossessedBy, PersistentDataType.STRING)) {
            admin.sendMessage(Msg.color("&cЭтого моба уже кто-то занял."));
            return;
        }
        event.setCancelled(true);
        if (isPossessing(admin)) {
            admin.sendMessage(Msg.color("&cТы уже в ком-то сидишь — сначала выйди (слот 9)."));
            return;
        }
        enter(admin, mob, kind);
    }

    private void enter(Player admin, LivingEntity mob, Kind kind) {
        ItemStack[] saved = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            ItemStack item = admin.getInventory().getItem(i);
            saved[i] = item == null ? null : item.clone();
            admin.getInventory().setItem(i, null);
        }
        Ability[] abilities = ABILITIES.get(kind);
        for (int i = 0; i < abilities.length && i < 6; i++) {
            Ability a = abilities[i];
            admin.getInventory().setItem(i, abilityItem(a.icon(), a.id(), a.name(), a.lore()));
        }
        admin.getInventory().setItem(8, abilityItem(Material.BARRIER, "exit",
                "&cВыйти из моба", "&7Вернёт тебе инвентарь."));

        admin.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                Integer.MAX_VALUE, 0, true, false, false));

        mob.setAI(false);
        mob.setInvulnerable(true);
        mob.setSilent(true);
        mob.setPersistent(true);
        mob.setCollidable(false);
        mob.getPersistentDataContainer().set(Keys.xyzPossessedBy, PersistentDataType.STRING,
                admin.getUniqueId().toString());

        State state = new State(kind, mob.getUniqueId(), saved, admin.getAllowFlight(), admin.isFlying());
        active.put(admin.getUniqueId(), state);

        state.followTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Entity e = Bukkit.getEntity(state.mobId);
            if (!admin.isOnline() || !(e instanceof LivingEntity le) || !le.isValid()) {
                exit(admin);
                return;
            }
            le.teleport(admin.getLocation());
        }, 0L, 1L);

        admin.sendMessage(Msg.color("&a✦ Ты вселился в " + displayName(kind, mob)
                + "! &7Слоты 1-6 — способности, 9 — выйти."));
    }

    /** Склонённые русские названия частых мобов для GENERIC; для редких — английское имя типа. */
    private static final Map<org.bukkit.entity.EntityType, String> MOB_NAMES = new EnumMap<>(
            org.bukkit.entity.EntityType.class);

    static {
        MOB_NAMES.put(org.bukkit.entity.EntityType.ZOMBIE, "зомби");
        MOB_NAMES.put(org.bukkit.entity.EntityType.SKELETON, "скелета");
        MOB_NAMES.put(org.bukkit.entity.EntityType.CREEPER, "крипера");
        MOB_NAMES.put(org.bukkit.entity.EntityType.ENDERMAN, "эндермена");
        MOB_NAMES.put(org.bukkit.entity.EntityType.SPIDER, "паука");
        MOB_NAMES.put(org.bukkit.entity.EntityType.CAVE_SPIDER, "пещерного паука");
        MOB_NAMES.put(org.bukkit.entity.EntityType.SILVERFISH, "чешуйницу");
        MOB_NAMES.put(org.bukkit.entity.EntityType.ENDERMITE, "эндермита");
        MOB_NAMES.put(org.bukkit.entity.EntityType.SLIME, "слайма");
        MOB_NAMES.put(org.bukkit.entity.EntityType.MAGMA_CUBE, "магма-куба");
        MOB_NAMES.put(org.bukkit.entity.EntityType.GHAST, "гаста");
        MOB_NAMES.put(org.bukkit.entity.EntityType.HOGLIN, "хоглина");
        MOB_NAMES.put(org.bukkit.entity.EntityType.ZOGLIN, "зоглина");
        MOB_NAMES.put(org.bukkit.entity.EntityType.ZOMBIFIED_PIGLIN, "зомбифицированного пиглина");
        MOB_NAMES.put(org.bukkit.entity.EntityType.STRIDER, "страйдера");
        MOB_NAMES.put(org.bukkit.entity.EntityType.WITHER_SKELETON, "иссушённого скелета");
        MOB_NAMES.put(org.bukkit.entity.EntityType.GUARDIAN, "стража");
        MOB_NAMES.put(org.bukkit.entity.EntityType.ELDER_GUARDIAN, "древнего стража");
        MOB_NAMES.put(org.bukkit.entity.EntityType.SHULKER, "шалкера");
        MOB_NAMES.put(org.bukkit.entity.EntityType.VEX, "векса");
        MOB_NAMES.put(org.bukkit.entity.EntityType.EVOKER, "иллюзионера");
        MOB_NAMES.put(org.bukkit.entity.EntityType.VINDICATOR, "иллюзера");
        MOB_NAMES.put(org.bukkit.entity.EntityType.PILLAGER, "разбойника");
        MOB_NAMES.put(org.bukkit.entity.EntityType.RAVAGER, "разорителя");
        MOB_NAMES.put(org.bukkit.entity.EntityType.WITCH, "ведьму");
        MOB_NAMES.put(org.bukkit.entity.EntityType.PHANTOM, "фантома");
        MOB_NAMES.put(org.bukkit.entity.EntityType.DROWNED, "утопленника");
        MOB_NAMES.put(org.bukkit.entity.EntityType.HUSK, "хаска");
        MOB_NAMES.put(org.bukkit.entity.EntityType.STRAY, "страя");
        MOB_NAMES.put(org.bukkit.entity.EntityType.BOGGED, "боггеда");
        MOB_NAMES.put(org.bukkit.entity.EntityType.BREEZE, "бриза");
        MOB_NAMES.put(org.bukkit.entity.EntityType.COW, "корову");
        MOB_NAMES.put(org.bukkit.entity.EntityType.MOOSHROOM, "грибную корову");
        MOB_NAMES.put(org.bukkit.entity.EntityType.PIG, "свинью");
        MOB_NAMES.put(org.bukkit.entity.EntityType.SHEEP, "овцу");
        MOB_NAMES.put(org.bukkit.entity.EntityType.CHICKEN, "курицу");
        MOB_NAMES.put(org.bukkit.entity.EntityType.HORSE, "лошадь");
        MOB_NAMES.put(org.bukkit.entity.EntityType.DONKEY, "осла");
        MOB_NAMES.put(org.bukkit.entity.EntityType.MULE, "мула");
        MOB_NAMES.put(org.bukkit.entity.EntityType.LLAMA, "ламу");
        MOB_NAMES.put(org.bukkit.entity.EntityType.CAT, "кота");
        MOB_NAMES.put(org.bukkit.entity.EntityType.WOLF, "волка");
        MOB_NAMES.put(org.bukkit.entity.EntityType.PARROT, "попугая");
        MOB_NAMES.put(org.bukkit.entity.EntityType.BAT, "летучую мышь");
        MOB_NAMES.put(org.bukkit.entity.EntityType.SQUID, "кальмара");
        MOB_NAMES.put(org.bukkit.entity.EntityType.GLOW_SQUID, "светящегося кальмара");
        MOB_NAMES.put(org.bukkit.entity.EntityType.DOLPHIN, "дельфина");
        MOB_NAMES.put(org.bukkit.entity.EntityType.TURTLE, "черепаху");
        MOB_NAMES.put(org.bukkit.entity.EntityType.PANDA, "панду");
        MOB_NAMES.put(org.bukkit.entity.EntityType.BEE, "пчелу");
        MOB_NAMES.put(org.bukkit.entity.EntityType.AXOLOTL, "аксолотля");
        MOB_NAMES.put(org.bukkit.entity.EntityType.GOAT, "козла");
        MOB_NAMES.put(org.bukkit.entity.EntityType.CAMEL, "верблюда");
        MOB_NAMES.put(org.bukkit.entity.EntityType.SNIFFER, "нюхача");
        MOB_NAMES.put(org.bukkit.entity.EntityType.ALLAY, "аллая");
        MOB_NAMES.put(org.bukkit.entity.EntityType.TADPOLE, "головастика");
        MOB_NAMES.put(org.bukkit.entity.EntityType.FROG, "лягушку");
        MOB_NAMES.put(org.bukkit.entity.EntityType.POLAR_BEAR, "белого медведя");
        MOB_NAMES.put(org.bukkit.entity.EntityType.SNOW_GOLEM, "снеговика");
        MOB_NAMES.put(org.bukkit.entity.EntityType.IRON_GOLEM, "железного голема");
        MOB_NAMES.put(org.bukkit.entity.EntityType.ENDER_DRAGON, "дракона Края");
        MOB_NAMES.put(org.bukkit.entity.EntityType.WITHER, "иссушителя");
        MOB_NAMES.put(org.bukkit.entity.EntityType.ARMADILLO, "броненосца");
        MOB_NAMES.put(org.bukkit.entity.EntityType.WANDERING_TRADER, "странствующего торговца");
    }

    private static String displayName(Kind kind, LivingEntity mob) {
        return switch (kind) {
            case RABBIT -> "кролика";
            case BLAZE -> "блейза";
            case WARDEN -> "вардена";
            case FOX -> "лису";
            case PIGLIN -> "пиглина";
            case VILLAGER -> "жителя";
            case GENERIC -> MOB_NAMES.getOrDefault(mob.getType(),
                    "моба (" + mob.getType().name().toLowerCase().replace('_', ' ') + ")");
        };
    }

    private void exit(Player admin) {
        State state = active.remove(admin.getUniqueId());
        if (state == null) return;
        if (state.followTask != null) state.followTask.cancel();

        for (int i = 0; i < 9; i++) {
            admin.getInventory().setItem(i, state.savedHotbar[i]);
        }
        admin.removePotionEffect(PotionEffectType.INVISIBILITY);
        admin.setAllowFlight(state.savedAllowFlight);
        admin.setFlying(state.savedFlying && state.savedAllowFlight);

        Entity e = Bukkit.getEntity(state.mobId);
        if (e instanceof LivingEntity mob && mob.isValid()) {
            mob.getPersistentDataContainer().remove(Keys.xyzPossessedBy);
            mob.setAI(true);
            mob.setInvulnerable(false);
            mob.setSilent(false);
            mob.setCollidable(true);
        }
        admin.sendMessage(Msg.color("&7Ты вышел из тела моба."));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (isPossessing(event.getPlayer())) exit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (isPossessing(player)) exit(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isAbilityItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!isPossessing(player)) return;
        if (isAbilityItem(event.getCurrentItem()) || isAbilityItem(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    // ---------- использование способностей ----------

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        State state = active.get(player.getUniqueId());
        if (state == null) return;
        ItemStack item = event.getItem();
        String ability = abilityId(item);
        if (ability == null) return;
        event.setCancelled(true);

        if (ability.equals("exit")) {
            exit(player);
            return;
        }
        switch (state.kind) {
            case RABBIT, GENERIC -> useRabbit(player, state, ability);
            case BLAZE -> useBlaze(player, state, ability);
            case WARDEN -> useWarden(player, state, ability);
            case FOX -> useFox(player, state, ability);
            case PIGLIN -> usePiglin(player, state, ability);
            case VILLAGER -> useVillager(player, state, ability);
        }
    }

    // ---------- Кролик ----------

    private void useRabbit(Player player, State state, String ability) {
        switch (ability) {
            case "bite" -> {
                if (!ScanUtil.tryUse(player.getUniqueId(), "xyz_rabbit_bite", 1000)) return;
                LivingEntity target = nearestTarget(player, state, 3.0);
                if (target != null) {
                    target.damage(6.0, player);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 1));
                }
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_RABBIT_ATTACK, 1f, 1.2f);
                player.swingMainHand();
            }
            case "ram" -> {
                if (!ScanUtil.tryUse(player.getUniqueId(), "xyz_rabbit_ram", 3000)) return;
                dashForward(player, 1.6, 0.25);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_RABBIT_JUMP, 1f, 0.7f);
                Bukkit.getScheduler().runTaskLater(plugin, () -> knockbackNearby(player, state, 2.5, 8.0), 3L);
            }
            case "claw" -> {
                if (!ScanUtil.tryUse(player.getUniqueId(), "xyz_rabbit_claw", 2000)) return;
                for (LivingEntity target : nearbyTargets(player, state, 4)) {
                    target.damage(5.0, player);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0));
                }
                player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, player.getLocation().add(0, 1, 0), 6,
                        1, 0.5, 1, 0);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1f);
            }
            case "fly" -> toggleFly(player);
            case "boom_small" -> boomSmall(player, state, "xyz_rabbit_boom_small", 10.0, false);
            case "boom_big" -> boomBig(player, state, "xyz_rabbit_boom_big", 15.0);
            default -> { }
        }
    }

    // ---------- Блейз ----------

    private void useBlaze(Player player, State state, String ability) {
        switch (ability) {
            case "fireball" -> {
                if (!ScanUtil.tryUse(player.getUniqueId(), "xyz_blaze_fireball", 800)) return;
                shootFireball(player, player.getEyeLocation().getDirection());
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1f);
            }
            case "volley" -> {
                if (!ScanUtil.tryUse(player.getUniqueId(), "xyz_blaze_volley", 3000)) return;
                Vector dir = player.getEyeLocation().getDirection();
                for (double offset : new double[]{-0.2, 0.0, 0.2}) {
                    Vector d = dir.clone().add(new Vector(offset, offset * 0.3, 0)).normalize();
                    shootFireball(player, d);
                }
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.8f);
            }
            case "flame_slash" -> {
                if (!ScanUtil.tryUse(player.getUniqueId(), "xyz_blaze_flame_slash", 2000)) return;
                for (LivingEntity target : nearbyTargets(player, state, 4)) {
                    target.damage(7.0, player);
                    target.setFireTicks(60);
                }
                player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0, 1, 0), 20,
                        1.2, 0.6, 1.2, 0.02);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_BURN, 1f, 1f);
            }
            case "fly" -> toggleFly(player);
            case "fire_pillar" -> {
                if (!ScanUtil.tryUse(player.getUniqueId(), "xyz_blaze_fire_pillar", 4000)) return;
                boomSmall(player, state, "xyz_blaze_fire_pillar_hit", 10.0, true);
            }
            case "meteor" -> {
                if (!ScanUtil.tryUse(player.getUniqueId(), "xyz_blaze_meteor", 8000)) return;
                boomBig(player, state, "xyz_blaze_meteor_hit", 15.0);
                Location point = impactPoint(player, 30);
                scorchArea(point);
            }
            default -> { }
        }
    }

    private void shootFireball(Player player, Vector direction) {
        SmallFireball fb = player.launchProjectile(SmallFireball.class);
        fb.setDirection(direction.normalize());
        fb.setVelocity(direction.clone().normalize().multiply(1.4));
        fb.setIsIncendiary(true);
    }

    /** Поджигает верхние блоки в радиусе 3 вокруг точки (без разрушения рельефа). */
    private void scorchArea(Location point) {
        Block center = point.getBlock();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz > 9) continue;
                Block ground = center.getRelative(dx, 0, dz);
                for (int dy = 2; dy >= -2; dy--) {
                    Block b = ground.getRelative(0, dy, 0);
                    if (!b.getType().isAir() && canBreak(b)) {
                        Block above = b.getRelative(0, 1, 0);
                        if (above.getType().isAir() && java.util.concurrent.ThreadLocalRandom.current()
                                .nextDouble() < 0.35) {
                            above.setType(Material.FIRE);
                        }
                        break;
                    }
                }
            }
        }
    }

    // ---------- Варден ----------

    private void useWarden(Player player, State state, String ability) {
        switch (ability) {
            case "claw" -> {
                if (!ScanUtil.tryUse(player.getUniqueId(), "xyz_warden_claw", 1200)) return;
                LivingEntity target = nearestTarget(player, state, 3.5);
                if (target != null) target.damage(12.0, player);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1f, 1f);
                player.swingMainHand();
            }
            case "sonic" -> {
                if (!ScanUtil.tryUse(player.getUniqueId(), "xyz_warden_sonic", 3000)) return;
                Vector dir = player.getLocation().getDirection().setY(0).normalize();
                Location origin = player.getEyeLocation();
                for (LivingEntity target : nearbyTargets(player, state, 10)) {
                    Vector toTarget = target.getLocation().toVector().subtract(origin.toVector());
                    if (toTarget.length() == 0) continue;
                    double angle = dir.angle(toTarget.normalize());
                    if (angle < Math.toRadians(20)) {
                        target.damage(9.0, player);
                        target.setVelocity(dir.clone().multiply(1.6).setY(0.3));
                    }
                }
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 2f, 1f);
                player.getWorld().spawnParticle(Particle.SONIC_BOOM, origin.add(dir.multiply(2)), 1);
            }
            case "roar" -> {
                if (!ScanUtil.tryUse(player.getUniqueId(), "xyz_warden_roar", 6000)) return;
                for (LivingEntity target : nearbyTargets(player, state, 6)) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
                }
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_ROAR, 2f, 0.8f);
            }
            case "burrow" -> {
                if (!ScanUtil.tryUse(player.getUniqueId(), "xyz_warden_burrow", 5000)) return;
                Vector dir = player.getLocation().getDirection().setY(0).normalize();
                Location dest = player.getLocation().add(dir.multiply(6));
                dest.setY(player.getWorld().getHighestBlockYAt(dest.getBlockX(), dest.getBlockZ()) + 1);
                player.getWorld().spawnParticle(Particle.SCULK_SOUL, player.getLocation(), 20, 0.5, 0.5, 0.5, 0.02);
                player.teleport(dest);
                player.getWorld().spawnParticle(Particle.SCULK_SOUL, dest, 20, 0.5, 0.5, 0.5, 0.02);
                player.getWorld().playSound(dest, Sound.ENTITY_WARDEN_DIG, 1.5f, 1f);
            }
            case "echo" -> {
                if (!ScanUtil.tryUse(player.getUniqueId(), "xyz_warden_echo", 6000)) return;
                for (LivingEntity target : nearbyTargets(player, state, 16)) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0));
                }
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_SCULK_SENSOR_CLICKING, 1.5f, 1f);
            }
            case "sonic_ultimate" -> {
                if (!ScanUtil.tryUse(player.getUniqueId(), "xyz_warden_sonic_ultimate", 10000)) return;
                boomBig(player, state, "xyz_warden_sonic_ultimate_hit", 18.0);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 3f, 0.6f);
            }
            default -> { }
        }
    }

    // ---------- Лиса ----------

    private void useFox(Player player, State state, String ability) {
        switch (ability) {
            case "bite" -> {
                if (!ScanUtil.tryUse(player.getUniqueId(), "xyz_fox_bite", 800)) return;
                LivingEntity target = nearestTarget(player, state, 2.5);
                if (target != null) target.damage(4.0, player);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FOX_BITE, 1f, 1.1f);
                player.swingMainHand();
            }
            case "pounce" -> {
                if (!ScanUtil.tryUse(player.getUniqueId(), "xyz_fox_pounce", 2500)) return;
                dashForward(player, 1.4, 0.35);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FOX_AMBIENT, 1f, 1.3f);
                Bukkit.getScheduler().runTaskLater(plugin, () -> knockbackNearby(player, state, 2.2, 6.0), 3L);
            }
            case "scavenge" -> {
                if (!ScanUtil.tryUse(player.getUniqueId(), "xyz_fox_scavenge", 2000)) return;
                int count = 0;
                for (Entity e : player.getNearbyEntities(5, 3, 5)) {
                    if (e instanceof Item itemEntity && itemEntity.isValid()) {
                        player.getInventory().addItem(itemEntity.getItemStack());
                        itemEntity.remove();
                        count++;
                    }
                }
                player.sendMessage(Msg.color("&eПодобрано предметов: &f" + count));
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FOX_EAT, 1f, 1f);
            }
            case "night_vision" -> {
                boolean has = player.hasPotionEffect(PotionEffectType.NIGHT_VISION);
                if (has) {
                    player.removePotionEffect(PotionEffectType.NIGHT_VISION);
                } else {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,
                            Integer.MAX_VALUE, 0, true, false, false));
                }
                player.sendMessage(Msg.color(has ? "&7Ночное зрение выключено." : "&bНочное зрение включено."));
            }
            case "ambush" -> boomSmall(player, state, "xyz_fox_ambush", 10.0, false);
            case "pack_cry" -> {
                if (!ScanUtil.tryUse(player.getUniqueId(), "xyz_fox_pack_cry", 7000)) return;
                for (LivingEntity target : nearbyTargets(player, state, 4.5)) {
                    target.damage(6.0, player);
                    target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                    Vector push = target.getLocation().toVector().subtract(player.getLocation().toVector())
                            .normalize().setY(0.3);
                    target.setVelocity(push.multiply(1.3));
                }
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FOX_SCREECH, 2f, 0.9f);
            }
            default -> { }
        }
    }

    // ---------- Пиглин ----------

    private void usePiglin(Player player, State state, String ability) {
        switch (ability) {
            case "strike" -> {
                if (!ScanUtil.tryUse(player.getUniqueId(), "xyz_piglin_strike", 1000)) return;
                LivingEntity target = nearestTarget(player, state, 3.0);
                if (target != null) target.damage(6.0, player);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PIGLIN_ANGRY, 1f, 1f);
                player.swingMainHand();
            }
            case "crossbow" -> {
                if (!ScanUtil.tryUse(player.getUniqueId(), "xyz_piglin_crossbow", 1500)) return;
                org.bukkit.entity.Arrow arrow = player.launchProjectile(org.bukkit.entity.Arrow.class);
                arrow.setVelocity(player.getEyeLocation().getDirection().multiply(3));
                arrow.setDamage(5.0);
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 1f, 1f);
            }
            case "gold_rush" -> {
                if (!ScanUtil.tryUse(player.getUniqueId(), "xyz_piglin_gold_rush", 3000)) return;
                dashForward(player, 1.5, 0.2);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PIGLIN_ADMIRING_ITEM, 1f, 1f);
                Bukkit.getScheduler().runTaskLater(plugin, () -> knockbackNearby(player, state, 2.5, 7.0), 3L);
            }
            case "gold_sense" -> {
                if (!ScanUtil.tryUse(player.getUniqueId(), "xyz_piglin_gold_sense", 5000)) return;
                int radius = 12;
                Location base = player.getLocation();
                int found = 0;
                for (int dx = -radius; dx <= radius && found < 40; dx++) {
                    for (int dy = -radius; dy <= radius && found < 40; dy++) {
                        for (int dz = -radius; dz <= radius && found < 40; dz++) {
                            Block b = base.clone().add(dx, dy, dz).getBlock();
                            if (isGoldBlock(b.getType())) {
                                player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                                        b.getLocation().add(0.5, 0.5, 0.5), 3, 0.2, 0.2, 0.2, 0);
                                found++;
                            }
                        }
                    }
                }
                player.sendMessage(Msg.color("&eНайдено золотых блоков рядом: &f" + found));
            }
            case "barter" -> boomSmall(player, state, "xyz_piglin_barter", 10.0, false);
            case "horde" -> boomBig(player, state, "xyz_piglin_horde", 15.0);
            default -> { }
        }
    }

    private static boolean isGoldBlock(Material m) {
        return m == Material.GOLD_ORE || m == Material.NETHER_GOLD_ORE || m == Material.RAW_GOLD_BLOCK
                || m == Material.GOLD_BLOCK || m == Material.GILDED_BLACKSTONE
                || m == Material.DEEPSLATE_GOLD_ORE;
    }

    // ---------- Житель ----------

    private void useVillager(Player player, State state, String ability) {
        switch (ability) {
            case "slap" -> {
                if (!ScanUtil.tryUse(player.getUniqueId(), "xyz_villager_slap", 700)) return;
                LivingEntity target = nearestTarget(player, state, 2.5);
                if (target != null) target.damage(3.0, player);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1.4f);
                player.swingMainHand();
            }
            case "haggle" -> {
                if (!ScanUtil.tryUse(player.getUniqueId(), "xyz_villager_haggle", 8000)) return;
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 160, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 160, 1));
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f);
            }
            case "scream" -> {
                if (!ScanUtil.tryUse(player.getUniqueId(), "xyz_villager_scream", 5000)) return;
                for (LivingEntity target : nearbyTargets(player, state, 5)) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                    Vector push = target.getLocation().toVector().subtract(player.getLocation().toVector())
                            .normalize().setY(0.3);
                    target.setVelocity(push.multiply(1.1));
                }
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_VILLAGER_HURT, 2f, 0.8f);
            }
            case "cure" -> {
                if (!ScanUtil.tryUse(player.getUniqueId(), "xyz_villager_cure", 10000)) return;
                for (PotionEffect eff : new ArrayList<>(player.getActivePotionEffects())) {
                    if (eff.getType() != PotionEffectType.INVISIBILITY) {
                        player.removePotionEffect(eff.getType());
                    }
                }
                player.setHealth(Math.min(player.getHealth() + 6, player.getAttribute(
                        org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()));
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_VILLAGER_CELEBRATE, 1f, 1f);
            }
            case "panic" -> boomSmall(player, state, "xyz_villager_panic", 8.0, false);
            case "golem" -> {
                if (!ScanUtil.tryUse(player.getUniqueId(), "xyz_villager_golem", 20000)) return;
                summonGolem(player);
            }
            default -> { }
        }
    }

    private void summonGolem(Player player) {
        IronGolem golem = player.getWorld().spawn(player.getLocation(), IronGolem.class, g -> {
            g.setPlayerCreated(false);
            g.getPersistentDataContainer().set(Keys.noLoot, PersistentDataType.BYTE, (byte) 1);
            g.customName(Msg.color("&fГолем-союзник &7· &f" + player.getName()));
            g.setCustomNameVisible(true);
        });
        player.sendMessage(Msg.color("&b✦ Призван голем-союзник на 30 секунд."));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_DEATH, 0.01f, 2f);
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int ticks = 0;
            @Override
            public void run() {
                ticks += 20;
                if (!golem.isValid()) return;
                LivingEntity target = nearestTarget(player, null, 10.0);
                if (target != null) golem.setTarget(target);
                if (ticks >= 30 * 20) {
                    golem.getWorld().spawnParticle(Particle.CLOUD, golem.getLocation().add(0, 1, 0), 15,
                            0.4, 0.6, 0.4, 0.02);
                    golem.remove();
                }
            }
        }, 20L, 20L);
    }

    // ---------- общие вспомогательные механики ----------

    private void toggleFly(Player player) {
        if (!ScanUtil.tryUse(player.getUniqueId(), "xyz_fly", 500)) return;
        boolean on = !player.isFlying();
        player.setAllowFlight(on);
        player.setFlying(on);
        player.sendMessage(Msg.color(on ? "&bПолёт включён." : "&7Полёт выключен."));
    }

    private void dashForward(Player player, double power, double upward) {
        Vector dir = player.getLocation().getDirection().setY(0).normalize();
        player.setVelocity(dir.multiply(power).setY(upward));
    }

    private void knockbackNearby(Player player, State state, double radius, double damage) {
        for (LivingEntity target : nearbyTargets(player, state, radius)) {
            target.damage(damage, player);
            Vector push = target.getLocation().toVector().subtract(player.getLocation().toVector())
                    .normalize().setY(0.3);
            target.setVelocity(push.multiply(1.2));
        }
    }

    private void boomSmall(Player player, State state, String cooldownKey, double damage, boolean fire) {
        if (!ScanUtil.tryUse(player.getUniqueId(), cooldownKey, 4000)) return;
        Location point = impactPoint(player, 20);
        for (LivingEntity target : nearbyTargets(point, player, state, 1.5)) {
            target.damage(damage, player);
            if (fire) target.setFireTicks(80);
        }
        point.getWorld().spawnParticle(fire ? Particle.FLAME : Particle.EXPLOSION, point, fire ? 25 : 1,
                0.6, 0.6, 0.6, fire ? 0.02 : 0);
        point.getWorld().playSound(point, Sound.ENTITY_GENERIC_EXPLODE, 1f, fire ? 1.0f : 1.4f);
    }

    private void boomBig(Player player, State state, String cooldownKey, double damage) {
        if (!ScanUtil.tryUse(player.getUniqueId(), cooldownKey, 8000)) return;
        Location point = impactPoint(player, 30);
        for (LivingEntity target : nearbyTargets(point, player, state, 4.5)) {
            target.damage(damage, player);
        }
        breakCrater(point);
        point.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, point, 1);
        point.getWorld().playSound(point, Sound.ENTITY_GENERIC_EXPLODE, 3f, 0.8f);
    }

    /** Ломает площадку 5×5 вокруг точки и поджигает часть расчищенной поверхности. */
    private void breakCrater(Location point) {
        Block center = point.getBlock();
        if (center.getType().isAir()) {
            for (int dy = 0; dy > -6; dy--) {
                Block below = point.getWorld().getBlockAt(center.getX(), center.getY() + dy, center.getZ());
                if (!below.getType().isAir()) { center = below; break; }
            }
        }
        List<Block> cleared = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Block b = center.getRelative(dx, 0, dz);
                if (!canBreak(b)) continue;
                b.setType(Material.AIR);
                cleared.add(b);
                Block above = b.getRelative(0, 1, 0);
                if (canBreak(above) && !above.getType().isAir()) {
                    above.setType(Material.AIR);
                }
            }
        }
        for (Block b : cleared) {
            if (Math.random() < 0.3) {
                Block above = b.getRelative(0, 1, 0);
                if (above.getType().isAir()) above.setType(Material.FIRE);
            }
        }
    }

    private static boolean canBreak(Block block) {
        return switch (block.getType()) {
            case BEDROCK, BARRIER, COMMAND_BLOCK, STRUCTURE_BLOCK, STRUCTURE_VOID, JIGSAW,
                    CHEST, TRAPPED_CHEST, ENDER_CHEST, SPAWNER, END_PORTAL_FRAME, END_PORTAL,
                    NETHER_PORTAL, WATER, LAVA, REINFORCED_DEEPSLATE -> false;
            default -> true;
        };
    }

    /** Точка попадания: блок/сущность по лучу взгляда, иначе фиксированная точка впереди. */
    private Location impactPoint(Player player, double maxDistance) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult hit = player.getWorld().rayTraceBlocks(eye, dir, maxDistance, FluidCollisionMode.NEVER, true);
        if (hit != null && hit.getHitPosition() != null) {
            return hit.getHitPosition().toLocation(player.getWorld());
        }
        return eye.clone().add(dir.clone().multiply(Math.min(maxDistance, 10)));
    }

    /** Ближайшая цель рядом с игроком (кроме него самого и его же подчинённого моба). */
    private LivingEntity nearestTarget(Player player, State state, double radius) {
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : player.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof LivingEntity target)) continue;
            if (state != null && target.getUniqueId().equals(state.mobId)) continue;
            double dist = target.getLocation().distanceSquared(player.getLocation());
            if (dist < bestDist) {
                bestDist = dist;
                best = target;
            }
        }
        return best;
    }

    private List<LivingEntity> nearbyTargets(Player player, State state, double radius) {
        return nearbyTargets(player.getLocation(), player, state, radius);
    }

    private List<LivingEntity> nearbyTargets(Location center, Player player, State state, double radius) {
        List<LivingEntity> result = new ArrayList<>();
        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (e == player) continue;
            if (!(e instanceof LivingEntity target)) continue;
            if (state != null && target.getUniqueId().equals(state.mobId)) continue;
            result.add(target);
        }
        return result;
    }

    // ---------- хотбар-предметы способностей ----------

    private ItemStack abilityItem(Material material, String id, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Msg.color(name));
            List<Component> lines = new ArrayList<>();
            for (String l : lore) lines.add(Msg.color(l));
            meta.lore(lines);
            meta.getPersistentDataContainer().set(Keys.xyzAbility, PersistentDataType.STRING, id);
        });
        return item;
    }

    private static boolean isAbilityItem(ItemStack item) {
        return abilityId(item) != null;
    }

    private static String abilityId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(Keys.xyzAbility, PersistentDataType.STRING);
    }
}
