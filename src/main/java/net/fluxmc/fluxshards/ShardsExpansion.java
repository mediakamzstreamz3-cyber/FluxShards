package net.fluxmc.fluxshards;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * %shards_balance%        raw number (use this one for plugins)
 * %shards_formatted%      1,234,567
 * %shards_short%          1.2M
 * %shards_top_name_1%     name of #1 (works for 1-100)
 * %shards_top_value_1%    raw balance of #1
 * %shards_top_short_1%    short balance of #1
 */
public class ShardsExpansion extends PlaceholderExpansion {

    private final FluxShards plugin;

    public ShardsExpansion(FluxShards plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "shards";
    }

    @Override
    public String getAuthor() {
        return "FluxMC";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        String p = params.toLowerCase();

        if (p.startsWith("top_")) {
            String[] parts = p.split("_");
            if (parts.length != 3) return null;
            int rank;
            try {
                rank = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                return null;
            }
            if (rank < 1 || rank > 100) return null;
            List<Map.Entry<UUID, Long>> top = plugin.storage().top(rank);
            if (top.size() < rank) return parts[1].equals("name") ? "-" : "0";
            Map.Entry<UUID, Long> e = top.get(rank - 1);
            switch (parts[1]) {
                case "name": {
                    String n = Bukkit.getOfflinePlayer(e.getKey()).getName();
                    return n == null ? "Unknown" : n;
                }
                case "value":
                    return String.valueOf(e.getValue());
                case "short":
                    return Fmt.shorten(e.getValue());
                default:
                    return null;
            }
        }

        if (player == null) return "0";
        long bal = plugin.storage().get(player.getUniqueId());
        switch (p) {
            case "balance":
                return String.valueOf(bal);
            case "formatted":
                return Fmt.commas(bal);
            case "short":
                return Fmt.shorten(bal);
            default:
                return null;
        }
    }
}
