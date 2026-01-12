package its.cactusdev.smp.managers;

import its.cactusdev.smp.Main;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PreviewManager {
    private final Main plugin;
    private final Map<UUID, BukkitRunnable> particleTasks = new ConcurrentHashMap<>();
    private final Set<UUID> active = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());

    public PreviewManager(Main plugin) {
        this.plugin = plugin;
    }

    public boolean isPreviewing(Player p) { return active.contains(p.getUniqueId()); }

    public void startPreview(Player p, World w, int chunkX, int chunkZ, int durationSeconds) {
        stopPreview(p);
        if (!p.getWorld().equals(w)) return;
        active.add(p.getUniqueId());

        // Draw preview for current chunk and all claimed chunks in the area
        BukkitRunnable task = new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                ticks++;
                drawPreview(p, w, chunkX, chunkZ);
                if (durationSeconds > 0 && ticks >= durationSeconds) cancel();
            }
            @Override public synchronized void cancel() {
                super.cancel();
                particleTasks.remove(p.getUniqueId());
                active.remove(p.getUniqueId());
            }
        };
        particleTasks.put(p.getUniqueId(), task);
        task.runTaskTimer(plugin, 0L, 20L);
    }

    public void stopPreview(Player p) {
        BukkitRunnable r = particleTasks.remove(p.getUniqueId());
        if (r != null) r.cancel();
        active.remove(p.getUniqueId());
    }

    private void drawPreview(Player p, World w, int centerChunkX, int centerChunkZ) {
        ClaimManager cm = Main.get().claims();
        int y = p.getLocation().getBlockY();
        
        // Bulunduğu chunk'ı kontrol et
        Chunk currentChunk = w.getChunkAt(centerChunkX, centerChunkZ);
        ClaimManager.Claim currentClaim = cm.getClaimAt(currentChunk);
        
        if (currentClaim != null) {
            // Claim varsa, o claim'in tüm bağlı claim'lerini birleştirip göster
            ClaimManager.Claim mainClaim = cm.getMainClaimChunk(currentChunk);
            if (mainClaim != null) {
                // Tüm bağlı claim'leri bul (BFS ile)
                Set<String> connectedClaims = findConnectedClaims(cm, mainClaim, w.getName());
                
                // Her bağlı claim'i çiz (YEŞIL - bizim olan)
                for (String key : connectedClaims) {
                    String[] parts = key.split(":");
                    int cx = Integer.parseInt(parts[1]);
                    int cz = Integer.parseInt(parts[2]);
                    drawChunkBorder(p, w, cx, cz, y, Color.LIME, true); // Daha yoğun
                }
                
                // Komşu chunk'ları kontrol et ve farklı renkle göster
                for (String key : connectedClaims) {
                    String[] parts = key.split(":");
                    int cx = Integer.parseInt(parts[1]);
                    int cz = Integer.parseInt(parts[2]);
                    
                    // 4 komşuyu kontrol et
                    int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
                    for (int[] d : dirs) {
                        int nx = cx + d[0];
                        int nz = cz + d[1];
                        String nk = ClaimManager.key(w.getName(), nx, nz);
                        
                        // Eğer bu chunk zaten bizim claim grubumuzda değilse
                        if (!connectedClaims.contains(nk)) {
                            ClaimManager.Claim neighborClaim = cm.getClaimAt(w.getChunkAt(nx, nz));
                            if (neighborClaim != null) {
                                // Başkasının claim'i - KIRMIZI
                                drawChunkBorder(p, w, nx, nz, y, Color.RED, false);
                            } else {
                                // Boş chunk - SARI
                                drawChunkBorder(p, w, nx, nz, y, Color.YELLOW, false);
                            }
                        }
                    }
                }
            }
        } else {
            // Claim yoksa, mevcut chunk'ı yeşil göster
            drawChunkBorder(p, w, centerChunkX, centerChunkZ, y, Color.LIME, true);
            
            // Komşu chunk'ları göster
            int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
            for (int[] d : dirs) {
                int nx = centerChunkX + d[0];
                int nz = centerChunkZ + d[1];
                ClaimManager.Claim neighborClaim = cm.getClaimAt(w.getChunkAt(nx, nz));
                if (neighborClaim != null) {
                    // Başkasının claim'i - KIRMIZI
                    drawChunkBorder(p, w, nx, nz, y, Color.RED, false);
                } else {
                    // Boş chunk - SARI
                    drawChunkBorder(p, w, nx, nz, y, Color.YELLOW, false);
                }
            }
        }
    }
    
    /**
     * Bağlı claim'leri bul (BFS ile)
     */
    private Set<String> findConnectedClaims(ClaimManager cm, ClaimManager.Claim startClaim, String world) {
        Set<String> visited = new HashSet<>();
        Deque<ClaimManager.Claim> queue = new ArrayDeque<>();
        queue.add(startClaim);
        visited.add(startClaim.getChunkKey());
        
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        while (!queue.isEmpty()) {
            ClaimManager.Claim current = queue.poll();
            int x = current.getX();
            int z = current.getZ();
            
            for (int[] d : dirs) {
                int nx = x + d[0];
                int nz = z + d[1];
                String nk = ClaimManager.key(world, nx, nz);
                ClaimManager.Claim neighbor = cm.getClaimAt(
                    Bukkit.getWorld(world).getChunkAt(nx, nz)
                );
                if (neighbor != null && neighbor.getOwner().equals(startClaim.getOwner()) && visited.add(nk)) {
                    queue.add(neighbor);
                }
            }
        }
        return visited;
    }
    
    /**
     * Birleştirilmiş claim alanının border'ını çiz
     */
    private void drawUnifiedBorder(Player p, World w, int minX, int maxX, int minZ, int maxZ, int y, Color color) {
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.5F);
        
        // Her 10 blokta bir particle spawn et
        // Üst ve alt kenarlar
        for (int x = minX; x <= maxX; x += 10) {
            spawn(w, x + 0.5, y + 0.2, minZ + 0.5, p, dust);
            spawn(w, x + 0.5, y + 0.2, maxZ + 0.5, p, dust);
        }
        // Son noktaları da çiz
        spawn(w, maxX + 0.5, y + 0.2, minZ + 0.5, p, dust);
        spawn(w, maxX + 0.5, y + 0.2, maxZ + 0.5, p, dust);
        
        // Sol ve sağ kenarlar
        for (int z = minZ; z <= maxZ; z += 10) {
            spawn(w, minX + 0.5, y + 0.2, z + 0.5, p, dust);
            spawn(w, maxX + 0.5, y + 0.2, z + 0.5, p, dust);
        }
        // Son noktaları da çiz
        spawn(w, minX + 0.5, y + 0.2, maxZ + 0.5, p, dust);
        spawn(w, maxX + 0.5, y + 0.2, maxZ + 0.5, p, dust);
    }

    private void drawChunkBorder(Player p, World w, int chunkX, int chunkZ, int y, Color color, boolean dense) {
        int minBlockX = chunkX * 16;
        int maxBlockX = chunkX * 16 + 15;
        int minBlockZ = chunkZ * 16;
        int maxBlockZ = chunkZ * 16 + 15;
        
        // Daha belirgin particle ayarları
        Particle.DustOptions dust = new Particle.DustOptions(color, dense ? 2.0F : 1.2F);
        int spacing = dense ? 2 : 4; // Yoğun olanlarda daha sık particle
        
        // Köşelere ekstra belirgin particle (çift spawn)
        for (int i = 0; i < 3; i++) {
            spawn(w, minBlockX + 0.5, y + 0.2 + (i * 0.3), minBlockZ + 0.5, p, dust);
            spawn(w, maxBlockX + 0.5, y + 0.2 + (i * 0.3), minBlockZ + 0.5, p, dust);
            spawn(w, minBlockX + 0.5, y + 0.2 + (i * 0.3), maxBlockZ + 0.5, p, dust);
            spawn(w, maxBlockX + 0.5, y + 0.2 + (i * 0.3), maxBlockZ + 0.5, p, dust);
        }
        
        // Kenarlar - daha yoğun
        for (int x = minBlockX; x <= maxBlockX; x += spacing) {
            spawn(w, x + 0.5, y + 0.2, minBlockZ + 0.5, p, dust);
            spawn(w, x + 0.5, y + 0.5, minBlockZ + 0.5, p, dust); // İkinci katman
            spawn(w, x + 0.5, y + 0.2, maxBlockZ + 0.5, p, dust);
            spawn(w, x + 0.5, y + 0.5, maxBlockZ + 0.5, p, dust);
        }
        
        for (int z = minBlockZ; z <= maxBlockZ; z += spacing) {
            spawn(w, minBlockX + 0.5, y + 0.2, z + 0.5, p, dust);
            spawn(w, minBlockX + 0.5, y + 0.5, z + 0.5, p, dust);
            spawn(w, maxBlockX + 0.5, y + 0.2, z + 0.5, p, dust);
            spawn(w, maxBlockX + 0.5, y + 0.5, z + 0.5, p, dust);
        }
    }

    private void spawn(World w, double x, double y, double z, Player p, Particle.DustOptions dust) {
        Location loc = new Location(w, x, y, z);
        p.spawnParticle(Particle.DUST, loc, 2, 0.05, 0.05, 0.05, 0, dust); // Daha fazla particle, hafif spread
    }
}
