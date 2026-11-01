package net.fluxmc.fluxshards;

import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class FluxShards extends JavaPlugin {

    private ShardStorage storage;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        storage = new ShardStorage(this);
        storage.load();

        ShardsCommand cmd = new ShardsCommand(this);
        PluginCommand pc = getCommand("shards");
        if (pc != null) {
            pc.setExecutor(cmd);
            pc.setTabCompleter(cmd);
        }

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            registerPlaceholders();
        } else {
            getLogger().warning("PlaceholderAPI not found - placeholders like %shards_balance% won't work.");
        }
        getLogger().info("FluxShards enabled.");
    }

    private void registerPlaceholders() {
        new ShardsExpansion(this).register();
        getLogger().info("Registered PlaceholderAPI placeholders (%shards_balance%, %shards_short%, ...).");
    }

    @Override
    public void onDisable() {
        if (storage != null) storage.shutdown();
    }

    public ShardStorage storage() {
        return storage;
    }

    public void reloadAll() {
        reloadConfig();
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    /** Message from config.yml with {key} placeholders replaced. Pairs: key, value, key, value... */
    public String msg(String key, String... pairs) {
        String m = getConfig().getString("messages." + key, key);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            m = m.replace("{" + pairs[i] + "}", pairs[i + 1]);
        }
        return color(getConfig().getString("messages.prefix", "") + m);
    }
}
