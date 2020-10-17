package com.massivecraft.factions.addon;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.util.FileUtil;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public abstract class FactionsAddon {

    private final static Set<FactionsAddon> ADDONS = new HashSet<>();

    private final Object instance;
    private final String name;
    private YamlConfiguration config;

    public FactionsAddon(final Object instance, final String name) {
        this.instance = instance;
        this.name = name;

        try {
            this.onEnable();
        } catch (final Throwable ex) {
            FactionsPlugin.getInstance().getLogger().log(Level.SEVERE, "Error occurred while enabling " + name + " addon.", ex);
        }

        ADDONS.add(this);
    }

    public void onEnable() {}

    public void onDisable() {}

    public void disableAddon() {
        this.onDisable();
        this.config = null;
        ADDONS.remove(this);
    }

    public void saveDefaultConfig() {
        final File dir = new File(FactionsPlugin.getInstance().getDataFolder().getPath() + "/addons/" + name);
        if (!dir.exists()) {
            dir.mkdir();
        }

        FileUtil.saveResource(this.getClass().getClassLoader(), dir.getPath(), "config.yml", false);

        this.config = new YamlConfiguration();
        try {
            this.config.load(dir.getPath() + "/config.yml");
        } catch (final IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
    }

    public Object getInstance() {
        return instance;
    }

    public YamlConfiguration getConfig() {
        return config;
    }

    public static Set<FactionsAddon> getAddons() {
        return ADDONS;
    }

}
