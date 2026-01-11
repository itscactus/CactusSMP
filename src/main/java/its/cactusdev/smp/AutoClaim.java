package its.cactusdev.smp;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AutoClaim implements Listener {
    private final Main plugin;
    private final Set<UUID> autoClaimEnabled = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> lastChunk = new ConcurrentHashMap<>();
    
    private final boolean enabled;
    private final double radiusDiscount;
    
    public AutoClaim(Main plugin) {
        this.plugin = plugin;
        
        plugin.getConfig().addDefault("auto_claim.enabled", true);
        plugin.getConfig().addDefault("auto_claim.radius_discount_multiplier", 0.9);
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
        
        this.enabled = plugin.getConfig().getBoolean("auto_claim.enabled", true);
        this.radiusDiscount = plugin.getConfig().getDouble("auto_claim.radius_discount_multiplier", 0.9);
        
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    
    public boolean toggle(UUID playerId) {
        if (!enabled) return false;
        
        if (autoClaimEnabled.contains(playerId)) {
            autoClaimEnabled.remove(playerId);
            return false;
        } else {
            autoClaimEnabled.add(playerId);
            return true;
        }
    }
    
    public boolean isEnabled(UUID playerId) {
        return autoClaimEnabled.contains(playerId);
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!enabled) return;
        
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        if (!autoClaimEnabled.contains(playerId)) return;
        
        Location to = event.getTo();
        if (to == null) return;
        
        Chunk chunk = to.getChunk();
        String chunkKey = ClaimManager.getChunkKey(chunk);
        
        // Aynı chunk'taysa skip
        if (chunkKey.equals(lastChunk.get(playerId))) return;
        lastChunk.put(playerId, chunkKey);
        
        // Zaten claim edilmişse skip
        if (plugin.claims().getClaim(chunkKey) != null) return;
        
        // Komşu chunk'ları kontrol et (en az biri kendisine ait olmalı)
        boolean hasAdjacentOwned = false;
        int[] dx = {-1, 1, 0, 0};
        int[] dz = {0, 0, -1, 1};
        
        for (int i = 0; i < 4; i++) {
            String neighborKey = chunk.getWorld().getName() + ":" + 
                                (chunk.getX() + dx[i]) + ":" + 
                                (chunk.getZ() + dz[i]);
            ClaimManager.Claim neighborClaim = plugin.claims().getClaim(neighborKey);
            if (neighborClaim != null && neighborClaim.getOwner().equals(playerId)) {
                hasAdjacentOwned = true;
                break;
            }
        }
        
        if (!hasAdjacentOwned) {
            // İlk claim ise normal fiyat
            double price = plugin.getConfig().getDouble("claim.price", 1000.0);
            if (!plugin.economy().has(player, price)) return;
            
            if (!plugin.economy().withdrawPlayer(player, price).transactionSuccess()) return;
            
            long duration = plugin.getConfig().getLong("claim.duration_days", 30) * 24L * 60 * 60 * 1000;
            plugin.claims().claimChunk(player, chunk, duration, "Auto Claim");
            player.sendMessage(plugin.messages().getComponent("messages.auto_claim_success",
                "<green>✓ Chunk otomatik claim edildi! (-{price})</green>")
                .replaceText(b -> b.matchLiteral("{price}").replacement(String.valueOf(price))));
            
            return;
        }
        
        // Komşu varsa genişletme
        double price = plugin.getConfig().getDouble("claim.expansion_price", 500.0);
        if (!plugin.economy().has(player, price)) {
            // Para yoksa auto-claim'i kapat
            autoClaimEnabled.remove(playerId);
            player.sendMessage(plugin.messages().getComponent("messages.auto_claim_disabled_no_money",
                "<red>Auto-claim kapatıldı: Yetersiz bakiye!</red>"));
            return;
        }
        
        if (!plugin.economy().withdrawPlayer(player, price).transactionSuccess()) return;
        
        long duration = plugin.getConfig().getLong("claim.duration_days", 30) * 24L * 60 * 60 * 1000;
        plugin.claims().claimChunk(player, chunk, duration, null); // Aynı claim'e ekle
        player.sendMessage(plugin.messages().getComponent("messages.auto_claim_expansion",
            "<green>✓ Claim genişletildi! (-{price})</green>")
            .replaceText(b -> b.matchLiteral("{price}").replacement(String.valueOf(price))));
    }
    
    public boolean claimRadius(Player player, int radius) {
        if (!enabled) return false;
        if (radius < 1 || radius > 5) return false;
        
        Chunk center = player.getLocation().getChunk();
        List<Chunk> chunks = new ArrayList<>();
        
        // Radius içindeki chunk'ları topla
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                Chunk chunk = center.getWorld().getChunkAt(center.getX() + x, center.getZ() + z);
                String chunkKey = ClaimManager.getChunkKey(chunk);
                
                // Zaten claim edilmemişse ekle
                if (plugin.claims().getClaim(chunkKey) == null) {
                    chunks.add(chunk);
                }
            }
        }
        
        if (chunks.isEmpty()) {
            player.sendMessage(plugin.messages().getComponent("messages.radius_claim_none",
                "<yellow>Tüm chunk'lar zaten claim edilmiş!</yellow>"));
            return false;
        }
        
        // Toplam fiyat hesapla (indirimli)
        double basePrice = plugin.getConfig().getDouble("claim.price", 1000.0);
        double totalPrice = basePrice * chunks.size() * radiusDiscount;
        
        if (!plugin.economy().has(player, totalPrice)) {
            player.sendMessage(plugin.messages().getComponent("messages.radius_claim_insufficient",
                "<red>Yetersiz bakiye! Gerekli: {price}</red>")
                .replaceText(b -> b.matchLiteral("{price}").replacement(String.valueOf(totalPrice))));
            return false;
        }
        
        // Parayı çek
        if (!plugin.economy().withdrawPlayer(player, totalPrice).transactionSuccess()) {
            return false;
        }
        
        // Chunk'ları claim et
        String claimName = "Radius Claim";
        long duration = plugin.getConfig().getLong("claim.duration_days", 30) * 24L * 60 * 60 * 1000;
        for (Chunk chunk : chunks) {
            plugin.claims().claimChunk(player, chunk, duration, claimName);
        }
        
        player.sendMessage(plugin.messages().getComponent("messages.radius_claim_success",
            "<green>✓ {count} chunk claim edildi! (-{price})</green>")
            .replaceText(b -> b.matchLiteral("{count}").replacement(String.valueOf(chunks.size())))
            .replaceText(b -> b.matchLiteral("{price}").replacement(String.valueOf(totalPrice))));
        
        return true;
    }
}
