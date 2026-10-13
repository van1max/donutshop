package me.donutshop;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class DonutShop extends JavaPlugin implements Listener, CommandExecutor {

    private Economy econ;
    private final Map<Integer, ShopItem> shopItems = new HashMap<>();

    record ShopItem(Material mat, int amount, double price, String name) {}

    /** Marker so we only touch our own GUI. */
    static class ShopHolder implements InventoryHolder {
        public Inventory getInventory() { return null; }
    }

    @Override
    public void onEnable() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().severe("No Vault economy found! Install Vault + an economy plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        econ = rsp.getProvider();
        saveDefaultConfig();
        loadItems();
        getCommand("shop").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    private String c(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
    private String msg(String key) { return c(getConfig().getString("messages." + key, key)); }

    private void loadItems() {
        shopItems.clear();
        ConfigurationSection sec = getConfig().getConfigurationSection("items");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(key);
            Material m = Material.matchMaterial(s.getString("material", "STONE"));
            if (m == null) { getLogger().warning("Bad material for " + key); continue; }
            shopItems.put(s.getInt("slot"), new ShopItem(m, s.getInt("amount", 1),
                    s.getDouble("price"), s.getString("name", null)));
        }
    }

    private void open(Player p) {
        int rows = Math.max(1, Math.min(6, getConfig().getInt("rows", 6)));
        Inventory inv = Bukkit.createInventory(new ShopHolder(), rows * 9, c(getConfig().getString("title", "Quick Buy")));

        Material fm = Material.matchMaterial(getConfig().getString("filler", "GRAY_STAINED_GLASS_PANE"));
        if (fm != null && fm != Material.AIR) {
            ItemStack f = new ItemStack(fm);
            ItemMeta fmM = f.getItemMeta();
            fmM.setDisplayName(" ");
            f.setItemMeta(fmM);
            for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, f);
        }

        ConfigurationSection sec = getConfig().getConfigurationSection("items");
        for (Map.Entry<Integer, ShopItem> e : shopItems.entrySet()) {
            if (e.getKey() >= inv.getSize()) continue;
            ShopItem si = e.getValue();
            ItemStack is = new ItemStack(si.mat(), si.amount());
            ItemMeta meta = is.getItemMeta();
            if (si.name() != null) meta.setDisplayName(c(si.name()));
            List<String> lore = new ArrayList<>();
            // extra lore from config
            for (String key : sec.getKeys(false)) {
                if (sec.getInt(key + ".slot") == e.getKey())
                    for (String l : sec.getStringList(key + ".lore")) lore.add(c(l));
            }
            lore.add("");
            lore.add(c("&7Cost: &e$" + format(si.price())));
            lore.add(c("&eClick to purchase!"));
            meta.setLore(lore);
            is.setItemMeta(meta);
            inv.setItem(e.getKey(), is);
        }
        p.openInventory(inv);
    }

    private String format(double d) { return String.format("%,.0f", d); }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof ShopHolder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;

        ShopItem si = shopItems.get(e.getSlot());
        if (si == null) return;

        if (!econ.has(p, si.price())) {
            p.sendMessage(msg("no-money").replace("%price%", format(si.price())));
            return;
        }
        ItemStack give = new ItemStack(si.mat(), si.amount());
        if (!p.getInventory().addItem(give).isEmpty()) {
            // addItem returns leftovers; roll back what was added
            p.sendMessage(msg("inv-full"));
            return;
        }
        econ.withdrawPlayer(p, si.price());
        String name = si.name() != null ? ChatColor.stripColor(c(si.name())) : si.mat().name().toLowerCase().replace('_', ' ');
        p.sendMessage(msg("bought").replace("%amount%", String.valueOf(si.amount()))
                .replace("%item%", name).replace("%price%", format(si.price())));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof ShopHolder) e.setCancelled(true);
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload") && s.hasPermission("donutshop.reload")) {
            reloadConfig(); loadItems();
            s.sendMessage(msg("reloaded"));
            return true;
        }
        if (s instanceof Player p) open(p);
        return true;
    }
}
