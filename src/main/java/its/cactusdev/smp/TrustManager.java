package its.cactusdev.smp;

import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class TrustManager {
    private final Main plugin;
    private final Database db;
    
    public enum TrustLevel {
        VISITOR,    // Sadece dolaşabilir
        BUILDER,    // Blok kırıp koyabilir
        MANAGER     // Ayarları değiştirebilir (üye ekleyemez)
    }
    
    public TrustManager(Main plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }
    
    public boolean setTrust(String chunkKey, UUID playerUuid, TrustLevel level) {
        String sql = "INSERT OR REPLACE INTO trust_levels (chunk_key, player_uuid, trust_level, granted_at) VALUES (?,?,?,?)";
        
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, chunkKey);
            ps.setString(2, playerUuid.toString());
            ps.setString(3, level.name());
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public boolean removeTrust(String chunkKey, UUID playerUuid) {
        String sql = "DELETE FROM trust_levels WHERE chunk_key = ? AND player_uuid = ?";
        
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, chunkKey);
            ps.setString(2, playerUuid.toString());
            int affected = ps.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public TrustLevel getTrustLevel(String chunkKey, UUID playerUuid) {
        String sql = "SELECT trust_level FROM trust_levels WHERE chunk_key = ? AND player_uuid = ?";
        
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, chunkKey);
            ps.setString(2, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return TrustLevel.valueOf(rs.getString("trust_level"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    public Map<UUID, TrustLevel> getAllTrusted(String chunkKey) {
        Map<UUID, TrustLevel> result = new HashMap<>();
        String sql = "SELECT player_uuid, trust_level FROM trust_levels WHERE chunk_key = ?";
        
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, chunkKey);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                    TrustLevel level = TrustLevel.valueOf(rs.getString("trust_level"));
                    result.put(uuid, level);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }
    
    public boolean canBuild(ClaimManager.Claim claim, Player player) {
        // Sahibi ise her şeyi yapabilir
        if (claim.getOwner().equals(player.getUniqueId())) return true;
        
        // Eski member sistemi (geriye uyumluluk)
        if (claim.getMembers().contains(player.getUniqueId())) return true;
        
        // Yeni trust sistemi
        TrustLevel level = getTrustLevel(claim.getChunkKey(), player.getUniqueId());
        return level == TrustLevel.BUILDER || level == TrustLevel.MANAGER;
    }
    
    public boolean canManage(ClaimManager.Claim claim, Player player) {
        // Sadece sahip ve MANAGER seviyesindekiler yönetebilir
        if (claim.getOwner().equals(player.getUniqueId())) return true;
        
        TrustLevel level = getTrustLevel(claim.getChunkKey(), player.getUniqueId());
        return level == TrustLevel.MANAGER;
    }
    
    public boolean canVisit(ClaimManager.Claim claim, Player player) {
        // Herkes ziyaret edebilir
        return true;
    }
}
