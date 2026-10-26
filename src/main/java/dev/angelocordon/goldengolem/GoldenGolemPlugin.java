package dev.angelocordon.goldengolem;

import dev.angelocordon.goldengolem.build.GolemBuildListener;
import dev.angelocordon.goldengolem.build.GolemLook;
import dev.angelocordon.goldengolem.chest.GoldenChestListener;
import dev.angelocordon.goldengolem.chest.GoldenChests;
import dev.angelocordon.goldengolem.golem.Feedback;
import dev.angelocordon.goldengolem.golem.GolemController;
import dev.angelocordon.goldengolem.golem.GolemRegistry;
import dev.angelocordon.goldengolem.golem.GolemState;
import dev.angelocordon.goldengolem.golem.movement.Movers;
import dev.angelocordon.goldengolem.golem.movement.PathMover;
import dev.angelocordon.goldengolem.golem.state.BlockedHandler;
import dev.angelocordon.goldengolem.golem.state.DepositingHandler;
import dev.angelocordon.goldengolem.golem.state.FollowingHandler;
import dev.angelocordon.goldengolem.golem.state.IdleHandler;
import dev.angelocordon.goldengolem.golem.state.ReturningHandler;
import dev.angelocordon.goldengolem.golem.state.WorkingHandler;
import dev.angelocordon.goldengolem.lifecycle.GolemLifecycle;
import dev.angelocordon.goldengolem.lifecycle.GolemLifecycleListener;
import dev.angelocordon.goldengolem.link.ChestLinkListener;
import dev.angelocordon.goldengolem.link.ChestLinker;
import dev.angelocordon.goldengolem.menu.GolemMenuListener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Wires everything together. Every feature is constructed and registered here, so feature work
 * happens in the feature's own files and this class rarely changes.
 */
public final class GoldenGolemPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        if (true) {
            throw new IllegalStateException("LAB-26 deliberate smoke-test failure");
        }
        saveDefaultConfig();
        GolemConfig config = GolemConfig.from(getConfig());

        GolemRegistry registry = new GolemRegistry(getLogger());
        Movers movers = new Movers(PathMover::new);
        GolemController controller = new GolemController(getLogger(), registry, movers);
        GoldenChests chests = new GoldenChests(this);
        ChestLinker linker = new ChestLinker(registry, chests);
        Services services = new Services(this, config, registry, controller, movers, chests, linker,
                new GolemLifecycle(), new Feedback());

        controller.register(GolemState.IDLE, new IdleHandler(services));
        controller.register(GolemState.FOLLOWING, new FollowingHandler(services));
        controller.register(GolemState.WORKING, new WorkingHandler(services));
        controller.register(GolemState.RETURNING, new ReturningHandler(services));
        controller.register(GolemState.DEPOSITING, new DepositingHandler(services));
        controller.register(GolemState.BLOCKED, new BlockedHandler(services));
        controller.checkComplete();

        chests.registerRecipe();

        PluginManager plugins = getServer().getPluginManager();
        plugins.registerEvents(registry, this);
        plugins.registerEvents(new GolemBuildListener(services), this);
        plugins.registerEvents(new GoldenChestListener(services), this);
        plugins.registerEvents(new ChestLinkListener(services), this);
        plugins.registerEvents(new GolemMenuListener(services), this);
        plugins.registerEvents(new GolemLifecycleListener(services), this);
        new GolemLook(services).start();

        registry.scanLoadedWorlds();
        getServer().getScheduler().runTaskTimer(this, controller, 1L, 1L);
    }
}
