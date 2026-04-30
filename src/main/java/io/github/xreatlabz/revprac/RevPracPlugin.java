package io.github.xreatlabz.revprac;

import io.github.xreatlabz.revprac.application.result.Ok;
import io.github.xreatlabz.revprac.application.result.Result;
import io.github.xreatlabz.revprac.bootstrap.BootstrapRuntime;
import io.github.xreatlabz.revprac.bootstrap.RevPracBootstrap;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;

public class RevPracPlugin extends JavaPlugin {

    private final RevPracBootstrap bootstrap = new RevPracBootstrap();
    private BootstrapRuntime runtime;

    @Override
    public void onEnable() {
        Result<BootstrapRuntime> result = bootstrap.enable(this);
        if (result instanceof Ok<BootstrapRuntime> ok) {
            runtime = ok.value();
            getLogger().info("RevPrac enabled for Paper 1.21.11.");
        }
    }

    @Override
    public void onDisable() {
        if (runtime != null) {
            try {
                runtime.shutdown();
            } catch (RuntimeException exception) {
                getLogger().log(Level.SEVERE, "RevPrac player-session shutdown failed.", exception);
            } finally {
                runtime = null;
            }
        }
        getLogger().info("RevPrac disabled.");
    }
}
