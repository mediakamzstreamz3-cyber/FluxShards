package net.fluxmc.fluxshards;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.*;

public class ShardsCommand implements TabExecutor {

    private final FluxShards plugin;

    public ShardsCommand(FluxShards plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length == 0 ? "bal" : args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "bal", "balance" -> balance(sender, args);
            case "pay" -> pay(sender, args);
            case "give", "take", "set" -> admin(sender, sub, args);
            case "top" -> top(sender);
            case "reload" -> {
                if (!sender.hasPermission("shards.admin")) {
                    sender.sendMessage(plugin.msg("no-permission"));
                    return true;
                }
                plugin.reloadAll();
                sender.sendMessage(plugin.msg("reloaded"));
            }
            default -> sender.sendMessage(plugin.msg("usage",
                    "usage", "/shards [bal|pay|top]" + (sender.hasPermission("shards.admin") ? " | give|take|set|reload" : "")));
        }
        return true;
    }

    // ------------------------------------------------------------ subcommands

    private void balance(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shards.bal")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return;
        }
        if (args.length >= 2) {
            OfflinePlayer t = target(args[1]);
            if (t == null) {
                sender.sendMessage(plugin.msg("unknown-player"));
                return;
            }
            sender.sendMessage(plugin.msg("balance-other", "player", nameOf(t),
                    "amount", Fmt.commas(plugin.storage().get(t.getUniqueId()))));
            return;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(plugin.msg("players-only"));
            return;
        }
        sender.sendMessage(plugin.msg("balance", "amount", Fmt.commas(plugin.storage().get(p.getUniqueId()))));
    }

    private void pay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(plugin.msg("players-only"));
            return;
        }
        if (!p.hasPermission("shards.pay")) {
            p.sendMessage(plugin.msg("no-permission"));
            return;
        }
        if (args.length < 3) {
            p.sendMessage(plugin.msg("usage", "usage", "/shards pay <player> <amount>"));
            return;
        }
        OfflinePlayer t = target(args[1]);
        if (t == null) {
            p.sendMessage(plugin.msg("unknown-player"));
            return;
        }
        if (t.getUniqueId().equals(p.getUniqueId())) {
            p.sendMessage(plugin.msg("cannot-pay-self"));
            return;
        }
        long amount = Fmt.parse(args[2]);
        if (amount <= 0) {
            p.sendMessage(plugin.msg("invalid-amount"));
            return;
        }
        if (!plugin.storage().transfer(p.getUniqueId(), t.getUniqueId(), amount)) {
            p.sendMessage(plugin.msg("not-enough"));
            return;
        }
        p.sendMessage(plugin.msg("paid", "player", nameOf(t), "amount", Fmt.commas(amount)));
        Player online = Bukkit.getPlayer(t.getUniqueId());
        if (online != null) {
            online.sendMessage(plugin.msg("received", "player", p.getName(), "amount", Fmt.commas(amount)));
        }
    }

    private void admin(CommandSender sender, String sub, String[] args) {
        if (!sender.hasPermission("shards.admin")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.msg("usage", "usage", "/shards " + sub + " <player> <amount>"));
            return;
        }
        OfflinePlayer t = target(args[1]);
        if (t == null) {
            sender.sendMessage(plugin.msg("unknown-player"));
            return;
        }
        long amount = Fmt.parse(args[2]);
        if (amount < 0 || (!sub.equals("set") && amount == 0)) {
            sender.sendMessage(plugin.msg("invalid-amount"));
            return;
        }

        UUID id = t.getUniqueId();
        String name = nameOf(t);
        String shown = Fmt.commas(amount);
        switch (sub) {
            case "give" -> {
                plugin.storage().add(id, amount);
                sender.sendMessage(plugin.msg("given", "player", name, "amount", shown));
            }
            case "take" -> {
                if (!plugin.storage().take(id, amount)) {
                    sender.sendMessage(plugin.msg("not-enough-target", "player", name));
                    return;
                }
                sender.sendMessage(plugin.msg("taken", "player", name, "amount", shown));
            }
            default -> {
                plugin.storage().set(id, amount);
                sender.sendMessage(plugin.msg("set", "player", name, "amount", shown));
            }
        }
    }

    private void top(CommandSender sender) {
        sender.sendMessage(FluxShards.color(plugin.getConfig().getString("messages.top-header", "&d&lTop Shards")));
        List<Map.Entry<UUID, Long>> top = plugin.storage().top(10);
        int rank = 1;
        for (Map.Entry<UUID, Long> e : top) {
            String name = Bukkit.getOfflinePlayer(e.getKey()).getName();
            sender.sendMessage(FluxShards.color(plugin.getConfig().getString("messages.top-line",
                            "&e#{rank} &f{player} &7- &d{amount}")
                    .replace("{rank}", String.valueOf(rank))
                    .replace("{player}", name == null ? "Unknown" : name)
                    .replace("{amount}", Fmt.commas(e.getValue()))));
            rank++;
        }
    }

    // ------------------------------------------------------------ helpers

    /** Online players first, then players the server has seen before. Never blocks on a web lookup. */
    private OfflinePlayer target(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        return Bukkit.getServer().getOfflinePlayerIfCached(name);
    }

    private String nameOf(OfflinePlayer p) {
        return p.getName() == null ? "Unknown" : p.getName();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("bal", "pay", "top"));
            if (sender.hasPermission("shards.admin")) subs.addAll(List.of("give", "take", "set", "reload"));
            String start = args[0].toLowerCase(Locale.ROOT);
            for (String s : subs) if (s.startsWith(start)) out.add(s);
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("top") && !args[0].equalsIgnoreCase("reload")) {
            String start = args[1].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(start)) out.add(p.getName());
            }
        }
        return out;
    }
}
