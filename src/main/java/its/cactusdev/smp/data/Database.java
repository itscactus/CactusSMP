package its.cactusdev.smp.data;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class Database {
    private final JavaPlugin plugin;
    private Connection connection;

    public Database(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        try {
            if (!plugin.getDataFolder().exists()) {
                //noinspection ResultOfMethodCallIgnored
                plugin.getDataFolder().mkdirs();
            }
            File dbFile = new File(plugin.getDataFolder(), "claims.db");
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            connection = DriverManager.getConnection(url);
            connection.setAutoCommit(true);
            createTables();
        } catch (SQLException e) {
            throw new RuntimeException("SQLite bağlantısı kurulamadı", e);
        }
    }

    private void createTables() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS claims (" +
                    "chunk_key TEXT PRIMARY KEY," +
                    "owner_uuid TEXT NOT NULL," +
                    "world TEXT NOT NULL," +
                    "x INTEGER NOT NULL," +
                    "z INTEGER NOT NULL," +
                    "expires_at INTEGER NOT NULL," +
                    "allow_explosions INTEGER NOT NULL DEFAULT 0," +
                    "allow_pvp INTEGER NOT NULL DEFAULT 0," +
                    "allow_interact INTEGER NOT NULL DEFAULT 0," +
                    "allow_open_doors INTEGER NOT NULL DEFAULT 1," +
                    "created_at INTEGER NOT NULL," +
                    "claim_name TEXT DEFAULT 'Adlandırılmamış Arazi'" +
                    ")");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_claims_owner ON claims(owner_uuid)");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS members (" +
                    "chunk_key TEXT NOT NULL," +
                    "member_uuid TEXT NOT NULL," +
                    "UNIQUE(chunk_key, member_uuid)" +
                    ")");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_members_uuid ON members(member_uuid)");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS settings (" +
                    "key TEXT PRIMARY KEY," +
                    "value TEXT" +
                    ")");

            // Claim Home sistemi
            st.executeUpdate("CREATE TABLE IF NOT EXISTS claim_homes (" +
                    "chunk_key TEXT PRIMARY KEY," +
                    "world TEXT NOT NULL," +
                    "x REAL NOT NULL," +
                    "y REAL NOT NULL," +
                    "z REAL NOT NULL," +
                    "yaw REAL NOT NULL," +
                    "pitch REAL NOT NULL," +
                    "FOREIGN KEY(chunk_key) REFERENCES claims(chunk_key) ON DELETE CASCADE" +
                    ")");

            // Trust/Permission sistemi
            st.executeUpdate("CREATE TABLE IF NOT EXISTS trust_levels (" +
                    "chunk_key TEXT NOT NULL," +
                    "player_uuid TEXT NOT NULL," +
                    "trust_level TEXT NOT NULL," +
                    "granted_at INTEGER NOT NULL," +
                    "UNIQUE(chunk_key, player_uuid)" +
                    ")");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_trust_player ON trust_levels(player_uuid)");

            // Claim Market sistemi
            st.executeUpdate("CREATE TABLE IF NOT EXISTS claim_market (" +
                    "chunk_key TEXT PRIMARY KEY," +
                    "price REAL NOT NULL," +
                    "listed_at INTEGER NOT NULL," +
                    "FOREIGN KEY(chunk_key) REFERENCES claims(chunk_key) ON DELETE CASCADE" +
                    ")");

            // Activity Log sistemi
            st.executeUpdate("CREATE TABLE IF NOT EXISTS activity_logs (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "chunk_key TEXT NOT NULL," +
                    "player_uuid TEXT NOT NULL," +
                    "action TEXT NOT NULL," +
                    "details TEXT," +
                    "timestamp INTEGER NOT NULL" +
                    ")");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_logs_chunk ON activity_logs(chunk_key)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_logs_time ON activity_logs(timestamp)");

            // Claim Bank sistemi
            st.executeUpdate("CREATE TABLE IF NOT EXISTS claim_banks (" +
                    "chunk_key TEXT PRIMARY KEY," +
                    "balance REAL NOT NULL DEFAULT 0," +
                    "last_transaction INTEGER," +
                    "FOREIGN KEY(chunk_key) REFERENCES claims(chunk_key) ON DELETE CASCADE" +
                    ")");

            // Bank Transaction History
            st.executeUpdate("CREATE TABLE IF NOT EXISTS claim_bank_transactions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "chunk_key TEXT NOT NULL," +
                    "player_uuid TEXT NOT NULL," +
                    "type TEXT NOT NULL," +
                    "amount REAL NOT NULL," +
                    "balance_before REAL NOT NULL," +
                    "balance_after REAL NOT NULL," +
                    "timestamp INTEGER NOT NULL" +
                    ")");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_bank_trans_chunk ON claim_bank_transactions(chunk_key)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_bank_trans_time ON claim_bank_transactions(timestamp)");

            // Multiple Homes sistemi
            st.executeUpdate("CREATE TABLE IF NOT EXISTS claim_multiple_homes (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "chunk_key TEXT NOT NULL," +
                    "home_name TEXT NOT NULL," +
                    "world TEXT NOT NULL," +
                    "x REAL NOT NULL," +
                    "y REAL NOT NULL," +
                    "z REAL NOT NULL," +
                    "yaw REAL NOT NULL," +
                    "pitch REAL NOT NULL," +
                    "created_at INTEGER NOT NULL," +
                    "UNIQUE(chunk_key, home_name)" +
                    ")");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_homes_chunk ON claim_multiple_homes(chunk_key)");

            // Gelişmiş koruma bayrakları - SQLite'da IF NOT EXISTS yok, try-catch ile handle et
            try {
                st.executeUpdate("ALTER TABLE claims ADD COLUMN allow_mob_spawn INTEGER DEFAULT 1");
            } catch (SQLException ignored) {} // Column zaten varsa ignore et
            
            try {
                st.executeUpdate("ALTER TABLE claims ADD COLUMN allow_fire_spread INTEGER DEFAULT 0");
            } catch (SQLException ignored) {}
            
            try {
                st.executeUpdate("ALTER TABLE claims ADD COLUMN allow_crop_growth INTEGER DEFAULT 1");
            } catch (SQLException ignored) {}
            
            try {
                st.executeUpdate("ALTER TABLE claims ADD COLUMN allow_leaf_decay INTEGER DEFAULT 1");
            } catch (SQLException ignored) {}
            
            try {
                st.executeUpdate("ALTER TABLE claims ADD COLUMN allow_mob_grief INTEGER DEFAULT 0");
            } catch (SQLException ignored) {}
            
            try {
                st.executeUpdate("ALTER TABLE claims ADD COLUMN allow_fluid_flow INTEGER DEFAULT 1");
            } catch (SQLException ignored) {}
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException ignored) {}
    }
    
    public java.sql.Connection getConnection() {
        return connection;
    }

    public List<DbClaim> fetchAllClaims() {
        String sql = "SELECT chunk_key, owner_uuid, world, x, z, expires_at, allow_explosions, allow_pvp, " +
                    "allow_interact, allow_open_doors, created_at, claim_name, allow_mob_spawn, allow_fire_spread, " +
                    "allow_crop_growth, allow_leaf_decay, allow_mob_grief, allow_fluid_flow FROM claims";
        List<DbClaim> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                DbClaim c = new DbClaim();
                c.chunkKey = rs.getString("chunk_key");
                c.ownerUuid = rs.getString("owner_uuid");
                c.world = rs.getString("world");
                c.x = rs.getInt("x");
                c.z = rs.getInt("z");
                c.expiresAt = rs.getLong("expires_at");
                c.allowExplosions = rs.getInt("allow_explosions") != 0;
                c.allowPvp = rs.getInt("allow_pvp") != 0;
                c.allowInteract = rs.getInt("allow_interact") != 0;
                c.allowOpenDoors = rs.getInt("allow_open_doors") != 0;
                c.createdAt = rs.getLong("created_at");
                c.claimName = rs.getString("claim_name");
                if (c.claimName == null) c.claimName = "Adlandırılmamış Arazi";
                
                // Advanced protection - null check için getObject kullan
                Integer mobSpawn = (Integer) rs.getObject("allow_mob_spawn");
                c.allowMobSpawn = mobSpawn == null || mobSpawn != 0;
                Integer fireSpread = (Integer) rs.getObject("allow_fire_spread");
                c.allowFireSpread = fireSpread != null && fireSpread != 0;
                Integer cropGrowth = (Integer) rs.getObject("allow_crop_growth");
                c.allowCropGrowth = cropGrowth == null || cropGrowth != 0;
                Integer leafDecay = (Integer) rs.getObject("allow_leaf_decay");
                c.allowLeafDecay = leafDecay == null || leafDecay != 0;
                Integer mobGrief = (Integer) rs.getObject("allow_mob_grief");
                c.allowMobGrief = mobGrief != null && mobGrief != 0;
                Integer fluidFlow = (Integer) rs.getObject("allow_fluid_flow");
                c.allowFluidFlow = fluidFlow == null || fluidFlow != 0;
                
                list.add(c);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    public Map<String, List<String>> fetchAllMembers() {
        Map<String, List<String>> map = new HashMap<>();
        String sql = "SELECT chunk_key, member_uuid FROM members";
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String k = rs.getString("chunk_key");
                String m = rs.getString("member_uuid");
                map.computeIfAbsent(k, x -> new ArrayList<>()).add(m);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return map;
    }

    public List<String> fetchMembers(String chunkKey) {
        String sql = "SELECT member_uuid FROM members WHERE chunk_key = ?";
        List<String> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, chunkKey);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    public void upsertClaim(DbClaim c) {
        String sql = "INSERT INTO claims (chunk_key, owner_uuid, world, x, z, expires_at, allow_explosions, allow_pvp, allow_interact, allow_open_doors, created_at, claim_name) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?) " +
                "ON CONFLICT(chunk_key) DO UPDATE SET owner_uuid=excluded.owner_uuid, world=excluded.world, x=excluded.x, z=excluded.z, expires_at=excluded.expires_at, " +
                "allow_explosions=excluded.allow_explosions, allow_pvp=excluded.allow_pvp, allow_interact=excluded.allow_interact, allow_open_doors=excluded.allow_open_doors, claim_name=excluded.claim_name";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int i = 0;
            ps.setString(++i, c.chunkKey);
            ps.setString(++i, c.ownerUuid);
            ps.setString(++i, c.world);
            ps.setInt(++i, c.x);
            ps.setInt(++i, c.z);
            ps.setLong(++i, c.expiresAt);
            ps.setInt(++i, c.allowExplosions ? 1 : 0);
            ps.setInt(++i, c.allowPvp ? 1 : 0);
            ps.setInt(++i, c.allowInteract ? 1 : 0);
            ps.setInt(++i, c.allowOpenDoors ? 1 : 0);
            ps.setLong(++i, c.createdAt);
            ps.setString(++i, c.claimName == null ? "Adlandırılmamış Arazi" : c.claimName);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateClaimName(String chunkKey, String claimName) {
        String sql = "UPDATE claims SET claim_name = ? WHERE chunk_key = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, claimName);
            ps.setString(2, chunkKey);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public void updateExpiry(String chunkKey, long newExpiry) {
        String sql = "UPDATE claims SET expires_at = ? WHERE chunk_key = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, newExpiry);
            ps.setString(2, chunkKey);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public void updateFlags(String chunkKey, boolean explosions, boolean pvp, boolean interact, boolean openDoors) {
        String sql = "UPDATE claims SET allow_explosions=?, allow_pvp=?, allow_interact=?, allow_open_doors=? WHERE chunk_key=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, explosions ? 1 : 0);
            ps.setInt(2, pvp ? 1 : 0);
            ps.setInt(3, interact ? 1 : 0);
            ps.setInt(4, openDoors ? 1 : 0);
            ps.setString(5, chunkKey);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public void deleteClaim(String chunkKey) {
        try (PreparedStatement ps1 = connection.prepareStatement("DELETE FROM members WHERE chunk_key = ?");
             PreparedStatement ps2 = connection.prepareStatement("DELETE FROM claims WHERE chunk_key = ?")) {
            ps1.setString(1, chunkKey);
            ps1.executeUpdate();
            ps2.setString(1, chunkKey);
            ps2.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public void addMember(String chunkKey, String memberUuid) {
        String sql = "INSERT OR IGNORE INTO members (chunk_key, member_uuid) VALUES (?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, chunkKey);
            ps.setString(2, memberUuid);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public void removeMember(String chunkKey, String memberUuid) {
        String sql = "DELETE FROM members WHERE chunk_key = ? AND member_uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, chunkKey);
            ps.setString(2, memberUuid);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public List<String> fetchExpiredChunkKeys(long now) {
        String sql = "SELECT chunk_key FROM claims WHERE expires_at <= ?";
        List<String> res = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, now);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) res.add(rs.getString(1));
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
        return res;
    }

    public static class DbClaim {
        public String chunkKey;
        public String ownerUuid;
        public String world;
        public int x;
        public int z;
        public long expiresAt;
        public boolean allowExplosions;
        public boolean allowPvp;
        public boolean allowInteract;
        public boolean allowOpenDoors;
        public long createdAt;
        public String claimName;
        
        // Advanced protection
        public boolean allowMobSpawn = true;
        public boolean allowFireSpread = false;
        public boolean allowCropGrowth = true;
        public boolean allowLeafDecay = true;
        public boolean allowMobGrief = false;
        public boolean allowFluidFlow = true;
    }
}
