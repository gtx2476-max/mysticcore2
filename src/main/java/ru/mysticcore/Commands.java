package ru.mysticcore;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Все команды плагина (кроме /spheres). */
public final class Commands implements CommandExecutor, TabCompleter {

    private final MysticCore plugin;

    public Commands(MysticCore plugin) {
        this.plugin = plugin;
    }

    private void out(CommandSender s, String legacy) { s.sendMessage(U.c(legacy)); }

    private boolean admin(CommandSender s) {
        if (s.hasPermission("mystic.admin")) return true;
        out(s, "&cНет прав.");
        return false;
    }

    private boolean player(CommandSender s) {
        if (s instanceof Player) return true;
        out(s, "&cТолько для игроков.");
        return false;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "menu" -> { if (player(s)) plugin.menus().openHub((Player) s); }
            case "donate" -> { if (player(s)) plugin.menus().openDonateMain((Player) s); }
            case "shop" -> { if (player(s)) plugin.menus().openShopMain((Player) s); }
            case "lobby" -> { if (player(s)) plugin.lobby().teleportLobby((Player) s); }
            case "mine" -> { if (player(s)) plugin.lobby().teleportMine((Player) s); }
            case "rtp" -> { if (player(s)) plugin.getRtp().teleport((Player) s, false); }
            case "quests" -> { if (player(s)) plugin.quests().open((Player) s); }
            case "sell" -> { if (player(s)) { if (a.length > 0 && a[0].equalsIgnoreCase("hand")) plugin.exchange().sellHand((Player) s); else plugin.exchange().openBuyer((Player) s); } }
            case "bourse" -> { if (player(s)) plugin.exchange().openBoard((Player) s, 0); }
            case "season" -> season(s, a);
            case "ah" -> ah(s, a);
            case "coins" -> coins(s, a);
            case "mystic" -> mystic(s, a);
            case "airdrops" -> airdrops(s, a);
            case "blackmarket" -> blackMarket(s, a);
            default -> { return false; }
        }
        return true;
    }

    // ---------- /season ----------
    private void season(CommandSender s, String[] a) {
        if (a.length >= 3 && a[0].equalsIgnoreCase("xp")) {
            if (!admin(s)) return;
            Player t = Bukkit.getPlayerExact(a[1]);
            if (t == null) { out(s, "&cИгрок не найден."); return; }
            try { plugin.season().addXp(t, Long.parseLong(a[2])); out(s, "&aXP добавлен."); }
            catch (NumberFormatException ex) { out(s, "&cНужно число."); }
            return;
        }
        if (player(s)) plugin.season().open((Player) s, 0);
    }

    // ---------- /ah ----------
    private void ah(CommandSender s, String[] a) {
        if (!player(s)) return;
        Player p = (Player) s;
        if (a.length >= 1 && a[0].equalsIgnoreCase("sell")) {
            if (a.length < 2) { out(s, "&cИспользование: /ah sell <цена>"); return; }
            try {
                plugin.auction().sell(p, Long.parseLong(a[1]));
            } catch (NumberFormatException ex) {
                out(s, "&cЦена должна быть числом.");
            }
            return;
        }
        plugin.auction().open(p);
    }

    // ---------- /coins ----------
    private void coins(CommandSender s, String[] a) {
        if (a.length == 0) {
            if (!player(s)) return;
            Player p = (Player) s;
            out(s, "&7Токены: &d" + U.money(plugin.getEconomy().tokens(p.getUniqueId()))
                    + " &8| &7Монетки: &e" + U.money(plugin.getEconomy().coins(p.getUniqueId())));
            return;
        }
        if (!admin(s)) return;
        if (a.length < 3) { out(s, "&cИспользование: /coins <add|set> <игрок> <кол-во>"); return; }
        Player t = Bukkit.getPlayerExact(a[1]);
        if (t == null) { out(s, "&cИгрок не найден."); return; }
        long n;
        try { n = Long.parseLong(a[2]); } catch (NumberFormatException ex) { out(s, "&cНужно число."); return; }
        if (a[0].equalsIgnoreCase("add")) plugin.getEconomy().addCoins(t.getUniqueId(), n);
        else plugin.getEconomy().setCoins(t.getUniqueId(), n);
        out(s, "&aГотово. Монетки " + t.getName() + ": &e" + U.money(plugin.getEconomy().coins(t.getUniqueId())));
    }

    // ---------- /mystic ----------
    private void mystic(CommandSender s, String[] a) {
        if (a.length == 0) {
            if (player(s)) plugin.menus().openChestShop((Player) s);
            return;
        }
        if (!admin(s)) return;
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "spawn" -> {
                Tier t = a.length > 1 ? plugin.drops().tier(a[1].toLowerCase(Locale.ROOT)) : plugin.drops().rollTier();
                if (t == null) { out(s, "&cУровни: poor, solid, rich, elite, crusher"); return; }
                boolean here = a.length > 2 && a[2].equalsIgnoreCase("here") && s instanceof Player;
                Location loc = here ? ((Player) s).getLocation() : plugin.drops().pickLocation();
                if (plugin.drops().spawn(t, loc) == null) out(s, "&cНе удалось заспавнить.");
            }
            case "wave" -> plugin.drops().spawnWave();
            case "boss" -> {
                if (!plugin.boss().spawn()) out(s, "&cБосс уже есть или нет места.");
            }
            case "bossoff" -> plugin.boss().despawn();
            case "tokens" -> {
                if (a.length < 4) { out(s, "&cИспользование: /mystic tokens <add|set> <игрок> <кол-во>"); return; }
                Player t = Bukkit.getPlayerExact(a[2]);
                if (t == null) { out(s, "&cИгрок не найден."); return; }
                long n;
                try { n = Long.parseLong(a[3]); } catch (NumberFormatException ex) { out(s, "&cНужно число."); return; }
                if (a[1].equalsIgnoreCase("add")) plugin.getEconomy().addTokens(t.getUniqueId(), n);
                else plugin.getEconomy().setTokens(t.getUniqueId(), n);
                out(s, "&aГотово. Токены " + t.getName() + ": &d" + U.money(plugin.getEconomy().tokens(t.getUniqueId())));
            }
            case "give" -> {
                // /mystic give <игрок> <id> [кол-во] — выдать талисман/кейс/расходник/уникальный предмет
                if (a.length < 3) { out(s, "&cИспользование: /mystic give <игрок> <id> [кол-во]"); return; }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null || plugin.getCatalog().get(a[2]) == null) { out(s, "&cИгрок или id не найден."); return; }
                int n = 1;
                if (a.length > 3) { try { n = Math.max(1, Math.min(64, Integer.parseInt(a[3]))); } catch (NumberFormatException ignored) { } }
                U.give(t, plugin.getCatalog().create(a[2], n));
                out(s, "&aВыдано.");
            }
            case "reload" -> {
                plugin.reloadConfig();
                plugin.drops().loadTiers();
                out(s, "&aКонфиг перезагружен.");
            }
            default -> out(s, "&cspawn | wave | boss | bossoff | tokens | give | reload");
        }
    }

    // ---------- /airdrops ----------
    private void airdrops(CommandSender s, String[] a) {
        if (a.length > 0 && a[0].equalsIgnoreCase("remove")) {
            if (admin(s)) out(s, "&7Удалено сундуков: &f" + plugin.drops().removeAll());
            return;
        }
        if (a.length > 0 && (a[0].equalsIgnoreCase("spawn") || a[0].equalsIgnoreCase("wave"))) {
            mystic(s, a);
            return;
        }
        out(s, "&6&l[Сундуки] &eСледующая волна через &f" + U.time(plugin.drops().getSecondsUntilWave()));
        if (plugin.drops().getDrops().isEmpty()) {
            out(s, "&7Активных сундуков сейчас нет.");
            return;
        }
        out(s, "&eАктивные сундуки:");
        for (Drop d : plugin.drops().getDrops()) {
            Location l = d.getLocation();
            String state = d.isReady() ? "&aоткрыт" : "&eоткроется через " + U.time(d.getSecondsUntilOpen());
            out(s, "&7#" + d.getId() + " " + d.getTier().name() + " &7— &fX: " + l.getBlockX() + " Y: " + l.getBlockY()
                    + " Z: " + l.getBlockZ() + " &7— " + state);
        }
    }

    // ---------- /bm ----------
    private void blackMarket(CommandSender s, String[] a) {
        if (a.length > 0 && a[0].equalsIgnoreCase("open")) {
            if (admin(s) && !plugin.blackMarket().open(null)) out(s, "&cРынок уже открыт или нет места.");
            return;
        }
        if (a.length > 0 && a[0].equalsIgnoreCase("close")) {
            if (admin(s)) plugin.blackMarket().close(true);
            return;
        }
        plugin.blackMarket().info(s);
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] a) {
        List<String> res = new ArrayList<>();
        if (!s.hasPermission("mystic.admin")) return res;
        String n = cmd.getName().toLowerCase(Locale.ROOT);
        if (n.equals("mystic")) {
            if (a.length == 1) res.addAll(List.of("spawn", "wave", "boss", "bossoff", "tokens", "give", "reload"));
            else if (a.length == 2 && a[0].equalsIgnoreCase("spawn")) res.addAll(List.of("poor", "solid", "rich", "elite", "crusher"));
            else if (a.length == 3 && a[0].equalsIgnoreCase("give")) plugin.getCatalog().all().forEach(en -> res.add(en.id()));
        } else if (n.equals("blackmarket") && a.length == 1) {
            res.addAll(List.of("open", "close"));
        } else if (n.equals("airdrops") && a.length == 1) {
            res.addAll(List.of("spawn", "wave", "remove"));
        }
        return res;
    }
}
