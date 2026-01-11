package its.cactusdev.smp;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.text.SimpleDateFormat;
import java.util.*;

public class MenuHandler implements Listener {
    private final Main plugin;
    private final ClaimManager claims;
    private final PreviewManager preview;
    private final Messages messages;

    private final double claimPrice;
    private final double extendPrice;
    private final long extendMillis;
    private final double upgradeBasePrice;
    private final int previewSeconds;

    public MenuHandler(Main plugin, ClaimManager claims, PreviewManager preview, Messages messages) {
        this.plugin = plugin;
        this.claims = claims;
        this.preview = preview;
        this.messages = messages;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        plugin.getConfig().addDefault("prices.claim", 1000.0);
        plugin.getConfig().addDefault("prices.extend", 250.0);
        plugin.getConfig().addDefault("claim.duration_days", 30);
        plugin.getConfig().addDefault("claim.duration_millis", 30L * 24L * 60L * 60L * 1000L);
        plugin.getConfig().addDefault("prices.upgrade_base", 10000.0);
        plugin.getConfig().addDefault("preview.duration_seconds", 30);
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();

        this.claimPrice = plugin.getConfig().getDouble("prices.claim");
        this.extendPrice = plugin.getConfig().getDouble("prices.extend");
        this.extendMillis = plugin.getConfig().getLong("claim.duration_millis", 30L * 24L * 60L * 60L * 1000L);
        this.upgradeBasePrice = plugin.getConfig().getDouble("prices.upgrade_base");
        this.previewSeconds = plugin.getConfig().getInt("preview.duration_seconds");
    }

    private Component getMenuTitle(String menuKey, String def) {
        String path = "menus." + menuKey + ".title";
        String title = plugin.getConfig().getString(path, def);
        return messages.getComponent("titles." + menuKey, title);
    }
    
    /**
     * Oyuncunun sahip olduğu chunk sayısına göre dinamik fiyat hesaplar.
     */
    private double calculateDynamicPrice(UUID playerUUID, double basePrice) {
        boolean enabled = plugin.getConfig().getBoolean("prices.dynamic_pricing.enabled", true);
        if (!enabled) return basePrice;
        
        int ownedChunks = claims.getTotalChunksOwned(playerUUID);
        double increasePerChunk = plugin.getConfig().getDouble("prices.dynamic_pricing.price_increase_per_chunk", 0.1);
        double maxMultiplier = plugin.getConfig().getDouble("prices.dynamic_pricing.max_multiplier", 5.0);
        
        double multiplier = 1.0 + (ownedChunks * increasePerChunk);
        multiplier = Math.min(multiplier, maxMultiplier);
        
        return basePrice * multiplier;
    }


    public void openMain(Player p) {
        Chunk currentChunk = p.getLocation().getChunk();
        ClaimManager.Claim claim = claims.getClaimAt(currentChunk);
        
        // Claim içinde mi dışında mı kontrol et
        boolean inClaim = claim != null;
        boolean isOwner = inClaim && claim.getOwner().equals(p.getUniqueId());
        
        if (inClaim) {
            // Claim içindeyiz
            if (isOwner) {
                openClaimOwnerMenu(p, claim);
            } else {
                openClaimVisitorMenu(p, claim);
            }
        } else {
            // Claim dışındayız
            openNonClaimMenu(p);
        }
    }
    
    private void openNonClaimMenu(Player p) {
        Chunk currentChunk = p.getLocation().getChunk();
        int size = plugin.getConfig().getInt("menus.main.size", 27);
        Component title = getMenuTitle("main", "<green>Claim Menüsü</green>");
        Inventory inv = Bukkit.createInventory(null, size, title);

        ConfigurationSection itemsConfig = plugin.getConfig().getConfigurationSection("menus.main.items");
        if (itemsConfig != null) {
            // Dinamik fiyat hesapla
            double dynamicClaimPrice = calculateDynamicPrice(p.getUniqueId(), claimPrice);
            
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("claim_price", String.format("%.0f", dynamicClaimPrice));
            placeholders.put("extend_price", String.valueOf(extendPrice));
            placeholders.put("preview_seconds", String.valueOf(previewSeconds));
            placeholders.put("world", currentChunk.getWorld().getName());
            placeholders.put("x", String.valueOf(currentChunk.getX()));
            placeholders.put("z", String.valueOf(currentChunk.getZ()));
            placeholders.put("owner", "Boş");

            for (String slotStr : itemsConfig.getKeys(false)) {
                int slot = Integer.parseInt(slotStr);
                ConfigurationSection itemConfig = itemsConfig.getConfigurationSection(slotStr);
                if (itemConfig == null) continue;

                String action = itemConfig.getString("action", "");
                Material material = Material.valueOf(itemConfig.getString("material", "AIR"));
                String name = itemConfig.getString("name", "");
                List<String> lore = itemConfig.getStringList("lore");
                int customModelData = itemConfig.getInt("custom_model_data", 0);
                
                ItemStack item = createItem(material, name, lore, placeholders, "main", action, null, customModelData);
                inv.setItem(slot, item);
            }
        }

        p.openInventory(inv);
    }

    private void openClaimVisitorMenu(Player p, ClaimManager.Claim claim) {
        int size = plugin.getConfig().getInt("menus.claim_visitor.size", 27);
        OfflinePlayer owner = Bukkit.getOfflinePlayer(claim.getOwner());
        String ownerName = owner.getName() != null ? owner.getName() : "Bilinmiyor";
        Component title = getMenuTitle("claim_visitor", "<gray>" + claim.getClaimName() + " - " + ownerName + "</gray>");
        Inventory inv = Bukkit.createInventory(null, size, title);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("claim_name", claim.getClaimName());
        placeholders.put("owner", ownerName);
        TrustManager.TrustLevel level = plugin.trustManager().getTrustLevel(claim.getChunkKey(), p.getUniqueId());
        placeholders.put("is_member", (level != null && level != TrustManager.TrustLevel.VISITOR) ? "Evet" : "Hayır");

        ConfigurationSection itemsConfig = plugin.getConfig().getConfigurationSection("menus.claim_visitor.items");
        if (itemsConfig != null) {
            for (String slotStr : itemsConfig.getKeys(false)) {
                int slot = Integer.parseInt(slotStr);
                ConfigurationSection itemConfig = itemsConfig.getConfigurationSection(slotStr);
                if (itemConfig == null) continue;

                Material material = Material.valueOf(itemConfig.getString("material", "AIR"));
                String name = itemConfig.getString("name", "");
                List<String> lore = itemConfig.getStringList("lore");
                String action = itemConfig.getString("action", "");
                int customModelData = itemConfig.getInt("custom_model_data", 0);

                inv.setItem(slot, createItem(material, name, lore, placeholders, "claim_visitor", action, null, customModelData));
            }
        }

        p.openInventory(inv);
    }
    
    private void openClaimOwnerMenu(Player p, ClaimManager.Claim claim) {
        int size = plugin.getConfig().getInt("menus.claim_owner.size", 27);
        Component title = messages.getComponent("menus.claim_owner.title", "<gold>★ " + claim.getClaimName() + " ★</gold>");
        Inventory inv = Bukkit.createInventory(null, size, title);

        Map<String, String> basePlaceholders = new HashMap<>();
        basePlaceholders.put("claim_name", claim.getClaimName());
        basePlaceholders.put("owner", p.getName());
        long chunkCount = claims.getAllClaims().stream()
            .filter(c -> c.getOwner().equals(claim.getOwner()) && c.getWorld().equals(claim.getWorld()))
            .count();
        basePlaceholders.put("chunks", String.valueOf(chunkCount));
        long daysLeft = (claim.getExpiresAt() - System.currentTimeMillis()) / (24 * 60 * 60 * 1000);
        basePlaceholders.put("days_left", String.valueOf(daysLeft));

        ConfigurationSection itemsConfig = plugin.getConfig().getConfigurationSection("menus.claim_owner.items");
        if (itemsConfig != null) {
            for (String slotStr : itemsConfig.getKeys(false)) {
                int slot = Integer.parseInt(slotStr);
                ConfigurationSection itemConfig = itemsConfig.getConfigurationSection(slotStr);
                if (itemConfig == null) continue;

                String action = itemConfig.getString("action", "");
                Material material = Material.valueOf(itemConfig.getString("material", "AIR"));
                String name = itemConfig.getString("name", "");
                List<String> lore = itemConfig.getStringList("lore");
                int customModelData = itemConfig.getInt("custom_model_data", 0);

                Map<String, String> placeholders = new HashMap<>(basePlaceholders);
                inv.setItem(slot, createItem(material, name, lore, placeholders, "claim_owner", action, null, customModelData));
            }
        }

        p.openInventory(inv);
    }

    private ItemStack createItem(Material material, String name, List<String> lore, Map<String, String> placeholders) {
        return createItem(material, name, lore, placeholders, null, null, null, 0);
    }

    private ItemStack createItem(Material material, String name, List<String> lore, Map<String, String> placeholders,
                                  String menuType, String action, String data) {
        return createItem(material, name, lore, placeholders, menuType, action, data, 0);
    }

    private ItemStack createItem(Material material, String name, List<String> lore, Map<String, String> placeholders,
                                  String menuType, String action, String data, int customModelData) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Lore'u mutable hale getir
            List<String> mutableLore = new ArrayList<>(lore);
            
            // Replace placeholders
            if (placeholders != null) {
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    name = name.replace("{" + entry.getKey() + "}", entry.getValue());
                    for (int i = 0; i < mutableLore.size(); i++) {
                        mutableLore.set(i, mutableLore.get(i).replace("{" + entry.getKey() + "}", entry.getValue()));
                    }
                }
            }
            meta.displayName(messages.getComponent("", name));
            List<Component> loreComponents = new ArrayList<>();
            for (String loreLine : mutableLore) {
                loreComponents.add(messages.getComponent("", loreLine));
            }
            meta.lore(loreComponents);
            
            // CustomModelData ekle
            if (customModelData > 0) {
                meta.setCustomModelData(customModelData);
            }
            
            // NBT ekle
            if (menuType != null) {
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                pdc.set(new NamespacedKey(plugin, "menu_type"), PersistentDataType.STRING, menuType);
                if (action != null) {
                    pdc.set(new NamespacedKey(plugin, "action"), PersistentDataType.STRING, action);
                }
                if (data != null) {
                    pdc.set(new NamespacedKey(plugin, "data"), PersistentDataType.STRING, data);
                }
            }
            
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;
        
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey menuKey = new NamespacedKey(plugin, "menu_type");
        NamespacedKey actionKey = new NamespacedKey(plugin, "action");
        NamespacedKey dataKey = new NamespacedKey(plugin, "data");
        
        // NBT varsa menü item'ı, inventory'yi engelle ve işle
        if (pdc.has(menuKey, PersistentDataType.STRING)) {
            e.setCancelled(true);
            
            String menuType = pdc.get(menuKey, PersistentDataType.STRING);
            String action = pdc.has(actionKey, PersistentDataType.STRING) ? 
                           pdc.get(actionKey, PersistentDataType.STRING) : null;
            String data = pdc.has(dataKey, PersistentDataType.STRING) ? 
                         pdc.get(dataKey, PersistentDataType.STRING) : null;
            
            // Shift+tıklama kontrolü
            if (menuType.equals("members") && action != null && action.equals("manage_trust") && e.isShiftClick()) {
                handleMenuClick(p, menuType, "remove_trust", data);
            }
            // Shift+Sağ tıklama kontrolü (homes menüsü için home silme)
            else if (menuType.equals("homes") && action != null && action.equals("teleport") && 
                     e.getClick() == ClickType.SHIFT_RIGHT) {
                handleMenuClick(p, menuType, "delete", data);
            } else {
                handleMenuClick(p, menuType, action, data);
            }
        }
    }
    
    private void handleMenuClick(Player p, String menuType, String action, String data) {
        if (action == null) return;
        
        switch (menuType) {
            case "main" -> handleMainMenuClick(p, action, data);
            case "claim_owner" -> handleMainMenuClick(p, action, data);
            case "claim_visitor" -> handleClaimVisitorMenuClick(p, action, data);
            case "settings" -> handleSettingsMenuClick(p, action, data);
            case "members" -> handleMembersMenuClick(p, action, data);
            case "upgrade" -> handleUpgradeMenuClick(p, action, data);
            case "extend" -> handleExtendMenuClick(p, action, data);
            case "market" -> handleMarketMenuClick(p, action, data);
            case "bank" -> handleBankMenuClick(p, action, data);
            case "logs" -> handleLogsMenuClick(p, action, data);
            case "homes" -> handleHomesMenuClick(p, action, data);
        }
    }

    private void handleClaimVisitorMenuClick(Player p, String action, String data) {
        if ("close".equals(action)) {
            p.closeInventory();
        }
    }
    
    private void handleMainMenuClick(Player p, String action, String data) {
        switch (action) {
            case "buy" -> handleBuy(p);
            case "preview" -> togglePreview(p);
            case "info" -> openInfo(p);
            case "extend_menu" -> openExtendMenu(p);
            case "members" -> openMembers(p);
            case "settings" -> openSettings(p);
            case "upgrade_map" -> openUpgradeMap(p);
            case "bank" -> openBank(p);
            case "logs" -> openLogs(p);
            case "homes" -> openHomesMenu(p);
            case "market_menu" -> openMarketMenu(p, 0);
            case "close" -> p.closeInventory();
        }
    }
    
    private void handleSettingsMenuClick(Player p, String action, String data) {
        Chunk c = p.getLocation().getChunk();
        ClaimManager.Claim claim = claims.getClaimAt(c);
        if (claim == null || !claim.getOwner().equals(p.getUniqueId())) return;
        
        switch (action) {
            case "toggle_explosions" -> {
                claims.updateFlags(c, !claim.isAllowExplosions(), claim.isAllowPvp(), 
                                  claim.isAllowInteract(), claim.isAllowOpenDoors());
                p.sendMessage(messages.getComponent("messages.setting_updated",
                    "<green>✓ Patlama ayarı güncellendi!</green>"));
                openSettings(p);
            }
            case "toggle_pvp" -> {
                claims.updateFlags(c, claim.isAllowExplosions(), !claim.isAllowPvp(), 
                                  claim.isAllowInteract(), claim.isAllowOpenDoors());
                p.sendMessage(messages.getComponent("messages.setting_updated",
                    "<green>✓ PvP ayarı güncellendi!</green>"));
                openSettings(p);
            }
            case "toggle_interact" -> {
                claims.updateFlags(c, claim.isAllowExplosions(), claim.isAllowPvp(), 
                                  !claim.isAllowInteract(), claim.isAllowOpenDoors());
                p.sendMessage(messages.getComponent("messages.setting_updated",
                    "<green>✓ Etkileşim ayarı güncellendi!</green>"));
                openSettings(p);
            }
            case "toggle_doors" -> {
                claims.updateFlags(c, claim.isAllowExplosions(), claim.isAllowPvp(), 
                                  claim.isAllowInteract(), !claim.isAllowOpenDoors());
                p.sendMessage(messages.getComponent("messages.setting_updated",
                    "<green>✓ Kapı ayarı güncellendi!</green>"));
                openSettings(p);
            }
            case "back" -> openMain(p);
            case "close" -> p.closeInventory();
        }
    }
    
    private void handleMembersMenuClick(Player p, String action, String data) {
        Chunk c = p.getLocation().getChunk();
        ClaimManager.Claim claim = claims.getClaimAt(c);
        if (claim == null || !claim.getOwner().equals(p.getUniqueId())) return;
        
        switch (action) {
            case "manage_trust" -> {
                if (data != null) {
                    try {
                        UUID targetUuid = UUID.fromString(data);
                        TrustManager.TrustLevel currentLevel = plugin.trustManager().getTrustLevel(claim.getChunkKey(), targetUuid);
                        
                        // Yetki döngüsü: VISITOR -> BUILDER -> MANAGER -> VISITOR
                        TrustManager.TrustLevel newLevel = switch(currentLevel) {
                            case VISITOR -> TrustManager.TrustLevel.BUILDER;
                            case BUILDER -> TrustManager.TrustLevel.MANAGER;
                            case MANAGER -> TrustManager.TrustLevel.VISITOR;
                            case null -> TrustManager.TrustLevel.VISITOR;
                        };
                        
                        plugin.trustManager().setTrust(claim.getChunkKey(), targetUuid, newLevel);
                        String levelName = switch(newLevel) {
                            case VISITOR -> "Ziyaretçi";
                            case BUILDER -> "İnşaatçı";
                            case MANAGER -> "Yönetici";
                        };
                        p.sendMessage(messages.getComponent("messages.trust_updated",
                            "<green>Yetki güncellendi: " + levelName + "</green>"));
                        openMembers(p);
                    } catch (IllegalArgumentException e) {
                        p.sendMessage(messages.getComponent("messages.error", "<red>Hata!</red>"));
                    }
                }
            }
            case "remove_trust" -> {
                if (data != null) {
                    try {
                        UUID targetUuid = UUID.fromString(data);
                        plugin.trustManager().removeTrust(claim.getChunkKey(), targetUuid);
                        p.sendMessage(messages.getComponent("messages.trust_removed",
                            "<red>Üye çıkarıldı!</red>"));
                        openMembers(p);
                    } catch (IllegalArgumentException e) {
                        p.sendMessage(messages.getComponent("messages.error", "<red>Hata!</red>"));
                    }
                }
            }
            case "add_member" -> {
                p.closeInventory();
                p.sendMessage(messages.getComponent("messages.add_member_prompt",
                    "<yellow>Eklemek istediğin oyuncunun adını yaz. İptal: 'iptal'</yellow>"));
                ChatInput.waitFor(p.getUniqueId(), input -> {
                    if (input.equalsIgnoreCase("iptal")) {
                        p.sendMessage(messages.getComponent("messages.cancelled", "<gray>İptal edildi.</gray>"));
                        openMembers(p);
                        return;
                    }
                    OfflinePlayer op = Bukkit.getOfflinePlayer(input);
                    if (op.getUniqueId() == null) {
                        p.sendMessage(messages.getComponent("messages.player_not_found", "<red>Oyuncu bulunamadı.</red>"));
                        openMembers(p);
                        return;
                    }
                    // Varsayılan olarak VISITOR seviyesinde ekle
                    plugin.trustManager().setTrust(claim.getChunkKey(), op.getUniqueId(), TrustManager.TrustLevel.VISITOR);
                    p.sendMessage(messages.getComponent("messages.member_added",
                        "<green>✓ Üye eklendi: " + input + "</green>"));
                    openMembers(p);
                });
            }
            case "close" -> p.closeInventory();
            case "back" -> openMain(p);
        }
    }
    
    private void handleUpgradeMenuClick(Player p, String action, String data) {
        if (action.equals("buy") && data != null) {
            String[] coords = data.split(",");
            if (coords.length == 2) {
                try {
                    int dx = Integer.parseInt(coords[0]);
                    int dz = Integer.parseInt(coords[1]);
                    attemptUpgradePurchase(p, dx, dz);
                } catch (NumberFormatException e) {
                    // Geçersiz koordinat
                }
            }
        }
    }
    
    private void handleExtendMenuClick(Player p, String action, String data) {
        if (action.equals("extend") && data != null) {
            try {
                int days = Integer.parseInt(data);
                handleExtend(p, days);
            } catch (NumberFormatException e) {
                // Geçersiz gün sayısı
            }
        } else if (action.equals("back")) {
            openMain(p);
        }
    }
    
    private void handleMarketMenuClick(Player p, String action, String data) {
        if (action.equals("close")) {
            p.closeInventory();
        } else if (action.equals("prev_page") && data != null) {
            try {
                int page = Integer.parseInt(data);
                openMarketMenu(p, page);
            } catch (NumberFormatException e) {
                openMarketMenu(p, 0);
            }
        } else if (action.equals("next_page") && data != null) {
            try {
                int page = Integer.parseInt(data);
                openMarketMenu(p, page);
            } catch (NumberFormatException e) {
                openMarketMenu(p, 0);
            }
        } else if (action.equals("buy") && data != null) {
            try {
                int index = Integer.parseInt(data);
                List<ClaimMarket.MarketListing> listings = plugin.market().getAllListings();
                if (index >= 0 && index < listings.size()) {
                    ClaimMarket.MarketListing listing = listings.get(index);
                    
                    if (listing.claim.getOwner().equals(p.getUniqueId())) {
                        p.sendMessage(messages.getComponent("messages.cannot_buy_own",
                            "<red>Kendi claim'inizi satın alamazsınız!</red>"));
                        return;
                    }
                    
                    boolean success = plugin.market().buyClaim(p, listing);
                    if (success) {
                        p.closeInventory();
                        p.sendMessage(messages.getComponent("messages.claim_bought",
                            "<green>✓ Claim başarıyla satın alındı!</green>"));
                    } else {
                        p.sendMessage(messages.getComponent("messages.buy_failed",
                            "<red>Satın alma başarısız! (Yetersiz bakiye veya başka bir hata)</red>"));
                    }
                }
            } catch (NumberFormatException e) {
                // Geçersiz index
            }
        }
    }

    private void openInfo(Player p) {
        Chunk c = p.getLocation().getChunk();
        ClaimManager.Claim claim = claims.getClaimAt(c);
        int size = plugin.getConfig().getInt("menus.info.size", 27);
        Component title = getMenuTitle("info", "<yellow>Claim Bilgisi</yellow>");
        Inventory inv = Bukkit.createInventory(null, size, title);
        
        ConfigurationSection itemConfig = plugin.getConfig().getConfigurationSection("menus.info.items.13");
        if (itemConfig == null) return;
        
        Map<String, String> placeholders = new HashMap<>();
        if (claim == null) {
            Material material = Material.valueOf(itemConfig.getString("not_claimed_material", "PAPER"));
            String name = itemConfig.getString("not_claimed_name", "<red>Claim Bilgisi</red>");
            List<String> lore = itemConfig.getStringList("not_claimed_lore");
            inv.setItem(13, createItem(material, name, lore, placeholders));
        } else {
            String ownerName = Optional.ofNullable(p.getServer().getOfflinePlayer(claim.getOwner()).getName()).orElse("Bilinmiyor");
            placeholders.put("owner", ownerName);
            placeholders.put("world", claim.getWorld());
            placeholders.put("x", String.valueOf(claim.getX()));
            placeholders.put("z", String.valueOf(claim.getZ()));
            placeholders.put("time", human(claim.getExpiresAt() - System.currentTimeMillis()));
            placeholders.put("pvp", claim.isAllowPvp() ? "Açık" : "Kapalı");
            placeholders.put("explosions", claim.isAllowExplosions() ? "Açık" : "Kapalı");
            placeholders.put("interact", claim.isAllowInteract() ? "Açık" : "Kapalı");
            placeholders.put("doors", claim.isAllowOpenDoors() ? "Açık" : "Kapalı");
            placeholders.put("created", new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(claim.getCreatedAt())));
            
            Material material = Material.valueOf(itemConfig.getString("material", "PAPER"));
            String name = itemConfig.getString("name", "<yellow>Claim Bilgisi</yellow>");
            List<String> lore = itemConfig.getStringList("lore");
            
            // Add members to lore if exists
            if (!claim.getMembers().isEmpty()) {
                List<String> newLore = new ArrayList<>(lore);
                newLore.add("<gray>Üyeler:</gray>");
                for (UUID u : claim.getMembers()) {
                    String memberName = Optional.ofNullable(p.getServer().getOfflinePlayer(u).getName()).orElse(u.toString());
                    newLore.add("<gray> - <white>" + memberName + "</white></gray>");
                }
                lore = newLore;
            }
            
            inv.setItem(13, createItem(material, name, lore, placeholders));
        }
        p.openInventory(inv);
    }

    private String human(long deltaMs) {
        if (deltaMs <= 0) return "Süre doldu";
        long s = deltaMs / 1000;
        long d = s / 86400; s %= 86400;
        long h = s / 3600; s %= 3600;
        long m = s / 60; s %= 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("g ");
        if (h > 0) sb.append(h).append("s ");
        if (m > 0) sb.append(m).append("d ");
        if (sb.length() == 0) sb.append(s).append("sn");
        return sb.toString().trim();
    }

    private void togglePreview(Player p) {
        Chunk c = p.getLocation().getChunk();
        if (preview.isPreviewing(p)) {
            preview.stopPreview(p);
            p.sendMessage(messages.getComponent("messages.preview_off", "<gray>Önizleme kapatıldı.</gray>"));
        } else {
            preview.startPreview(p, c.getWorld(), c.getX(), c.getZ(), previewSeconds);
            p.sendMessage(messages.getComponent("messages.preview_on", "<green>Önizleme açıldı.</green>"));
        }
        openMain(p);
    }

    private void handleBuy(Player p) {
        Chunk chunk = p.getLocation().getChunk();
        if (claims.getClaimAt(chunk) != null) {
            p.sendMessage(messages.getComponent("messages.already_claimed", "<red>Bu chunk zaten alınmış.</red>"));
            return;
        }
        
        // 10x10 alan kontrolü
        if (hasAnyClaimIn10x10(chunk)) {
            p.sendMessage(messages.getComponent("messages.10x10_claim_found", "<red>10x10 alan içinde claim bulunduğu için satın alınamaz.</red>"));
            return;
        }
        
        // Dinamik fiyatlandırma hesapla
        double finalPrice = calculateDynamicPrice(p.getUniqueId(), claimPrice);
        
        // Önce parayı çek
        if (!Main.get().economy().withdrawPlayer(p, finalPrice).transactionSuccess()) {
            p.sendMessage(messages.getComponent("messages.insufficient_funds", "<red>Yetersiz bakiye.</red>"));
            return;
        }
        
        // Claim ismi sor
        p.closeInventory();
        p.sendMessage(messages.getComponent("messages.enter_claim_name", "<green>Arazi ismi girin (veya 'iptal' yazın):</green>"));
        
        ChatInput.waitFor(p.getUniqueId(), input -> {
            if (input.equalsIgnoreCase("iptal") || input.equalsIgnoreCase("cancel")) {
                // Parayı iade et
                Main.get().economy().depositPlayer(p, finalPrice);
                p.sendMessage(messages.getComponent("messages.claim_cancelled", "<yellow>İptal edildi, para iade edildi.</yellow>"));
                return;
            }
            
            // İsim uzunluk kontrolü
            String claimName = input.trim();
            if (claimName.length() > 32) {
                claimName = claimName.substring(0, 32);
            }
            if (claimName.isEmpty()) {
                claimName = "Adlandırılmamış Arazi";
            }
            
            // Claim oluştur
            if (claims.claimChunk(p, chunk, extendMillis, claimName)) {
                ClaimManager.Claim newClaim = claims.getClaimAt(chunk);
                if (newClaim != null) {
                    Main.get().claimStones().updateOrCreateStone(newClaim);
                }
                p.sendMessage(messages.getComponent("messages.claim_acquired", "<green>Arazi alındı: <white>" + claimName + "</white></green>"));
                p.sendActionBar(messages.getComponent("actionbar.claim_owned", "<yellow>Bu arazi {owner} kişisine aittir</yellow>", "owner", p.getName()));
            } else {
                // Hata durumunda parayı iade et
                Main.get().economy().depositPlayer(p, claimPrice);
                p.sendMessage(messages.getComponent("messages.claim_failed", "<red>Chunk alınamadı, para iade edildi.</red>"));
            }
        });
    }

    private boolean hasAnyClaimIn10x10(Chunk center) {
        int centerX = center.getX();
        int centerZ = center.getZ();
        
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                // Merkezi atla
                if (dx == 0 && dz == 0) continue;
                
                int x = centerX + dx;
                int z = centerZ + dz;
                ClaimManager.Claim claim = claims.getClaimAt(center.getWorld().getChunkAt(x, z));
                if (claim != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private void openExtendMenu(Player p) {
        Chunk chunk = p.getLocation().getChunk();
        ClaimManager.Claim claim = claims.getClaimAt(chunk);
        if (claim == null) {
            p.sendMessage(messages.getComponent("messages.not_claimed", "<red>Bu chunk size ait değil.</red>"));
            return;
        }
        if (!claim.getOwner().equals(p.getUniqueId())) {
            p.sendMessage(messages.getComponent("messages.not_owner", "<red>Sadece sahibi uzatabilir.</red>"));
            return;
        }

        int size = plugin.getConfig().getInt("menus.extend.size", 27);
        Component title = getMenuTitle("extend", "<gold>Süre Uzatma</gold>");
        Inventory inv = Bukkit.createInventory(null, size, title);

        ConfigurationSection extendOptions = plugin.getConfig().getConfigurationSection("extend.options");
        if (extendOptions != null) {
            int slot = 10; // Başlangıç slotu
            for (String key : extendOptions.getKeys(false)) {
                ConfigurationSection option = extendOptions.getConfigurationSection(key);
                if (option == null) continue;

                int days = option.getInt("days", 1);
                double price = option.getDouble("price", 10000.0);
                Material material = Material.valueOf(option.getString("material", "LIME_DYE"));
                String name = option.getString("name", "<green>+{days} Gün</green>");
                List<String> lore = option.getStringList("lore");
                int customModelData = option.getInt("custom_model_data", 0);

                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("days", String.valueOf(days));
                placeholders.put("price", String.valueOf(price));
                placeholders.put("time", human(days * 24L * 60L * 60L * 1000L));

                // Name'deki placeholder'ları değiştir
                name = name.replace("{days}", String.valueOf(days));

                inv.setItem(slot, createItem(material, name, lore, placeholders, "extend", "extend", String.valueOf(days), customModelData));
                slot += 2; // Her seçenek arasında 1 slot boşluk
            }
        }

        // Geri dön butonu
        ConfigurationSection backConfig = plugin.getConfig().getConfigurationSection("menus.extend.items.back");
        if (backConfig != null) {
            Material material = Material.valueOf(backConfig.getString("material", "ARROW"));
            String name = backConfig.getString("name", "<gray>Geri Dön</gray>");
            List<String> lore = backConfig.getStringList("lore");
            inv.setItem(22, createItem(material, name, lore, null, "extend", "back", null));
        } else {
            // Varsayılan geri dön butonu
            inv.setItem(22, createItem(Material.ARROW, "<gray>Geri Dön</gray>", 
                List.of("<gray>Ana menüye dön</gray>"), null, "extend", "back", null));
        }

        p.openInventory(inv);
    }

    private void handleExtend(Player p, int days) {
        Chunk chunk = p.getLocation().getChunk();
        ClaimManager.Claim claim = claims.getClaimAt(chunk);
        if (claim == null) {
            p.sendMessage(messages.getComponent("messages.not_claimed", "<red>Bu chunk size ait değil.</red>"));
            return;
        }
        if (!claim.getOwner().equals(p.getUniqueId())) {
            p.sendMessage(messages.getComponent("messages.not_owner", "<red>Sadece sahibi uzatabilir.</red>"));
            return;
        }

        ConfigurationSection extendOptions = plugin.getConfig().getConfigurationSection("extend.options");
        if (extendOptions == null) return;

        // Gün sayısına göre seçeneği bul
        ConfigurationSection option = null;
        for (String key : extendOptions.getKeys(false)) {
            ConfigurationSection opt = extendOptions.getConfigurationSection(key);
            if (opt != null && opt.getInt("days", 0) == days) {
                option = opt;
                break;
            }
        }

        if (option == null) {
            p.sendMessage(messages.getComponent("messages.invalid_option", "<red>Geçersiz seçenek.</red>"));
            return;
        }

        double price = option.getDouble("price", 10000.0);
        if (Main.get().economy().withdrawPlayer(p, price).transactionSuccess()) {
            long extendMillis = days * 24L * 60L * 60L * 1000L;
            claims.extendClaim(chunk, extendMillis);
            p.sendMessage(messages.getComponent("messages.extend_success", "<green>Süre uzatıldı: +{days} gün</green>", "days", String.valueOf(days)));
            openExtendMenu(p); // Menüyü yenile
        } else {
            p.sendMessage(messages.getComponent("messages.insufficient_funds", "<red>Yetersiz bakiye.</red>"));
        }
    }

    private void openMembers(Player p) {
        Chunk c = p.getLocation().getChunk();
        ClaimManager.Claim claim = claims.getClaimAt(c);
        if (claim == null || !claim.getOwner().equals(p.getUniqueId())) {
            p.sendMessage(messages.getComponent("messages.not_claimed", "<red>Bu chunk size ait değil.</red>"));
            return;
        }
        
        int size = 54;
        Component title = getMenuTitle("members", "<blue>Üye Yönetimi</blue>");
        Inventory inv = Bukkit.createInventory(null, size, title);
        
        // Trusted üyeleri göster
        Map<UUID, TrustManager.TrustLevel> trustedPlayers = plugin.trustManager().getAllTrusted(claim.getChunkKey());
        int slot = 0;
        
        for (Map.Entry<UUID, TrustManager.TrustLevel> entry : trustedPlayers.entrySet()) {
            if (slot >= 45) break; // İlk 45 slot üyeler için
            
            OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
            String playerName = op.getName() != null ? op.getName() : "Bilinmiyor";
            TrustManager.TrustLevel level = entry.getValue();
            
            String levelColor = switch(level) {
                case VISITOR -> "<gray>";
                case BUILDER -> "<green>";
                case MANAGER -> "<gold>";
            };
            
            String levelName = switch(level) {
                case VISITOR -> "Ziyaretçi";
                case BUILDER -> "İnşaatçı";
                case MANAGER -> "Yönetici";
            };
            
            List<String> lore = new ArrayList<>();
            lore.add("<gray>─────────────────</gray>");
            lore.add(levelColor + "Yetki: " + levelName + levelColor.replace("<", "</"));
            lore.add("");
            lore.add("<yellow>» Tıkla: Yetki Değiştir</yellow>");
            lore.add("<red>» Shift+Tıkla: Çıkar</red>");
            
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(op);
                meta.displayName(messages.getComponent("", "<white>" + playerName + "</white>"));
                List<Component> loreComponents = new ArrayList<>();
                for (String loreLine : lore) {
                    loreComponents.add(messages.getComponent("", loreLine));
                }
                meta.lore(loreComponents);
                
                // NBT ekle
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                pdc.set(new NamespacedKey(plugin, "menu_type"), PersistentDataType.STRING, "members");
                pdc.set(new NamespacedKey(plugin, "action"), PersistentDataType.STRING, "manage_trust");
                pdc.set(new NamespacedKey(plugin, "data"), PersistentDataType.STRING, entry.getKey().toString());
                
                head.setItemMeta(meta);
            }
            
            inv.setItem(slot++, head);
        }
        
        // Alt kısım butonlar
        ItemStack addButton = createItem(Material.LIME_DYE, "<green>+ Üye Ekle</green>",
            List.of("<gray>Yeni üye ekle</gray>", "", "<aqua>» Tıkla!</aqua>"),
            null, "members", "add_member", null, 0);
        inv.setItem(48, addButton);
        
        ItemStack backButton = createItem(Material.ARROW, "<gray>← Geri Dön</gray>",
            List.of("<gray>Ana menüye dön</gray>"),
            null, "members", "back", null, 1001);
        inv.setItem(49, backButton);
        
        ItemStack closeButton = createItem(Material.BARRIER, "<red>✖ Kapat</red>",
            List.of(),
            null, "members", "close", null, 0);
        inv.setItem(50, closeButton);
        
        p.openInventory(inv);
    }

    private void openSettings(Player p) {
        Chunk c = p.getLocation().getChunk();
        ClaimManager.Claim claim = claims.getClaimAt(c);
        if (claim == null || !claim.getOwner().equals(p.getUniqueId())) {
            p.sendMessage(messages.getComponent("messages.not_claimed", "<red>Bu chunk size ait değil.</red>"));
            return;
        }
        int size = plugin.getConfig().getInt("menus.settings.size", 27);
        Component title = getMenuTitle("settings", "<red>Claim Ayarları</red>");
        Inventory inv = Bukkit.createInventory(null, size, title);
        
        ConfigurationSection itemsConfig = plugin.getConfig().getConfigurationSection("menus.settings.items");
        if (itemsConfig != null) {
            for (String slotStr : itemsConfig.getKeys(false)) {
                int slot = Integer.parseInt(slotStr);
                ConfigurationSection itemConfig = itemsConfig.getConfigurationSection(slotStr);
                if (itemConfig == null) continue;
                
                Material material = Material.valueOf(itemConfig.getString("material", "AIR"));
                String name = itemConfig.getString("name", "");
                List<String> lore = itemConfig.getStringList("lore");
                String action = itemConfig.getString("action", "");

                Map<String, String> placeholders = new HashMap<>();
                switch (action) {
                    case "toggle_pvp" -> placeholders.put("pvp_status", claim.isAllowPvp() ?
                        itemConfig.getString("status_on", "<green>Açık</green>") :
                        itemConfig.getString("status_off", "<red>Kapalı</red>"));
                    case "toggle_doors" -> placeholders.put("doors_status", claim.isAllowOpenDoors() ?
                        itemConfig.getString("status_on", "<green>Açık</green>") :
                        itemConfig.getString("status_off", "<red>Kapalı</red>"));
                    case "toggle_interact" -> placeholders.put("interact_status", claim.isAllowInteract() ?
                        itemConfig.getString("status_on", "<green>Açık</green>") :
                        itemConfig.getString("status_off", "<red>Kapalı</red>"));
                    case "toggle_explosions" -> placeholders.put("explosions_status", claim.isAllowExplosions() ?
                        itemConfig.getString("status_on", "<green>Açık</green>") :
                        itemConfig.getString("status_off", "<red>Kapalı</red>"));
                }

                int customModelData = itemConfig.getInt("custom_model_data", 0);
                inv.setItem(slot, createItem(material, name, lore, placeholders, "settings", action, null, customModelData));
            }
        }
        
        p.openInventory(inv);
    }

    public void openUpgradeMap(Player p) {
        Chunk playerChunk = p.getLocation().getChunk();
        // Eğer oyuncu bir claim içindeyse, o claim'in ana chunk'ını kullan
        ClaimManager.Claim mainClaim = claims.getMainClaimChunk(playerChunk);
        
        // Claim yoksa veya sahibi değilse menü açılmasın
        if (mainClaim == null || !mainClaim.getOwner().equals(p.getUniqueId())) {
            p.sendMessage(messages.getComponent("messages.no_claim_for_upgrade", "<red>Yükseltme menüsü için bir claim'iniz olmalı.</red>"));
            return;
        }
        
        int size = plugin.getConfig().getInt("menus.upgrade.size", 45);
        Component title = getMenuTitle("upgrade", "<purple>Claim Yükseltme</purple>");
        Inventory inv = Bukkit.createInventory(null, size, title);
        
        // Ana claim chunk'ını kullan
        World w = playerChunk.getWorld();
        Chunk center = w.getChunkAt(mainClaim.getX(), mainClaim.getZ());
        
        ConfigurationSection itemsConfig = plugin.getConfig().getConfigurationSection("menus.upgrade.items");
        if (itemsConfig == null) return;
        
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                int slot = slotFromOffset(dx, dz);
                if (dx == 0 && dz == 0) {
                    // Center
                    ConfigurationSection centerConfig = itemsConfig.getConfigurationSection("center");
                    if (centerConfig != null) {
                        Material material = Material.valueOf(centerConfig.getString("material", "GOLD_BLOCK"));
                        String name = centerConfig.getString("name", "<gold>Merkez</gold>");
                        List<String> lore = centerConfig.getStringList("lore");
                        Map<String, String> placeholders = new HashMap<>();
                        placeholders.put("x", String.valueOf(center.getX()));
                        placeholders.put("z", String.valueOf(center.getZ()));
                        inv.setItem(slot, createItem(material, name, lore, placeholders, "upgrade", "center", null));
                    }
                    continue;
                }
                
                int tx = center.getX() + dx;
                int tz = center.getZ() + dz;
                Chunk target = w.getChunkAt(tx, tz);
                ClaimManager.Claim c = claims.getClaimAt(target);
                
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("x", String.valueOf(tx));
                placeholders.put("z", String.valueOf(tz));
                
                if (c != null) {
                    if (c.getOwner().equals(p.getUniqueId())) {
                        // Owned
                        ConfigurationSection ownedConfig = itemsConfig.getConfigurationSection("owned");
                        if (ownedConfig != null) {
                            Material material = Material.valueOf(ownedConfig.getString("material", "LIME_STAINED_GLASS_PANE"));
                            String name = ownedConfig.getString("name", "<green>Sizde</green>");
                            List<String> lore = ownedConfig.getStringList("lore");
                            inv.setItem(slot, createItem(material, name, lore, placeholders, "upgrade", "owned", null));
                        }
                    } else {
                        // Taken
                        ConfigurationSection takenConfig = itemsConfig.getConfigurationSection("taken");
                        if (takenConfig != null) {
                            Material material = Material.valueOf(takenConfig.getString("material", "RED_STAINED_GLASS_PANE"));
                            String name = takenConfig.getString("name", "<red>Dolu</red>");
                            List<String> lore = takenConfig.getStringList("lore");
                            String ownerName = Optional.ofNullable(p.getServer().getOfflinePlayer(c.getOwner()).getName()).orElse("Bilinmiyor");
                            placeholders.put("owner", ownerName);
                            inv.setItem(slot, createItem(material, name, lore, placeholders, "upgrade", "taken", null));
                        }
                    }
                } else {
                    boolean available = isAvailableFor(p, target);
                    if (available) {
                        // Available
                        ConfigurationSection availableConfig = itemsConfig.getConfigurationSection("available");
                        if (availableConfig != null) {
                            Material material = Material.valueOf(availableConfig.getString("material", "WHITE_STAINED_GLASS_PANE"));
                            String name = availableConfig.getString("name", "<green>Satın Al</green>");
                            List<String> lore = availableConfig.getStringList("lore");
                            int dist = Math.abs(dx) + Math.abs(dz);
                            double basePrice = upgradeBasePrice * dist;
                            // Dinamik fiyatlandırma hesapla
                            double price = calculateDynamicPrice(p.getUniqueId(), basePrice);
                            placeholders.put("price", String.format("%.0f", price));
                            String coordData = dx + "," + dz;
                            inv.setItem(slot, createItem(material, name, lore, placeholders, "upgrade", "buy", coordData));
                        }
                    } else {
                        // Unavailable
                        ConfigurationSection unavailableConfig = itemsConfig.getConfigurationSection("unavailable");
                        if (unavailableConfig != null) {
                            Material material = Material.valueOf(unavailableConfig.getString("material", "RED_STAINED_GLASS_PANE"));
                            String name = unavailableConfig.getString("name", "<red>Alınamaz</red>");
                            List<String> lore = unavailableConfig.getStringList("lore");
                            inv.setItem(slot, createItem(material, name, lore, placeholders, "upgrade", "unavailable", null));
                        }
                    }
                }
            }
        }
        
        p.openInventory(inv);
    }

    private boolean isAvailableFor(Player p, Chunk target) {
        if (claims.getClaimAt(target) != null) return false;
        if (!claims.hasAnyClaim(p.getUniqueId())) return true;
        
        // Target chunk'ın komşularını kontrol et
        String w = target.getWorld().getName();
        int x = target.getX(); int z = target.getZ();
        String[] neighbors = new String[]{
                ClaimManager.key(w, x+1, z), ClaimManager.key(w, x-1, z), ClaimManager.key(w, x, z+1), ClaimManager.key(w, x, z-1)
        };
        
        // Oyuncunun sahip olduğu tüm claim'lerin komşu olup olmadığını kontrol et
        for (ClaimManager.Claim c : claims.getAllClaims()) {
            if (!c.getOwner().equals(p.getUniqueId())) continue;
            for (String nk : neighbors) {
                if (c.getChunkKey().equals(nk)) return true;
            }
        }
        return false;
    }

    private void attemptUpgradePurchase(Player p, int dx, int dz) {
        Chunk playerChunk = p.getLocation().getChunk();
        // Eğer oyuncu bir claim içindeyse, o claim'in ana chunk'ını kullan
        ClaimManager.Claim mainClaim = claims.getMainClaimChunk(playerChunk);
        Chunk center;
        if (mainClaim != null && mainClaim.getOwner().equals(p.getUniqueId())) {
            // Ana claim chunk'ını kullan
            World w = playerChunk.getWorld();
            center = w.getChunkAt(mainClaim.getX(), mainClaim.getZ());
        } else {
            // Claim'li değilse veya sahibi değilse, bulunduğu chunk'ı kullan
            center = playerChunk;
        }
        int tx = center.getX() + dx;
        int tz = center.getZ() + dz;
        Chunk target = center.getWorld().getChunkAt(tx, tz);
        if (!isAvailableFor(p, target)) {
            p.sendMessage(messages.getComponent("messages.claim_unavailable", "<red>Bu chunk alınamaz.</red>"));
            return;
        }
        int dist = Math.abs(dx) + Math.abs(dz);
        double basePrice = upgradeBasePrice * dist;
        
        // Dinamik fiyatlandırma hesapla
        double price = calculateDynamicPrice(p.getUniqueId(), basePrice);
        
        if (Main.get().economy().withdrawPlayer(p, price).transactionSuccess()) {
            // Genişletmelerde ana claim'in ismini kullan
            String claimName = mainClaim != null ? mainClaim.getClaimName() : "Genişletme";
            boolean ok = claims.claimChunk(p, target, extendMillis, claimName);
            if (ok) {
                // Sadece güncelle çağırıyoruz, Manager artık Main chunk'ı bulup orayı güncelleyecek.
                ClaimManager.Claim newClaim = claims.getClaimAt(target);
                if (newClaim != null) {
                    Main.get().claimStones().updateOrCreateStone(newClaim);
                }
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("x", String.valueOf(tx));
                placeholders.put("z", String.valueOf(tz));
                p.sendMessage(messages.getComponent("messages.claim_acquired", "<green>Chunk alındı: ({x},{z})</green>", placeholders));
                p.sendActionBar(messages.getComponent("actionbar.claim_owned", "<yellow>Bu arazi {owner} kişisine aittir</yellow>", "owner", p.getName()));
                openUpgradeMap(p);
            } else {
                p.sendMessage(messages.getComponent("messages.neighbor_rule", "<red>Komşuluk kuralı nedeniyle chunk alınamadı.</red>"));
            }
        } else {
            p.sendMessage(messages.getComponent("messages.insufficient_funds", "<red>Yetersiz bakiye.</red>"));
        }
    }

    private int slotFromOffset(int dx, int dz) {
        int row = 2 + dz;
        int col = 4 + dx;
        if (row < 0 || row > 4 || col < 0 || col > 8) return -1;
        return row * 9 + col;
    }

    private boolean isGridSlot(int slot) {
        int row = slot / 9; int col = slot % 9;
        return row >= 0 && row <= 4 && col >= 0 && col <= 8;
    }

    private int[] offsetFromSlot(int slot) {
        int row = slot / 9; int col = slot % 9;
        if (row < 0 || row > 4 || col < 0 || col > 8) return null;
        int dz = row - 2; int dx = col - 4;
        return new int[]{dx, dz};
    }
    
    public void openMarketMenu(Player p) {
        openMarketMenu(p, 0);
    }
    
    public void openMarketMenu(Player p, int page) {
        List<ClaimMarket.MarketListing> listings = plugin.market().getAllListings();
        
        int itemsPerPage = 45;
        int totalPages = (int) Math.ceil(listings.size() / (double) itemsPerPage);
        if (totalPages < 1) totalPages = 1;
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;
        
        int size = 54; // 6 satır
        Component title = getMenuTitle("market", "<gold>Claim Marketi - Sayfa " + (page + 1) + "/" + totalPages + "</gold>");
        Inventory inv = Bukkit.createInventory(null, size, title);
        
        if (listings.isEmpty()) {
            // Boş market mesajı
            ItemStack emptyItem = createItem(Material.BARRIER, "<red>Market Boş</red>", 
                List.of("<gray>Şu anda satılık claim yok</gray>"), null, "market", "empty", null);
            inv.setItem(22, emptyItem);
        } else {
            // Sayfa için başlangıç ve bitiş indexleri
            int startIndex = page * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, listings.size());
            
            // Her claim için bir slot
            int slot = 0;
            for (int i = startIndex; i < endIndex; i++) {
                ClaimMarket.MarketListing listing = listings.get(i);
                String ownerName = plugin.getServer().getOfflinePlayer(listing.claim.getOwner()).getName();
                String claimName = listing.claim.getClaimName() != null ? listing.claim.getClaimName() : "İsimsiz Arazi";
                
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("name", claimName);
                placeholders.put("price", String.format("%.0f", listing.price));
                placeholders.put("owner", ownerName);
                placeholders.put("x", String.valueOf(listing.claim.getX()));
                placeholders.put("z", String.valueOf(listing.claim.getZ()));
                placeholders.put("world", listing.claim.getWorld());
                
                List<String> lore = List.of(
                    "<gray>─────────────────</gray>",
                    "<gray>Satıcı: <white>{owner}</white></gray>",
                    "<gray>Fiyat: <yellow>{price} TL</yellow></gray>",
                    "<gray>Konum: <white>{world} ({x}, {z})</white></gray>",
                    "<gray>─────────────────</gray>",
                    "",
                    "<green>» Tıkla ve satın al!</green>"
                );
                
                ItemStack item = createItem(Material.GRASS_BLOCK, "<yellow>" + claimName + "</yellow>", 
                    lore, placeholders, "market", "buy", String.valueOf(i));
                inv.setItem(slot++, item);
            }
            
            // Sayfalama butonları
            if (page > 0) {
                // Önceki sayfa butonu
                ItemStack prevButton = createItem(Material.ARROW, "<yellow>« Önceki Sayfa</yellow>", 
                    List.of("<gray>Sayfa " + page + "'e git</gray>"), null, "market", "prev_page", String.valueOf(page - 1));
                inv.setItem(48, prevButton);
            }
            
            if (page < totalPages - 1) {
                // Sonraki sayfa butonu
                ItemStack nextButton = createItem(Material.ARROW, "<yellow>Sonraki Sayfa »</yellow>", 
                    List.of("<gray>Sayfa " + (page + 2) + "'e git</gray>"), null, "market", "next_page", String.valueOf(page + 1));
                inv.setItem(50, nextButton);
            }
        }
        
        // Kapat butonu
        inv.setItem(49, createItem(Material.BARRIER, "<red>✖ Kapat</red>", List.of(), null, "market", "close", null));
        
        p.openInventory(inv);
    }
    
    // ==================== BANK MENU ====================
    
    private void openBank(Player p) {
        Chunk c = p.getLocation().getChunk();
        ClaimManager.Claim claim = claims.getClaimAt(c);
        if (claim == null) {
            p.sendMessage(messages.getComponent("messages.not_in_claim", "<red>Burası bir claim değil!</red>"));
            return;
        }
        
        // Only owner and managers can access bank
        if (!claim.getOwner().equals(p.getUniqueId()) && 
            !plugin.trustManager().canManage(claim, p)) {
            p.sendMessage(messages.getComponent("messages.no_permission", "<red>Claim bankasına erişim yetkiniz yok!</red>"));
            return;
        }
        
        String chunkKey = ClaimManager.getChunkKey(c);
        double balance = plugin.claimBank().getBalance(chunkKey);
        
        Inventory inv = Bukkit.createInventory(null, 27, messages.getComponent("", "<gold>💰 Claim Bankası</gold>"));
        
        // Balance display
        ItemStack balanceItem = createItem(Material.GOLD_INGOT, "<yellow>Banka Bakiyesi</yellow>",
            List.of(
                "<gray>─────────────────</gray>",
                "<gray>Mevcut Bakiye:</gray>",
                "<yellow>" + String.format("%.2f", balance) + " TL</yellow>",
                "<gray>─────────────────</gray>",
                "",
                "<gray>Bu para sadece claim</gray>",
                "<gray>sahipleri tarafından</gray>",
                "<gray>yatırılıp çekilebilir.</gray>"
            ), null, "bank", "balance_info", null);
        inv.setItem(4, balanceItem);
        
        // Deposit button
        ItemStack depositItem = createItem(Material.LIME_DYE, "<green>Para Yatır</green>",
            List.of(
                "<gray>─────────────────</gray>",
                "<gray>Claim bankasına para</gray>",
                "<gray>yatırmak için tıkla</gray>",
                "<gray>─────────────────</gray>",
                "",
                "<green>» Tıkla ve miktar gir!</green>"
            ), null, "bank", "deposit", null);
        inv.setItem(11, depositItem);
        
        // Withdraw button
        ItemStack withdrawItem = createItem(Material.RED_DYE, "<red>Para Çek</red>",
            List.of(
                "<gray>─────────────────</gray>",
                "<gray>Claim bankasından para</gray>",
                "<gray>çekmek için tıkla</gray>",
                "<gray>─────────────────</gray>",
                "",
                "<gold>» Tıkla ve miktar gir!</gold>"
            ), null, "bank", "withdraw", null);
        inv.setItem(15, withdrawItem);
        
        // Transaction history button
        ItemStack historyItem = createItem(Material.BOOK, "<aqua>İşlem Geçmişi</aqua>",
            List.of(
                "<gray>─────────────────</gray>",
                "<gray>Banka işlem geçmişini</gray>",
                "<gray>görüntülemek için tıkla</gray>",
                "<gray>─────────────────</gray>",
                "",
                "<aqua>» Tıkla ve geçmişi gör!</aqua>"
            ), null, "bank", "history", null);
        inv.setItem(13, historyItem);
        
        // Back button
        ItemStack backItem = createItem(Material.ARROW, "<gray>« Geri Dön</gray>",
            List.of("<gray>Claim menüsüne dön</gray>"), null, "bank", "back", null, 1001);
        inv.setItem(22, backItem);
        
        p.openInventory(inv);
    }
    
    private void handleBankMenuClick(Player p, String action, String data) {
        Chunk c = p.getLocation().getChunk();
        ClaimManager.Claim claim = claims.getClaimAt(c);
        if (claim == null) return;
        
        String chunkKey = ClaimManager.getChunkKey(c);
        
        switch (action) {
            case "deposit" -> {
                p.closeInventory();
                p.sendMessage(messages.getComponent("messages.bank_enter_amount", 
                    "<yellow>Yatırmak istediğiniz miktarı sohbete yazın (iptal için 'cancel'):</yellow>"));
                ChatInput.waitFor(p.getUniqueId(), input -> {
                    if (input.equalsIgnoreCase("cancel")) {
                        p.sendMessage(messages.getComponent("messages.cancelled", "<gray>İptal edildi.</gray>"));
                        return;
                    }
                    try {
                        double amount = Double.parseDouble(input);
                        plugin.claimBank().deposit(p, chunkKey, amount);
                    } catch (NumberFormatException e) {
                        p.sendMessage(messages.getComponent("messages.invalid_number", "<red>Geçersiz sayı!</red>"));
                    }
                });
            }
            case "withdraw" -> {
                p.closeInventory();
                p.sendMessage(messages.getComponent("messages.bank_enter_amount", 
                    "<yellow>Çekmek istediğiniz miktarı sohbete yazın (iptal için 'cancel'):</yellow>"));
                ChatInput.waitFor(p.getUniqueId(), input -> {
                    if (input.equalsIgnoreCase("cancel")) {
                        p.sendMessage(messages.getComponent("messages.cancelled", "<gray>İptal edildi.</gray>"));
                        return;
                    }
                    try {
                        double amount = Double.parseDouble(input);
                        plugin.claimBank().withdraw(p, chunkKey, amount);
                    } catch (NumberFormatException e) {
                        p.sendMessage(messages.getComponent("messages.invalid_number", "<red>Geçersiz sayı!</red>"));
                    }
                });
            }
            case "history" -> openBankHistory(p, 0);
            case "history_prev", "history_next" -> {
                if (data != null) {
                    try {
                        openBankHistory(p, Integer.parseInt(data));
                    } catch (NumberFormatException ignored) { }
                }
            }
            case "back_to_bank" -> openBank(p);
            case "back" -> openClaimOwnerMenu(p, claim);
            case "close" -> p.closeInventory();
        }
    }
    
    private void openBankHistory(Player p, int page) {
        Chunk c = p.getLocation().getChunk();
        ClaimManager.Claim claim = claims.getClaimAt(c);
        if (claim == null) return;
        
        String chunkKey = ClaimManager.getChunkKey(c);
        List<ClaimBank.Transaction> transactions = plugin.claimBank().getTransactions(chunkKey, 100);
        
        int itemsPerPage = 45;
        int totalPages = Math.max(1, (int) Math.ceil(transactions.size() / (double) itemsPerPage));
        page = Math.max(0, Math.min(page, totalPages - 1));
        
        Inventory inv = Bukkit.createInventory(null, 54, messages.getComponent("", 
            "<gold>💰 İşlem Geçmişi - Sayfa " + (page + 1) + "/" + totalPages + "</gold>"));
        
        if (transactions.isEmpty()) {
            ItemStack emptyItem = createItem(Material.BARRIER, "<red>Henüz işlem yok</red>",
                List.of("<gray>Bu claim'de henüz</gray>", "<gray>banka işlemi yapılmadı</gray>"), 
                null, "bank", "empty", null);
            inv.setItem(22, emptyItem);
        } else {
            int startIndex = page * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, transactions.size());
            
            int slot = 0;
            for (int i = startIndex; i < endIndex; i++) {
                ClaimBank.Transaction tx = transactions.get(i);
                String playerName = plugin.getServer().getOfflinePlayer(tx.playerUuid).getName();
                
                Material icon = tx.type.equals("DEPOSIT") ? Material.LIME_DYE : Material.RED_DYE;
                String color = tx.type.equals("DEPOSIT") ? "<green>" : "<red>";
                String symbol = tx.type.equals("DEPOSIT") ? "+" : "-";
                
                long timeAgo = (System.currentTimeMillis() - tx.timestamp) / 1000;
                String timeStr;
                if (timeAgo < 60) timeStr = timeAgo + " saniye önce";
                else if (timeAgo < 3600) timeStr = (timeAgo / 60) + " dakika önce";
                else if (timeAgo < 86400) timeStr = (timeAgo / 3600) + " saat önce";
                else timeStr = (timeAgo / 86400) + " gün önce";
                
                ItemStack item = createItem(icon, color + (tx.type.equals("DEPOSIT") ? "Yatırma" : "Çekme") + "</color>",
                    List.of(
                        "<gray>─────────────────</gray>",
                        "<gray>Oyuncu: <white>" + playerName + "</white></gray>",
                        "<gray>Miktar: <yellow>" + symbol + String.format("%.2f", tx.amount) + " TL</yellow></gray>",
                        "<gray>Önceki: <yellow>" + String.format("%.2f", tx.balanceBefore) + " TL</yellow></gray>",
                        "<gray>Sonra: <yellow>" + String.format("%.2f", tx.balanceAfter) + " TL</yellow></gray>",
                        "<gray>─────────────────</gray>",
                        "<gray>" + timeStr + "</gray>"
                    ), null, "bank", "transaction_info", null);
                
                inv.setItem(slot++, item);
            }
            
            // Pagination
            if (page > 0) {
                ItemStack prevButton = createItem(Material.ARROW, "<yellow>« Önceki Sayfa</yellow>",
                    List.of("<gray>Sayfa " + page + "'e git</gray>"), null, "bank", "history_prev", String.valueOf(page - 1));
                inv.setItem(48, prevButton);
            }
            
            if (page < totalPages - 1) {
                ItemStack nextButton = createItem(Material.ARROW, "<yellow>Sonraki Sayfa »</yellow>",
                    List.of("<gray>Sayfa " + (page + 2) + "'e git</gray>"), null, "bank", "history_next", String.valueOf(page + 1));
                inv.setItem(50, nextButton);
            }
        }
        
        // Back button
        ItemStack backItem = createItem(Material.BARRIER, "<red>✖ Geri</red>",
            List.of("<gray>Banka menüsüne dön</gray>"), null, "bank", "back_to_bank", null);
        inv.setItem(49, backItem);
        
        p.openInventory(inv);
    }
    
    // ==================== LOGS MENU ====================
    
    private void openLogs(Player p) {
        openLogs(p, 0);
    }
    
    private void openLogs(Player p, int page) {
        Chunk c = p.getLocation().getChunk();
        ClaimManager.Claim claim = claims.getClaimAt(c);
        if (claim == null) {
            p.sendMessage(messages.getComponent("messages.not_in_claim", "<red>Burası bir claim değil!</red>"));
            return;
        }
        
        // Check permissions
        if (!claim.getOwner().equals(p.getUniqueId()) && 
            !plugin.trustManager().canManage(claim, p)) {
            p.sendMessage(messages.getComponent("messages.no_permission", "<red>Logları görme yetkiniz yok!</red>"));
            return;
        }
        
        String chunkKey = ClaimManager.getChunkKey(c);
        List<ActivityLogger.LogEntry> logs = plugin.activityLogger().getRecentLogs(chunkKey, 100);
        
        int itemsPerPage = 45;
        int totalPages = Math.max(1, (int) Math.ceil(logs.size() / (double) itemsPerPage));
        page = Math.max(0, Math.min(page, totalPages - 1));
        
        Inventory inv = Bukkit.createInventory(null, 54, messages.getComponent("", 
            "<aqua>📜 Claim Kayıtları - Sayfa " + (page + 1) + "/" + totalPages + "</aqua>"));
        
        if (logs.isEmpty()) {
            ItemStack emptyItem = createItem(Material.BARRIER, "<red>Henüz kayıt yok</red>",
                List.of("<gray>Bu claim'de henüz</gray>", "<gray>kayıt tutulmaya başlanmadı</gray>"), 
                null, "logs", "empty", null);
            inv.setItem(22, emptyItem);
        } else {
            int startIndex = page * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, logs.size());
            
            int slot = 0;
            for (int i = startIndex; i < endIndex; i++) {
                ActivityLogger.LogEntry log = logs.get(i);
                String playerName = plugin.getServer().getOfflinePlayer(log.playerUuid).getName();
                
                // Icon based on action type
                Material icon = getLogIcon(log.action);
                String actionName = getLogActionName(log.action);
                
                long timeAgo = (System.currentTimeMillis() - log.timestamp) / 1000;
                String timeStr;
                if (timeAgo < 60) timeStr = timeAgo + " saniye önce";
                else if (timeAgo < 3600) timeStr = (timeAgo / 60) + " dakika önce";
                else if (timeAgo < 86400) timeStr = (timeAgo / 3600) + " saat önce";
                else timeStr = (timeAgo / 86400) + " gün önce";
                
                ItemStack item = createItem(icon, "<yellow>" + actionName + "</yellow>",
                    List.of(
                        "<gray>─────────────────</gray>",
                        "<gray>Oyuncu: <white>" + playerName + "</white></gray>",
                        "<gray>Eylem: <yellow>" + actionName + "</yellow></gray>",
                        "<gray>Detay: <white>" + (log.details != null ? log.details : "-") + "</white></gray>",
                        "<gray>─────────────────</gray>",
                        "<gray>" + timeStr + "</gray>"
                    ), null, "logs", "log_info", null);
                
                // Player head for better visualization
                if (icon == Material.PLAYER_HEAD) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta instanceof SkullMeta skullMeta) {
                        skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(log.playerUuid));
                        item.setItemMeta(skullMeta);
                    }
                }
                
                inv.setItem(slot++, item);
            }
            
            // Pagination
            if (page > 0) {
                ItemStack prevButton = createItem(Material.ARROW, "<yellow>« Önceki Sayfa</yellow>",
                    List.of("<gray>Sayfa " + page + "'e git</gray>"), null, "logs", "prev_page", String.valueOf(page - 1));
                inv.setItem(48, prevButton);
            }
            
            if (page < totalPages - 1) {
                ItemStack nextButton = createItem(Material.ARROW, "<yellow>Sonraki Sayfa »</yellow>",
                    List.of("<gray>Sayfa " + (page + 2) + "'e git</gray>"), null, "logs", "next_page", String.valueOf(page + 1));
                inv.setItem(50, nextButton);
            }
        }
        
        // Back button
        ItemStack backItem = createItem(Material.BARRIER, "<red>✖ Geri</red>",
            List.of("<gray>Claim menüsüne dön</gray>"), null, "logs", "back", null);
        inv.setItem(49, backItem);
        
        p.openInventory(inv);
    }
    
    private void handleLogsMenuClick(Player p, String action, String data) {
        switch (action) {
            case "prev_page" -> {
                if (data != null) openLogs(p, Integer.parseInt(data));
            }
            case "next_page" -> {
                if (data != null) openLogs(p, Integer.parseInt(data));
            }
            case "back" -> {
                Chunk c = p.getLocation().getChunk();
                ClaimManager.Claim claim = claims.getClaimAt(c);
                if (claim != null) openClaimOwnerMenu(p, claim);
            }
            case "close" -> p.closeInventory();
        }
    }
    
    private Material getLogIcon(String action) {
        return switch (action) {
            case "BANK_DEPOSIT", "BANK_WITHDRAW" -> Material.GOLD_INGOT;
            case "TRUST_ADDED", "TRUST_REMOVED", "TRUST_CHANGED" -> Material.PLAYER_HEAD;
            case "SETTING_CHANGED" -> Material.COMPARATOR;
            case "HOME_SET", "HOME_DELETE", "HOME_TELEPORT" -> Material.RED_BED;
            case "CLAIM_EXTENDED" -> Material.CLOCK;
            case "CLAIM_UPGRADED" -> Material.DIAMOND;
            case "MEMBER_ADDED", "MEMBER_REMOVED" -> Material.PLAYER_HEAD;
            default -> Material.PAPER;
        };
    }
    
    private String getLogActionName(String action) {
        return switch (action) {
            case "BANK_DEPOSIT" -> "Para Yatırma";
            case "BANK_WITHDRAW" -> "Para Çekme";
            case "TRUST_ADDED" -> "Güven Ekleme";
            case "TRUST_REMOVED" -> "Güven Kaldırma";
            case "TRUST_CHANGED" -> "Güven Değişimi";
            case "SETTING_CHANGED" -> "Ayar Değişikliği";
            case "HOME_SET" -> "Home Ayarlama";
            case "HOME_DELETE" -> "Home Silme";
            case "HOME_TELEPORT" -> "Home Işınlanma";
            case "CLAIM_EXTENDED" -> "Claim Uzatma";
            case "CLAIM_UPGRADED" -> "Claim Yükseltme";
            case "MEMBER_ADDED" -> "Üye Ekleme";
            case "MEMBER_REMOVED" -> "Üye Çıkarma";
            default -> action;
        };
    }
    
    // ==================== HOMES MENU ====================
    
    private void openHomesMenu(Player p) {
        Chunk c = p.getLocation().getChunk();
        ClaimManager.Claim claim = claims.getClaimAt(c);
        if (claim == null) {
            p.sendMessage(messages.getComponent("messages.not_in_claim", "<red>Burası bir claim değil!</red>"));
            return;
        }
        
        if (!claim.getOwner().equals(p.getUniqueId())) {
            p.sendMessage(messages.getComponent("messages.not_owner", "<red>Bu claim'in sahibi değilsiniz!</red>"));
            return;
        }
        
        String chunkKey = ClaimManager.getChunkKey(c);
        List<ClaimHome.HomeData> homes = plugin.claimHome().getHomes(chunkKey);
        int maxHomes = plugin.claimHome().getMaxHomesPerClaim();
        
        Inventory inv = Bukkit.createInventory(null, 54, messages.getComponent("", 
            "<green>🏠 Claim Home'ları (" + homes.size() + "/" + maxHomes + ")</green>"));
        
        // Add home button
        ItemStack addHomeItem = createItem(Material.LIME_CONCRETE, "<green>+ Yeni Home Ekle</green>",
            List.of(
                "<gray>─────────────────</gray>",
                "<gray>Mevcut konumunuza yeni</gray>",
                "<gray>bir home oluşturun</gray>",
                "<gray>─────────────────</gray>",
                "",
                "<gray>Limit: <yellow>" + homes.size() + "/" + maxHomes + "</yellow></gray>",
                "",
                "<green>» Tıkla ve isim gir!</green>"
            ), null, "homes", "add_home", null);
        inv.setItem(4, addHomeItem);
        
        // Display homes
        int slot = 9; // Start from second row
        for (ClaimHome.HomeData home : homes) {
            long daysAgo = (System.currentTimeMillis() - home.createdAt) / (24 * 60 * 60 * 1000);
            String timeStr = daysAgo == 0 ? "Bugün" : daysAgo + " gün önce";
            
            ItemStack homeItem = createItem(Material.RED_BED, "<yellow>" + home.name + "</yellow>",
                List.of(
                    "<gray>─────────────────</gray>",
                    "<gray>Konum:</gray>",
                    "<white>X: " + String.format("%.1f", home.location.getX()) + "</white>",
                    "<white>Y: " + String.format("%.1f", home.location.getY()) + "</white>",
                    "<white>Z: " + String.format("%.1f", home.location.getZ()) + "</white>",
                    "<gray>─────────────────</gray>",
                    "<gray>Oluşturulma: " + timeStr + "</gray>",
                    "",
                    "<green>» Sol tık: Işınlan</green>",
                    "<red>» Shift+Sağ tık: Sil</red>"
                ), null, "homes", "teleport", home.name);
            
            inv.setItem(slot++, homeItem);
        }
        
        // Back button
        ItemStack backItem = createItem(Material.ARROW, "<gray>« Geri Dön</gray>",
            List.of("<gray>Claim menüsüne dön</gray>"), null, "homes", "back", null, 1001);
        inv.setItem(49, backItem);
        
        p.openInventory(inv);
    }
    
    private void handleHomesMenuClick(Player p, String action, String data) {
        Chunk c = p.getLocation().getChunk();
        ClaimManager.Claim claim = claims.getClaimAt(c);
        if (claim == null) return;
        
        String chunkKey = ClaimManager.getChunkKey(c);
        
        switch (action) {
            case "add_home" -> {
                int currentHomes = plugin.claimHome().getHomes(chunkKey).size();
                int maxHomes = plugin.claimHome().getMaxHomesPerClaim();
                
                if (currentHomes >= maxHomes) {
                    p.sendMessage(messages.getComponent("messages.max_homes_reached",
                        "<red>Maksimum home sayısına ulaştınız! ({max})</red>",
                        Map.of("max", String.valueOf(maxHomes))));
                    p.closeInventory();
                    return;
                }
                
                p.closeInventory();
                p.sendMessage(messages.getComponent("messages.enter_home_name",
                    "<yellow>Home için bir isim yazın (iptal için 'cancel'):</yellow>"));
                ChatInput.waitFor(p.getUniqueId(), input -> {
                    if (input.equalsIgnoreCase("cancel")) {
                        p.sendMessage(messages.getComponent("messages.cancelled", "<gray>İptal edildi.</gray>"));
                        return;
                    }
                    if (input.length() > 16) {
                        p.sendMessage(messages.getComponent("messages.name_too_long", "<red>İsim çok uzun! (Max 16 karakter)</red>"));
                        return;
                    }
                    if (plugin.claimHome().setHome(p, chunkKey, input)) {
                        Map<String, String> placeholders = new HashMap<>();
                        placeholders.put("name", input);
                        p.sendMessage(messages.getComponent("messages.home_set",
                            "<green>✓ '{name}' home'u oluşturuldu!</green>", placeholders));
                    } else {
                        p.sendMessage(messages.getComponent("messages.home_error", "<red>Home oluşturulamadı!</red>"));
                    }
                });
            }
            case "teleport" -> {
                if (data != null) {
                    p.closeInventory();
                    plugin.claimHome().teleportHome(p, chunkKey, data);
                }
            }
            case "delete" -> {
                if (data != null) {
                    if (plugin.claimHome().deleteHome(p, chunkKey, data)) {
                        Map<String, String> placeholders = new HashMap<>();
                        placeholders.put("name", data);
                        p.sendMessage(messages.getComponent("messages.home_deleted",
                            "<red>✓ '{name}' home'u silindi!</red>", placeholders));
                        openHomesMenu(p); // Refresh menu
                    }
                }
            }
            case "back" -> openClaimOwnerMenu(p, claim);
            case "close" -> p.closeInventory();
        }
    }
}
