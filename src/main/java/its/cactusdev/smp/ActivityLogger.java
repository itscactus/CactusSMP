package its.cactusdev.smp;

import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class ActivityLogger {
    private final Main plugin;
    private final Database db;
    private final boolean enabled;
    private final int retentionDays;
    
    public static class LogEntry {
        public final int id;
        public final String chunkKey;
        public final UUID playerUuid;
        public final String action;
        public final String details;
        public final long timestamp;
        
        public LogEntry(int id, String chunkKey, UUID playerUuid, String action, String details, long timestamp) {
            this.id = id;
            this.chunkKey = chunkKey;
            this.playerUuid = playerUuid;
            this.action = action;
            this.details = details;
            this.timestamp = timestamp;
        }
    }
    
    public ActivityLogger(Main plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
        
        plugin.getConfig().addDefault("activity_log.enabled", true);
        plugin.getConfig().addDefault("activity_log.retention_days", 30);
        plugin.getConfig().addDefault("activity_log.log_break", true);
        plugin.getConfig().addDefault("activity_log.log_place", true);
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
        
        this.enabled = plugin.getConfig().getBoolean("activity_log.enabled", true);
        this.retentionDays = plugin.getConfig().getInt("activity_log.retention_days", 30);
        
        // Cleanup task - her gün bir kez
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::cleanupOldLogs, 20L * 60 * 60, 20L * 60 * 60 * 24);
    }
    
    public void logAction(String chunkKey, UUID playerUuid, String action, String details) {
        if (!enabled) return;
        
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT INTO activity_logs (chunk_key, player_uuid, action, details, timestamp) VALUES (?,?,?,?,?)";
            
            try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
                ps.setString(1, chunkKey);
                ps.setString(2, playerUuid.toString());
                ps.setString(3, action);
                ps.setString(4, details);
                ps.setLong(5, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }
    
    /**
     * Simplified log method
     */
    public void log(String chunkKey, UUID playerUuid, String action, String details) {
        logAction(chunkKey, playerUuid, action, details);
    }
    
    public void logBlockBreak(Chunk chunk, Player player, Block block) {
        if (!enabled || !plugin.getConfig().getBoolean("activity_log.log_break", true)) return;
        
        String chunkKey = ClaimManager.getChunkKey(chunk);
        String details = String.format("Broke %s at %d,%d,%d", 
            block.getType().name(),
            block.getX(),
            block.getY(),
            block.getZ()
        );
        
        logAction(chunkKey, player.getUniqueId(), "BLOCK_BREAK", details);
    }
    
    public void logBlockPlace(Chunk chunk, Player player, Block block) {
        if (!enabled || !plugin.getConfig().getBoolean("activity_log.log_place", true)) return;
        
        String chunkKey = ClaimManager.getChunkKey(chunk);
        String details = String.format("Placed %s at %d,%d,%d",
            block.getType().name(),
            block.getX(),
            block.getY(),
            block.getZ()
        );
        
        logAction(chunkKey, player.getUniqueId(), "BLOCK_PLACE", details);
    }
    
    public List<LogEntry> getRecentLogs(String chunkKey, int limit) {
        List<LogEntry> result = new ArrayList<>();
        String sql = "SELECT id, chunk_key, player_uuid, action, details, timestamp " +
                    "FROM activity_logs WHERE chunk_key = ? " +
                    "ORDER BY timestamp DESC LIMIT ?";
        
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, chunkKey);
            ps.setInt(2, limit);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new LogEntry(
                        rs.getInt("id"),
                        rs.getString("chunk_key"),
                        UUID.fromString(rs.getString("player_uuid")),
                        rs.getString("action"),
                        rs.getString("details"),
                        rs.getLong("timestamp")
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return result;
    }
    
    public List<LogEntry> getPlayerLogs(UUID playerUuid, int limit) {
        List<LogEntry> result = new ArrayList<>();
        String sql = "SELECT id, chunk_key, player_uuid, action, details, timestamp " +
                    "FROM activity_logs WHERE player_uuid = ? " +
                    "ORDER BY timestamp DESC LIMIT ?";
        
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setInt(2, limit);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new LogEntry(
                        rs.getInt("id"),
                        rs.getString("chunk_key"),
                        UUID.fromString(rs.getString("player_uuid")),
                        rs.getString("action"),
                        rs.getString("details"),
                        rs.getLong("timestamp")
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return result;
    }
    
    private void cleanupOldLogs() {
        long cutoffTime = System.currentTimeMillis() - (retentionDays * 24L * 60 * 60 * 1000);
        String sql = "DELETE FROM activity_logs WHERE timestamp < ?";
        
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setLong(1, cutoffTime);
            int deleted = ps.executeUpdate();
            if (deleted > 0) {
                plugin.getLogger().info("Cleaned up " + deleted + " old log entries");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    public Map<String, Integer> getActionStats(String chunkKey) {
        Map<String, Integer> stats = new HashMap<>();
        String sql = "SELECT action, COUNT(*) as count FROM activity_logs " +
                    "WHERE chunk_key = ? GROUP BY action";
        
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, chunkKey);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    stats.put(rs.getString("action"), rs.getInt("count"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return stats;
    }
}
