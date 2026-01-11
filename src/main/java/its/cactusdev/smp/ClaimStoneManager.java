package its.cactusdev.smp;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class ClaimStoneManager implements Listener {
    private final Main plugin;
    private final ClaimManager claims;
    private final Messages messages;
    private final Map<String, List<TextDisplay>> holograms = new HashMap<>();
    private final double hologramHeight;
    private final boolean enabled;
    private final List<String> hologramLines;

    public ClaimStoneManager(Main plugin, ClaimManager claims, Messages messages) {
        this.plugin = plugin;
        this.claims = claims;
        this.messages = messages;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        plugin.getConfig().addDefault("claim_stone.enabled", true);
        plugin.getConfig().addDefault("claim_stone.hologram_height", 2.5);
        plugin.getConfig().addDefault("claim_stone.hologram_lines", Arrays.asList(
                "<yellow><bold>{name}</bold></yellow>",
                "<gray>─────────────────</gray>",
                "<green>Sahip: <white>{owner}</white></green>",
                "<aqua>Büyüklük: <white>{size} Chunk</white></aqua>",
                "<gold>Kalan Süre: <white>{time}</white></gold>",
                "",
                "<gray><italic>Yönetmek için sağ tıkla!</italic></gray>"
        ));
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();

        this.enabled = plugin.getConfig().getBoolean("claim_stone.enabled", true);
        this.hologramHeight = plugin.getConfig().getDouble("claim_stone.hologram_height", 2.5);
        this.hologramLines = plugin.getConfig().getStringList("claim_stone.hologram_lines");
    }

    /**
     * Claim oluşturulduğunda veya genişletildiğinde ana chunk'ın merkezine bedrock koy ve hologram güncelle.
     * Sadece ana claim'de taş oluşturur, genişletmelerde sadece hologram günceller.
     */
    public void updateOrCreateStone(ClaimManager.Claim claim) {
        if (!enabled) return;

        World world = Bukkit.getWorld(claim.getWorld());
        if (world == null) return;

        // Ana claim chunk'ını bul
        Chunk claimChunk = world.getChunkAt(claim.getX(), claim.getZ());
        ClaimManager.Claim mainClaim = claims.getMainClaimChunk(claimChunk);
        if (mainClaim == null) mainClaim = claim;

        // Ana chunk merkezini bul (8, 8, yükseklik)
        Chunk mainChunk = world.getChunkAt(mainClaim.getX(), mainClaim.getZ());
        String mainChunkKey = ClaimManager.key(mainClaim.getWorld(), mainClaim.getX(), mainClaim.getZ());
        
        int centerX = mainChunk.getX() * 16 + 8;
        int centerZ = mainChunk.getZ() * 16 + 8;
        
        // Yüksekliği bul (en yüksek blok + 1)
        int y = world.getHighestBlockYAt(centerX, centerZ) + 1;
        Location stoneLoc = new Location(world, centerX, y, centerZ);
        
        Block block = stoneLoc.getBlock();
        
        // Eğer bu genişletme (expansion) ise ve ana claim chunk ile aynı değilse, sadece hologram güncelle
        boolean isMainChunk = (claim.getX() == mainClaim.getX() && claim.getZ() == mainClaim.getZ());
        
        if (!isMainChunk) {
            // Bu bir genişletme, sadece hologram güncelle
            if (holograms.containsKey(mainChunkKey)) {
                updateHologram(mainClaim);
            } else {
                // Hologram yoksa oluştur (ama bedrock koymadan)
                if (block.getType() == Material.BEDROCK) {
                    createHologram(mainClaim, stoneLoc);
                }
            }
            return;
        }
        
        // Ana chunk için: Bedrock ve hologram oluştur/güncelle
        // Bedrock koy (sadece yoksa)
        if (block.getType() != Material.BEDROCK) {
            block.setType(Material.BEDROCK);
        }

        // Hologram oluştur veya güncelle
        if (holograms.containsKey(mainChunkKey)) {
            updateHologram(mainClaim);
        } else {
            createHologram(mainClaim, stoneLoc);
        }
    }

    /**
     * Claim silindiğinde bedrock'u ve hologram'ı kaldır
     * Sadece ana chunk'daki bedrock'u kaldır (eğer başka claim'ler yoksa)
     */
    public void removeClaimStone(ClaimManager.Claim claim) {
        if (!enabled) return;

        World world = Bukkit.getWorld(claim.getWorld());
        if (world == null) return;

        // Ana claim chunk'ını bul
        Chunk claimChunk = world.getChunkAt(claim.getX(), claim.getZ());
        ClaimManager.Claim mainClaim = claims.getMainClaimChunk(claimChunk);
        if (mainClaim == null) return;

        // Ana chunk merkezini bul
        Chunk chunk = world.getChunkAt(mainClaim.getX(), mainClaim.getZ());
        int centerX = chunk.getX() * 16 + 8;
        int centerZ = chunk.getZ() * 16 + 8;
        int y = world.getHighestBlockYAt(centerX, centerZ) + 1;
        Location stoneLoc = new Location(world, centerX, y, centerZ);

        // Ana chunk'a ait başka claim'ler var mı kontrol et
        boolean hasOtherClaims = false;
        for (ClaimManager.Claim c : claims.getAllClaims()) {
            if (c.getWorld().equals(mainClaim.getWorld())) {
                ClaimManager.Claim otherMain = claims.getMainClaimChunk(world.getChunkAt(c.getX(), c.getZ()));
                if (otherMain != null && otherMain.getX() == mainClaim.getX() && otherMain.getZ() == mainClaim.getZ() 
                    && !c.getChunkKey().equals(claim.getChunkKey())) {
                    hasOtherClaims = true;
                    break;
                }
            }
        }

        // Eğer başka claim'ler yoksa bedrock'u kaldır
        if (!hasOtherClaims) {
            Block block = stoneLoc.getBlock();
            if (block.getType() == Material.BEDROCK) {
                block.setType(Material.AIR);
            }
        }

        // Hologram'ı kaldır (sadece bu claim'in hologram'ı)
        removeHologram(claim.getChunkKey());
        
        // Eğer başka claim'ler varsa, hologram'ı güncelle
        if (hasOtherClaims) {
            updateHologram(mainClaim);
        }
    }

    /**
     * Hologram oluştur
     */
    private void createHologram(ClaimManager.Claim claim, Location baseLoc) {
        // Ana claim chunk'ını bul - ana chunk key kullan
        World world = baseLoc.getWorld();
        ClaimManager.Claim mainClaim = claims.getMainClaimChunk(world.getChunkAt(claim.getX(), claim.getZ()));
        if (mainClaim == null) mainClaim = claim;
        
        String mainChunkKey = ClaimManager.key(mainClaim.getWorld(), mainClaim.getX(), mainClaim.getZ());
        
        // Eski hologram varsa kaldır
        removeHologram(mainChunkKey);

        List<TextDisplay> displays = new ArrayList<>();
        
        // Hologram'ı ortalı yapmak için toplam yüksekliği hesapla
        double totalHeight = (hologramLines.size() - 1) * 0.3; // Satırlar arası mesafe
        double startY = baseLoc.getY() + hologramHeight + (totalHeight / 2.0); // Ortadan başla
        double currentY = startY;

        // Placeholder'ları hazırla
        Map<String, String> placeholders = new HashMap<>();
        String ownerName = Optional.ofNullable(Bukkit.getOfflinePlayer(claim.getOwner()).getName()).orElse("Bilinmiyor");
        placeholders.put("owner", ownerName);
        placeholders.put("name", claim.getClaimName());
        placeholders.put("world", claim.getWorld());
        placeholders.put("x", String.valueOf(claim.getX()));
        placeholders.put("z", String.valueOf(claim.getZ()));
        placeholders.put("time", formatTime(claim.getExpiresAt() - System.currentTimeMillis()));
        placeholders.put("created", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(claim.getCreatedAt())));
        
        // Toplam chunk sayısını hesapla (aynı sahibin aynı dünyadaki claimleri)
        long chunkCount = claims.getAllClaims().stream()
            .filter(c -> c.getOwner().equals(claim.getOwner()) && c.getWorld().equals(claim.getWorld()))
            .count();
        placeholders.put("size", String.valueOf(chunkCount));

        // Her satır için TextDisplay oluştur (yukarıdan aşağıya)
        for (int i = hologramLines.size() - 1; i >= 0; i--) {
            String line = hologramLines.get(i);
            
            // Placeholder'ları değiştir
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                line = line.replace("{" + entry.getKey() + "}", entry.getValue());
            }

            // Bedrock'un tam üstüne, ortalanmış şekilde yerleştir
            Location loc = baseLoc.clone();
            loc.setX(baseLoc.getX() + 0.5); // Chunk merkezi (8.5)
            loc.setZ(baseLoc.getZ() + 0.5); // Chunk merkezi (8.5)
            loc.setY(currentY);

            TextDisplay display = baseLoc.getWorld().spawn(loc, TextDisplay.class);
            display.text(messages.getComponent("", line));
            display.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            display.setSeeThrough(true);
            display.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
            display.setAlignment(org.bukkit.entity.TextDisplay.TextAlignment.CENTER);
            display.setLineWidth(200);
            display.setPersistent(false);
            display.setInvulnerable(true);
            display.setCustomNameVisible(false);

            displays.add(display);
            currentY -= 0.3; // Yukarıdan aşağıya doğru
        }

        holograms.put(mainChunkKey, displays);

        // Süre güncellemesi için task
        startUpdateTask(mainClaim);
    }

    /**
     * Hologram'ı kaldır
     */
    private void removeHologram(String chunkKey) {
        List<TextDisplay> displays = holograms.remove(chunkKey);
        if (displays != null) {
            for (TextDisplay display : displays) {
                if (display.isValid()) {
                    display.remove();
                }
            }
        }
    }

    /**
     * Hologram'ı güncelle (süre bilgisi için)
     */
    public void updateHologram(ClaimManager.Claim claim) {
        if (!enabled) return;
        
        // Ana claim chunk'ını bul - ana chunk key kullan
        World world = Bukkit.getWorld(claim.getWorld());
        if (world == null) return;
        
        ClaimManager.Claim mainClaim = claims.getMainClaimChunk(world.getChunkAt(claim.getX(), claim.getZ()));
        if (mainClaim == null) mainClaim = claim;
        
        String mainChunkKey = ClaimManager.key(mainClaim.getWorld(), mainClaim.getX(), mainClaim.getZ());
        List<TextDisplay> displays = holograms.get(mainChunkKey);
        if (displays == null || displays.isEmpty()) {
            // Hologram yoksa yeniden oluştur
            Chunk chunk = world.getChunkAt(mainClaim.getX(), mainClaim.getZ());
            int centerX = chunk.getX() * 16 + 8;
            int centerZ = chunk.getZ() * 16 + 8;
            int y = world.getHighestBlockYAt(centerX, centerZ) + 1;
            Location stoneLoc = new Location(world, centerX, y, centerZ);
            createHologram(mainClaim, stoneLoc);
            return;
        }

        Map<String, String> placeholders = new HashMap<>();
        String ownerName = Optional.ofNullable(Bukkit.getOfflinePlayer(claim.getOwner()).getName()).orElse("Bilinmiyor");
        placeholders.put("owner", ownerName);
        placeholders.put("name", claim.getClaimName());
        placeholders.put("world", claim.getWorld());
        placeholders.put("x", String.valueOf(claim.getX()));
        placeholders.put("z", String.valueOf(claim.getZ()));
        placeholders.put("time", formatTime(claim.getExpiresAt() - System.currentTimeMillis()));
        placeholders.put("created", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(claim.getCreatedAt())));
        
        // Toplam chunk sayısını hesapla
        long chunkCount = claims.getAllClaims().stream()
            .filter(c -> c.getOwner().equals(claim.getOwner()) && c.getWorld().equals(claim.getWorld()))
            .count();
        placeholders.put("size", String.valueOf(chunkCount));

        int lineIndex = 0;
        for (int i = hologramLines.size() - 1; i >= 0; i--) {
            if (lineIndex >= displays.size()) break;
            
            String line = hologramLines.get(i);
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                line = line.replace("{" + entry.getKey() + "}", entry.getValue());
            }

            TextDisplay display = displays.get(lineIndex);
            if (display.isValid()) {
                display.text(messages.getComponent("", line));
            }
            lineIndex++;
        }
    }

    /**
     * Süre güncelleme task'ı başlat
     */
    private void startUpdateTask(ClaimManager.Claim claim) {
        new BukkitRunnable() {
            @Override
            public void run() {
                ClaimManager.Claim currentClaim = claims.getClaimAt(
                    Bukkit.getWorld(claim.getWorld()).getChunkAt(claim.getX(), claim.getZ())
                );
                if (currentClaim == null) {
                    cancel();
                    return;
                }
                updateHologram(currentClaim);
            }
        }.runTaskTimer(plugin, 20L, 20L); // Her saniye güncelle
    }

    /**
     * Süreyi formatla
     */
    private String formatTime(long deltaMs) {
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

    /**
     * Bedrock'a sağ tıklama event'i - Sadece ana chunk'daki bedrock'da menü aç
     */
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null) return;
        if (e.getClickedBlock().getType() != Material.BEDROCK) return;

        Block block = e.getClickedBlock();
        Chunk chunk = block.getChunk();
        ClaimManager.Claim claim = claims.getClaimAt(chunk);
        
        if (claim == null) return;

        // Ana claim chunk'ını bul
        ClaimManager.Claim mainClaim = claims.getMainClaimChunk(chunk);
        if (mainClaim == null) return;

        // Sadece ana chunk'daki bedrock'da menü aç
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        int mainX = mainClaim.getX();
        int mainZ = mainClaim.getZ();
        
        // Eğer bu chunk ana chunk değilse, menü açma
        if (chunkX != mainX || chunkZ != mainZ) {
            return;
        }

        Player p = e.getPlayer();
        e.setCancelled(true);

        // Menüyü aç
        Main.get().menuHandler().openMain(p);
    }

    /**
     * Tüm hologram'ları temizle (plugin kapatılırken)
     */
    public void cleanup() {
        for (List<TextDisplay> displays : holograms.values()) {
            for (TextDisplay display : displays) {
                if (display.isValid()) {
                    display.remove();
                }
            }
        }
        holograms.clear();
    }
}
