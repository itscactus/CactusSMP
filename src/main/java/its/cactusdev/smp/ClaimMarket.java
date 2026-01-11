package its.cactusdev.smp;

import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class ClaimMarket {
    private final Main plugin;
    private final Database db;
    private final boolean enabled;
    private final double listingFee;
    
    public static class MarketListing {
        public final String chunkKey;
        public final double price;
        public final long listedAt;
        public final ClaimManager.Claim claim;
        
        public MarketListing(String chunkKey, double price, long listedAt, ClaimManager.Claim claim) {
            this.chunkKey = chunkKey;
            this.price = price;
            this.listedAt = listedAt;
            this.claim = claim;
        }
    }
    
    public ClaimMarket(Main plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
        
        plugin.getConfig().addDefault("claim_market.enabled", true);
        plugin.getConfig().addDefault("claim_market.listing_fee_percent", 5.0);
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
        
        this.enabled = plugin.getConfig().getBoolean("claim_market.enabled", true);
        this.listingFee = plugin.getConfig().getDouble("claim_market.listing_fee_percent", 5.0);
    }
    
    public boolean listClaim(String chunkKey, double price) {
        if (!enabled) return false;
        
        String sql = "INSERT OR REPLACE INTO claim_market (chunk_key, price, listed_at) VALUES (?,?,?)";
        
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, chunkKey);
            ps.setDouble(2, price);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean unlistClaim(String chunkKey) {
        String sql = "DELETE FROM claim_market WHERE chunk_key = ?";
        
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, chunkKey);
            int affected = ps.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public MarketListing getListing(String chunkKey) {
        String sql = "SELECT chunk_key, price, listed_at FROM claim_market WHERE chunk_key = ?";
        
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, chunkKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ClaimManager.Claim claim = plugin.claims().getAllClaims().stream()
                        .filter(c -> c.getChunkKey().equals(chunkKey))
                        .findFirst().orElse(null);
                    
                    if (claim != null) {
                        return new MarketListing(
                            rs.getString("chunk_key"),
                            rs.getDouble("price"),
                            rs.getLong("listed_at"),
                            claim
                        );
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    public List<MarketListing> getAllListings() {
        List<MarketListing> result = new ArrayList<>();
        String sql = "SELECT chunk_key, price, listed_at FROM claim_market ORDER BY listed_at DESC";
        
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String chunkKey = rs.getString("chunk_key");
                ClaimManager.Claim claim = plugin.claims().getAllClaims().stream()
                    .filter(c -> c.getChunkKey().equals(chunkKey))
                    .findFirst().orElse(null);
                
                if (claim != null) {
                    result.add(new MarketListing(
                        chunkKey,
                        rs.getDouble("price"),
                        rs.getLong("listed_at"),
                        claim
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }
    
    public boolean buyClaim(Player buyer, MarketListing listing) {
        if (!enabled) return false;
        
        ClaimManager.Claim claim = listing.claim;
        
        // Kendisinden alamaz
        if (claim.getOwner().equals(buyer.getUniqueId())) {
            buyer.sendMessage(plugin.messages().getComponent("messages.cannot_buy_own", "<red>Kendi claim'inizi satın alamazsınız!</red>"));
            return false;
        }
        
        // Para kontrolü
        if (!plugin.economy().has(buyer, listing.price)) {
            buyer.sendMessage(plugin.messages().getComponent("messages.insufficient_funds", "<red>Yetersiz bakiye!</red>"));
            return false;
        }
        
        // Parayı çek
        if (!plugin.economy().withdrawPlayer(buyer, listing.price).transactionSuccess()) {
            return false;
        }
        
        // Satıcıya parayı ver (komisyon düş)
        double sellerAmount = listing.price * (1.0 - listingFee / 100.0);
        plugin.economy().depositPlayer(plugin.getServer().getOfflinePlayer(claim.getOwner()), sellerAmount);
        
        // Sahipliği değiştir (bu kısmı ClaimManager'da implement etmek gerekecek)
        // Şimdilik basit bir yaklaşım
        
        // Listeden kaldır
        unlistClaim(listing.chunkKey);
        
        buyer.sendMessage(plugin.messages().getComponent("messages.claim_bought", "<green>✓ Claim satın alındı!</green>"));
        
        return true;
    }
    
    public boolean isListed(String chunkKey) {
        return getListing(chunkKey) != null;
    }
}
