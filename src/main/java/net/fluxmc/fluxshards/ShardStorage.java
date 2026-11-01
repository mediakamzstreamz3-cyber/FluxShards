package net.fluxmc.fluxshards;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Holds every player's shard balance in memory and saves it to data.yml in the background. */
public class ShardStorage {

    private final FluxShards plugin;
    private final File file;
    private final Map<UUID, Long> balances = new ConcurrentHashMap<>();
    private final AtomicBoolean saveQueued = new AtomicBoolean(false);
    private final ScheduledExecutorService saver = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "FluxShards-Saver");
        t.setDaemon(true);
        return t;
    });

    private volatile List<Map.Entry<UUID, Long>> topCache = new ArrayList<>();
    private volatile long topCacheTime = 0;

    public ShardStorage(FluxShards plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        balances.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = yml.getConfigurationSection("balances");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                balances.put(UUID.fromString(key), sec.getLong(key));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Skipping bad entry in data.yml: " + key);
            }
        }
    }

    private long start() {
        return plugin.getConfig().getLong("starting-balance", 0);
    }

    public long get(UUID id) {
        return balances.getOrDefault(id, start());
    }

    public synchronized void set(UUID id, long amount) {
        balances.put(id, Math.max(0, amount));
        queueSave();
    }

    public synchronized void add(UUID id, long amount) {
        long now = get(id);
        long next = now + amount;
        if (next < now) next = Long.MAX_VALUE; // overflow guard
        balances.put(id, next);
        queueSave();
    }

    /** Removes shards only if the player has enough. */
    public synchronized boolean take(UUID id, long amount) {
        long now = get(id);
        if (now < amount) return false;
        balances.put(id, now - amount);
        queueSave();
        return true;
    }

    /** Moves shards from one player to another in one step. */
    public synchronized boolean transfer(UUID from, UUID to, long amount) {
        if (!take(from, amount)) return false;
        add(to, amount);
        return true;
    }

    /** Top balances, cached for a few seconds so placeholders stay cheap. */
    public List<Map.Entry<UUID, Long>> top(int n) {
        long now = System.currentTimeMillis();
        if (now - topCacheTime > 5000) {
            List<Map.Entry<UUID, Long>> list = new ArrayList<>(balances.entrySet());
            list.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
            topCache = list;
            topCacheTime = now;
        }
        List<Map.Entry<UUID, Long>> c = topCache;
        return c.subList(0, Math.min(n, c.size()));
    }

    private void queueSave() {
        if (saveQueued.compareAndSet(false, true)) {
            saver.schedule(() -> {
                saveQueued.set(false);
                saveNow();
            }, 5, TimeUnit.SECONDS);
        }
    }

    public synchronized void saveNow() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, Long> e : balances.entrySet()) {
            yml.set("balances." + e.getKey(), e.getValue());
        }
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save data.yml: " + ex.getMessage());
        }
    }

    public void shutdown() {
        saver.shutdown();
        saveNow();
    }
}
