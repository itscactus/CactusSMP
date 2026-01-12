package its.cactusdev.smp;

import org.bukkit.Chunk;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClaimManager {
    private final Database db;
    private final Map<String, Claim> cache = new ConcurrentHashMap<>();

    public ClaimManager(Database db) {
        this.db = db;
        loadAll();
    }

    private void loadAll() {
        // N+1 Fix: Tüm üyeleri tek sorguda çek
        Map<String, List<String>> allMembers = db.fetchAllMembers();

        for (Database.DbClaim c : db.fetchAllClaims()) {
            Claim claim = new Claim(c.chunkKey, UUID.fromString(c.ownerUuid), c.world, c.x, c.z,
                    c.expiresAt, c.allowExplosions, c.allowPvp, c.allowInteract, c.allowOpenDoors, c.createdAt, c.claimName);
            
            if (allMembers.containsKey(c.chunkKey)) {
                claim.getMembers().addAll(asUuids(allMembers.get(c.chunkKey)));
            }
            
            // Advanced protection ayarları
            claim.setAllowMobSpawn(c.allowMobSpawn);
            claim.setAllowFireSpread(c.allowFireSpread);
            claim.setAllowCropGrowth(c.allowCropGrowth);
            claim.setAllowLeafDecay(c.allowLeafDecay);
            claim.setAllowMobGrief(c.allowMobGrief);
            claim.setAllowFluidFlow(c.allowFluidFlow);
            
            cache.put(c.chunkKey, claim);
        }
    }

    private List<UUID> asUuids(List<String> list) {
        List<UUID> out = new ArrayList<>();
        for (String s : list) out.add(UUID.fromString(s));
        return out;
    }

    public static String key(String world, int x, int z) { return world + ":" + x + ":" + z; }
    
    public static String getChunkKey(Chunk chunk) {
        return key(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }
    
    public Claim getClaim(String chunkKey) {
        return cache.get(chunkKey);
    }

    public Claim getClaimAt(Chunk chunk) {
        return cache.get(key(chunk.getWorld().getName(), chunk.getX(), chunk.getZ()));
    }

    public boolean canBuild(Player p, Chunk chunk) {
        Claim c = getClaimAt(chunk);
        if (c == null) return true;
        if (c.getOwner().equals(p.getUniqueId())) return true;
        return c.getMembers().contains(p.getUniqueId());
    }

    public boolean canInteract(Player p, Chunk chunk) {
        Claim c = getClaimAt(chunk);
        if (c == null) return true;
        if (c.getOwner().equals(p.getUniqueId())) return true;
        if (c.getMembers().contains(p.getUniqueId())) return true;
        return c.isAllowInteract();
    }

    public boolean claimChunk(Player player, Chunk chunk, long durationMillis, String claimName) {
        String k = key(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        if (cache.containsKey(k)) return false;
        // Birden fazla claim alınabilir, komşuluk kontrolü yok
        long now = System.currentTimeMillis();
        long expires = now + durationMillis;
        Claim c = new Claim(k, player.getUniqueId(), chunk.getWorld().getName(), chunk.getX(), chunk.getZ(),
                expires, false, false, false, true, now, claimName);
        cache.put(k, c);
        persist(c);
        return true;
    }

    public boolean extendClaim(Chunk chunk, long extendMillis) {
        Claim c = getClaimAt(chunk);
        if (c == null) return false;
        c.setExpiresAt(c.getExpiresAt() + extendMillis);
        db.updateExpiry(c.getChunkKey(), c.getExpiresAt());
        return true;
    }

    public List<Claim> getExpiredClaims(long now) {
        List<String> expiredKeys = db.fetchExpiredChunkKeys(now);
        List<Claim> expiredClaims = new ArrayList<>();
        for (String key : expiredKeys) {
            Claim claim = cache.get(key);
            if (claim != null) {
                expiredClaims.add(claim);
            }
        }
        return expiredClaims;
    }

    public void removeExpired(long now) {
        List<String> expired = db.fetchExpiredChunkKeys(now);
        for (String key : expired) {
            cache.remove(key);
            db.deleteClaim(key);
        }
    }

    public void addMember(Chunk chunk, UUID uuid) {
        Claim c = getClaimAt(chunk);
        if (c == null) return;
        if (c.getMembers().add(uuid)) db.addMember(c.getChunkKey(), uuid.toString());
    }

    public void removeMember(Chunk chunk, UUID uuid) {
        Claim c = getClaimAt(chunk);
        if (c == null) return;
        if (c.getMembers().remove(uuid)) db.removeMember(c.getChunkKey(), uuid.toString());
    }

    public void updateFlags(Chunk chunk, boolean explosions, boolean pvp, boolean interact, boolean openDoors) {
        Claim c = getClaimAt(chunk);
        if (c == null) return;
        c.setAllowExplosions(explosions);
        c.setAllowPvp(pvp);
        c.setAllowInteract(interact);
        c.setAllowOpenDoors(openDoors);
        db.updateFlags(c.getChunkKey(), explosions, pvp, interact, openDoors);
    }

    public boolean hasAnyClaim(UUID owner) {
        for (Claim c : cache.values()) if (c.getOwner().equals(owner)) return true;
        return false;
    }
    
    /**
     * Bir oyuncunun sahip olduğu toplam chunk sayısını döndürür.
     */
    public int getTotalChunksOwned(UUID owner) {
        return (int) cache.values().stream()
                .filter(c -> c.getOwner().equals(owner))
                .count();
    }

    private void persist(Claim c) {
        Database.DbClaim d = new Database.DbClaim();
        d.chunkKey = c.getChunkKey();
        d.ownerUuid = c.getOwner().toString();
        d.world = c.getWorld();
        d.x = c.getX();
        d.z = c.getZ();
        d.expiresAt = c.getExpiresAt();
        d.allowExplosions = c.isAllowExplosions();
        d.allowPvp = c.isAllowPvp();
        d.allowInteract = c.isAllowInteract();
        d.allowOpenDoors = c.isAllowOpenDoors();
        d.createdAt = c.getCreatedAt();
        d.claimName = c.getClaimName();
        db.upsertClaim(d);
    }

    public Collection<Claim> getAllClaims() { return cache.values(); }

    /**
     * Verilen chunk'ın ait olduğu claim grubunun ana chunk'ını döndürür.
     * Ana chunk, en eski oluşturulan chunk'dır (createdAt'a göre).
     * Eğer chunk claim'li değilse, null döner.
     */
    public Claim getMainClaimChunk(Chunk chunk) {
        Claim startClaim = getClaimAt(chunk);
        if (startClaim == null) return null;
        
        UUID owner = startClaim.getOwner();
        String world = chunk.getWorld().getName();
        
        // Tüm bağlı claim'leri bul (BFS ile)
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(startClaim.getChunkKey());
        visited.add(startClaim.getChunkKey());
        
        List<Claim> connectedClaims = new ArrayList<>();
        connectedClaims.add(startClaim);
        
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        while (!queue.isEmpty()) {
            String currentKey = queue.poll();
            String[] parts = currentKey.split(":");
            int cx = Integer.parseInt(parts[1]);
            int cz = Integer.parseInt(parts[2]);
            
            for (int[] d : dirs) {
                int nx = cx + d[0];
                int nz = cz + d[1];
                String nk = key(world, nx, nz);
                Claim neighbor = cache.get(nk);
                if (neighbor != null && neighbor.getOwner().equals(owner) && visited.add(nk)) {
                    queue.add(nk);
                    connectedClaims.add(neighbor);
                }
            }
        }
        
        // En eski claim'i bul (ana chunk)
        return connectedClaims.stream()
                .min(Comparator.comparingLong(Claim::getCreatedAt))
                .orElse(startClaim);
    }

    public static class Claim {
        private final String chunkKey;
        private final UUID owner;
        private final String world;
        private final int x, z;
        private final long createdAt;
        private long expiresAt;
        private boolean allowExplosions;
        private boolean allowPvp;
        private boolean allowInteract;
        private boolean allowOpenDoors;
        private String claimName;
        private final Set<UUID> members = new HashSet<>();
        
        // Advanced protection flags
        private boolean allowMobSpawn = true;
        private boolean allowFireSpread = false;
        private boolean allowCropGrowth = true;
        private boolean allowLeafDecay = true;
        private boolean allowMobGrief = false;
        private boolean allowFluidFlow = true;

        public Claim(String chunkKey, UUID owner, String world, int x, int z, long expiresAt,
                      boolean allowExplosions, boolean allowPvp, boolean allowInteract, boolean allowOpenDoors, long createdAt, String claimName) {
            this.chunkKey = chunkKey; this.owner = owner; this.world = world; this.x = x; this.z = z;
            this.expiresAt = expiresAt; this.allowExplosions = allowExplosions; this.allowPvp = allowPvp;
            this.allowInteract = allowInteract; this.allowOpenDoors = allowOpenDoors; this.createdAt = createdAt;
            this.claimName = claimName == null ? "Adlandırılmamış Arazi" : claimName;
        }
        public String getChunkKey() { return chunkKey; }
        public UUID getOwner() { return owner; }
        public String getWorld() { return world; }
        public int getX() { return x; }
        public int getZ() { return z; }
        public long getExpiresAt() { return expiresAt; }
        public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }
        public boolean isAllowExplosions() { return allowExplosions; }
        public void setAllowExplosions(boolean allowExplosions) { this.allowExplosions = allowExplosions; }
        public boolean isAllowPvp() { return allowPvp; }
        public void setAllowPvp(boolean allowPvp) { this.allowPvp = allowPvp; }
        public boolean isAllowInteract() { return allowInteract; }
        public void setAllowInteract(boolean allowInteract) { this.allowInteract = allowInteract; }
        public boolean isAllowOpenDoors() { return allowOpenDoors; }
        public void setAllowOpenDoors(boolean allowOpenDoors) { this.allowOpenDoors = allowOpenDoors; }
        public Set<UUID> getMembers() { return members; }
        public long getCreatedAt() { return createdAt; }
        public String getClaimName() { return claimName; }
        public void setClaimName(String claimName) { this.claimName = claimName; }
        
        // Advanced protection getters/setters
        public boolean isAllowMobSpawn() { return allowMobSpawn; }
        public void setAllowMobSpawn(boolean allowMobSpawn) { this.allowMobSpawn = allowMobSpawn; }
        public boolean isAllowFireSpread() { return allowFireSpread; }
        public void setAllowFireSpread(boolean allowFireSpread) { this.allowFireSpread = allowFireSpread; }
        public boolean isAllowCropGrowth() { return allowCropGrowth; }
        public void setAllowCropGrowth(boolean allowCropGrowth) { this.allowCropGrowth = allowCropGrowth; }
        public boolean isAllowLeafDecay() { return allowLeafDecay; }
        public void setAllowLeafDecay(boolean allowLeafDecay) { this.allowLeafDecay = allowLeafDecay; }
        public boolean isAllowMobGrief() { return allowMobGrief; }
        public void setAllowMobGrief(boolean allowMobGrief) { this.allowMobGrief = allowMobGrief; }
        public boolean isAllowFluidFlow() { return allowFluidFlow; }
        public void setAllowFluidFlow(boolean allowFluidFlow) { this.allowFluidFlow = allowFluidFlow; }
    }
}
