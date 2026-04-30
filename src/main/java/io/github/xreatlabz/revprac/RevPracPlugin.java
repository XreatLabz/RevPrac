package io.github.xreatlabz.revprac;

import org.bukkit.plugin.java.JavaPlugin;

public class RevPracPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("RevPrac enabled for Paper 1.21.11.");
    }

    @Override
    public void onDisable() {
        getLogger().info("RevPrac disabled.");
    }
}
