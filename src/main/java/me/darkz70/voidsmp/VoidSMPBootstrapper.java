package me.darkz70.voidsmp;

import io.papermc.paper.datapack.DatapackRegistrar;
import io.papermc.paper.datapack.DiscoveredDatapack;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import java.nio.file.Path;

/**
 * Bootstrap-класс: регистрирует датапак с лут-таблицой VoidSMP.
 * <p>
 * DATAPACK_DISCOVERY можно подписать только здесь — до того, как сервер
 * впервые ищет датапаки при загрузке мира.
 */
public final class VoidSMPBootstrapper implements PluginBootstrap {

    /** Имя уже зарегистрированного датапака (чтобы не дублировать при /reload). */
    private static volatile String discoveredPackName;

    @Override
    public void bootstrap(BootstrapContext context) {
        Path dataDir = context.getDataDirectory();

        context.getLifecycleManager().registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY, event -> {
            try {
                LootConfig.ensureDefaultConfig(dataDir);

                LootConfig config;
                try {
                    config = LootConfig.load(dataDir);
                } catch (Exception e) {
                    context.getLogger().error("config.yml некорректен (" + e.getMessage() + "), использую встроенный");
                    config = LootConfig.loadDefault();
                }

                LootPackGenerator.writePack(dataDir, config);

                DatapackRegistrar registrar = event.registrar();
                String previous = discoveredPackName;
                if (previous != null && registrar.hasPackDiscovered(previous)) {
                    registrar.removeDiscoveredPack(previous);
                }

                DiscoveredDatapack pack = registrar.discoverPack(dataDir.resolve("pack"), "loot", configurer -> {
                    configurer.autoEnableOnServerStart(true);
                    configurer.title(Component.text("VoidSMP Loot"));
                });

                if (pack != null) {
                    discoveredPackName = pack.getName();
                    context.getLogger().info("Датапак VoidSMP зарегистрирован: " + pack.getName());
                } else {
                    context.getLogger().error("discoverPack вернул null — датапак VoidSMP не зарегистрирован");
                }
            } catch (Exception e) {
                context.getLogger().error("Ошибка при регистрации датапака VoidSMP", e);
            }
        });
    }
}
