package its.cactusdev.smp.features;

import its.cactusdev.smp.Main;
import its.cactusdev.smp.data.Database;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClaimHome {
    private final Main plugin;
    private final Database db;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final int cooldownSeconds;
    private final int warmupSeconds;
    private final boolean enabled;
    private final int maxHomesPerClaim;

    public static class HomeData {
        public final String name;
        public final Location location;
        public final long createdAt;
        
        public HomeData(String name, Location location, long createdAt) {
            this.name = name;
            this.location = location;
            this.createdAt = createdAt;
        }
    }

    public ClaimHome(Main plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
        
        plugin.getConfig().addDefault("claim_home.enabled", true);
        plugin.getConfig().addDefault("claim_home.cooldown_seconds", 60);
        plugin.getConfig().addDefault("claim_home.warmup_seconds", 3);
        plugin.getConfig().addDefault("claim_home.max_homes_per_claim", 5);
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
        
        this.enabled = plugin.getConfig().getBoolean("claim_home.enabled", true);
        this.cooldownSeconds = plugin.getConfig().getInt("claim_home.cooldown_seconds", 60);
        this.warmupSeconds = plugin.getConfig().getInt("claim_home.warmup_seconds", 3);
        this.maxHomesPerClaim = plugin.getConfig().getInt("claim_home.max_homes_per_claim", 5);
    }

    /**
     * Set a named home in a claim
     */
    public boolean setHome(Player player, String chunkKey, String homeName) {
        if (!enabled) return false;
        
        // Check if max homes reached
        List<HomeData> homes = getHomes(chunkKey);
        if (homes.size() >= maxHomesPerClaim && !hasHome(chunkKey, homeName)) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("max", String.valueOf(maxHomesPerClaim));
            player.sendMessage(plugin.messages().getComponent("messages.max_homes_reached", 
                "<red>Maksimum {max} home oluşturabilirsiniz!</red>", placeholders));
            return false;
        }
        
        Location loc = player.getLocation();
        String sql = "INSERT INTO claim_multiple_homes (chunk_key, home_name, world, x, y, z, yaw, pitch, created_at) " +
                    "VALUES (?,?,?,?,?,?,?,?,?) " +
                    "ON CONFLICT(chunk_key, home_name) DO UPDATE SET world=?, x=?, y=?, z=?, yaw=?, pitch=?";
        
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            long now = System.currentTimeMillis();
            ps.setString(1, chunkKey);
            ps.setString(2, homeName);
            ps.setString(3, loc.getWorld().getName());
            ps.setDouble(4, loc.getX());
            ps.setDouble(5, loc.getY());
            ps.setDouble(6, loc.getZ());
            ps.setFloat(7, loc.getYaw());
            ps.setFloat(8, loc.getPitch());
            ps.setLong(9, now);
            // For UPDATE part
            ps.setString(10, loc.getWorld().getName());
            ps.setDouble(11, loc.getX());
            ps.setDouble(12, loc.getY());
            ps.setDouble(13, loc.getZ());
            ps.setFloat(14, loc.getYaw());
            ps.setFloat(15, loc.getPitch());
            ps.executeUpdate();
            
            // Log activity
            plugin.getActivityLogger().log(chunkKey, player.getUniqueId(), "HOME_SET", 
                "Home '" + homeName + "' ayarlandı");
            
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Legacy single home support (uses "default" name)
     */
    @Deprecated
    public boolean setHome(Player player, String chunkKey) {
        return setHome(player, chunkKey, "default");
    }

    /**
     * Get all homes in a claim
     */
    public List<HomeData> getHomes(String chunkKey) {
        List<HomeData> homes = new ArrayList<>();
        String sql = "SELECT home_name, world, x, y, z, yaw, pitch, created_at FROM claim_multiple_homes WHERE chunk_key = ? ORDER BY created_at ASC";
        
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, chunkKey);
            ResultSet rs = ps.executeQuery();
            
            while (rs.next()) {
                String name = rs.getString("home_name");
                String world = rs.getString("world");
                double x = rs.getDouble("x");
                double y = rs.getDouble("y");
                double z = rs.getDouble("z");
                float yaw = rs.getFloat("yaw");
                float pitch = rs.getFloat("pitch");
                long createdAt = rs.getLong("created_at");
                
                Location loc = new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
                homes.add(new HomeData(name, loc, createdAt));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return homes;
    }

    /**
     * Get a specific named home
     */
    public Location getHome(String chunkKey, String homeName) {
        String sql = "SELECT world, x, y, z, yaw, pitch FROM claim_multiple_homes WHERE chunk_key = ? AND home_name = ?";
        
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, chunkKey);
            ps.setString(2, homeName);
            ResultSet rs = ps.executeQuery();
            
            if (rs.next()) {
                String world = rs.getString("world");
                double x = rs.getDouble("x");
                double y = rs.getDouble("y");
                double z = rs.getDouble("z");
                float yaw = rs.getFloat("yaw");
                float pitch = rs.getFloat("pitch");
                
                return new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Legacy support - get "default" home
     */
    @Deprecated
    public Location getHome(String chunkKey) {
        return getHome(chunkKey, "default");
    }

    /**
     * Teleport to a named home
     */
    public void teleportHome(Player player, String chunkKey, String homeName) {
        if (!enabled) {
            player.sendMessage(plugin.messages().getComponent("messages.home_disabled", "<red>Home sistemi devre dışı!</red>"));
            return;
        }
        
        // Cooldown kontrolü
        long now = System.currentTimeMillis();
        Long lastUse = cooldowns.get(player.getUniqueId());
        if (lastUse != null && (now - lastUse) < cooldownSeconds * 1000L) {
            long remaining = (cooldownSeconds * 1000L - (now - lastUse)) / 1000;
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("seconds", String.valueOf(remaining));
            player.sendMessage(plugin.messages().getComponent("messages.home_cooldown", "<red>Cooldown: {seconds}s</red>", placeholders));
            return;
        }
        
        // Home lokasyonunu al
        Location home = getHome(chunkKey, homeName);
        if (home == null) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("name", homeName);
            player.sendMessage(plugin.messages().getComponent("messages.no_home_set", 
                "<red>'{name}' adlı home bulunamadı!</red>", placeholders));
            return;
        }
        
        // Warmup
        if (warmupSeconds > 0) {
            Location startLoc = player.getLocation().clone();
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("seconds", String.valueOf(warmupSeconds));
            player.sendMessage(plugin.messages().getComponent("messages.home_warmup", 
                "<green>Işınlanıyorsunuz... Hareket etmeyin! ({seconds}s)</green>", placeholders));
            
            new BukkitRunnable() {
                int countdown = warmupSeconds;
                
                @Override
                public void run() {
                    if (!player.isOnline()) {
                        cancel();
                        return;
                    }
                    
                    // Hareket kontrolü
                    if (player.getLocation().distance(startLoc) > 0.5) {
                        player.sendMessage(plugin.messages().getComponent("messages.home_cancelled", "<red>Hareket ettiniz, ışınlanma iptal edildi!</red>"));
                        cancel();
                        return;
                    }
                    
                    countdown--;
                    if (countdown > 0) {
                        player.sendActionBar(plugin.messages().getComponent("", "<yellow>Işınlanma: " + countdown + "s</yellow>"));
                    } else {
                        player.teleport(home);
                        Map<String, String> placeholders = new HashMap<>();
                        placeholders.put("name", homeName);
                        player.sendMessage(plugin.messages().getComponent("messages.home_success", 
                            "<green>✓ '{name}' home'una ışınlandınız!</green>", placeholders));
                        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
                        
                        // Log activity
                        plugin.getActivityLogger().log(chunkKey, player.getUniqueId(), "HOME_TELEPORT", 
                            "Home '" + homeName + "' ışınlandı");
                        
                        cancel();
                    }
                }
            }.runTaskTimer(plugin, 0L, 20L);
        } else {
            player.teleport(home);
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("name", homeName);
            player.sendMessage(plugin.messages().getComponent("messages.home_success", 
                "<green>✓ '{name}' home'una ışınlandınız!</green>", placeholders));
            cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
            
            // Log activity
            plugin.getActivityLogger().log(chunkKey, player.getUniqueId(), "HOME_TELEPORT", 
                "Home '" + homeName + "' ışınlandı");
        }
    }

    /**
     * Legacy support - teleport to "default" home
     */
    @Deprecated
    public void teleportHome(Player player, String chunkKey) {
        teleportHome(player, chunkKey, "default");
    }

    /**
     * Check if a specific home exists
     */
    public boolean hasHome(String chunkKey, String homeName) {
        return getHome(chunkKey, homeName) != null;
    }

    /**
     * Legacy support - check if "default" home exists
     */
    @Deprecated
    public boolean hasHome(String chunkKey) {
        return hasHome(chunkKey, "default");
    }

    /**
     * Delete a specific home
     */
    public boolean deleteHome(Player player, String chunkKey, String homeName) {
        String sql = "DELETE FROM claim_multiple_homes WHERE chunk_key = ? AND home_name = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, chunkKey);
            ps.setString(2, homeName);
            int deleted = ps.executeUpdate();
            
            if (deleted > 0 && player != null) {
                // Log activity
                plugin.getActivityLogger().log(chunkKey, player.getUniqueId(), "HOME_DELETE", 
                    "Home '" + homeName + "' silindi");
            }
            
            return deleted > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Delete all homes in a claim
     */
    public void deleteHome(String chunkKey) {
        String sql = "DELETE FROM claim_multiple_homes WHERE chunk_key = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, chunkKey);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    public int getMaxHomesPerClaim() {
        return maxHomesPerClaim;
    }
}
