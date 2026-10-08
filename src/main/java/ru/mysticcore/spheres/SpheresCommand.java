package ru.mysticcore.spheres;

import ru.mysticcore.MysticCore;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /spheres                              - меню со сферами (клик = рецепт)
 * /spheres give <игрок> <id|all> [кол]  - (admin) выдать сферу
 * /spheres reload                       - (admin) перечитать config.yml
 */
public final class SpheresCommand implements CommandExecutor, TabCompleter {

    private final MysticCore plugin;
    private final SphereItems items;
    private final SphereMenu menu;

    public SpheresCommand(MysticCore plugin, SphereItems items, SphereMenu menu) {
        this.plugin = plugin;
        this.items = items;
        this.menu = menu;
    }

    private String msg(String key) {
        return plugin.getConfig().getString("messages." + key, "");
    }

    private void out(CommandSender s, String legacy) {
        s.sendMessage(SphereItems.c(legacy));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player p) menu.openMain(p);
            else out(sender, "&cТолько для игроков.");
            return true;
        }
        if (!sender.hasPermission("spheres.admin")) {
            out(sender, msg("no-permission"));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadConfig();
                out(sender, msg("reloaded"));
            }
            case "give" -> {
                if (args.length < 3) {
                    out(sender, "&cИспользование: /spheres give <игрок> <id|all> [кол-во]");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { out(sender, "&cИгрок не найден."); return true; }
                int amount = 1;
                if (args.length > 3) {
                    try { amount = Math.max(1, Math.min(64, Integer.parseInt(args[3]))); }
                    catch (NumberFormatException ignored) { }
                }
                List<SphereType> list = new ArrayList<>();
                if (args[2].equalsIgnoreCase("all")) list.addAll(List.of(SphereType.values()));
                else {
                    SphereType t = SphereType.byId(args[2]);
                    if (t == null) { out(sender, "&cНеизвестная сфера."); return true; }
                    list.add(t);
                }
                for (SphereType t : list) {
                    ItemStack it = items.create(t, amount, null);
                    Map<Integer, ItemStack> left = target.getInventory().addItem(it);
                    for (ItemStack rest : left.values()) target.getWorld().dropItemNaturally(target.getLocation(), rest);
                    out(sender, msg("given").replace("{item}", t.displayName)
                            .replace("{amount}", String.valueOf(amount)).replace("{player}", target.getName()));
                }
            }
            default -> {
                if (sender instanceof Player p) menu.openMain(p);
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> res = new ArrayList<>();
        if (!sender.hasPermission("spheres.admin")) return res;
        if (args.length == 1) {
            res.add("give");
            res.add("reload");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (Player p : Bukkit.getOnlinePlayers()) res.add(p.getName());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            res.add("all");
            for (SphereType t : SphereType.values()) res.add(t.id);
        }
        return res;
    }
}
