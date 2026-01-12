package its.cactusdev.smp.features;

import its.cactusdev.smp.Main;
import its.cactusdev.smp.managers.ClaimManager;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;

public class ClaimEffects {
    private final Main plugin;
    private final Map<String, EffectTask> activeEffects = new HashMap<>();
    
    private final boolean enabled;
    
    public ClaimEffects(Main plugin) {
        this.plugin = plugin;
        
        plugin.getConfig().addDefault("claim_effects.enabled", true);
        plugin.getConfig().addDefault("claim_effects.particle_type", "FLAME");
        plugin.getConfig().addDefault("claim_effects.density", 5);
        plugin.getConfig().addDefault("claim_effects.height", 3.0);
        plugin.getConfig().addDefault("claim_effects.speed", 0.02);
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
        
        this.enabled = plugin.getConfig().getBoolean("claim_effects.enabled", true);
    }
    
    public void startEffect(ClaimManager.Claim claim, EffectType type) {
        if (!enabled) return;
        
        String chunkKey = claim.getChunkKey();
        
        // Mevcut efekti durdur
        stopEffect(chunkKey);
        
        // Yeni efekt başlat
        EffectTask task = new EffectTask(claim, type);
        task.runTaskTimer(plugin, 0L, 5L); // Her 5 tick (0.25 saniye)
        activeEffects.put(chunkKey, task);
    }
    
    public void stopEffect(String chunkKey) {
        EffectTask task = activeEffects.remove(chunkKey);
        if (task != null) {
            task.cancel();
        }
    }
    
    public boolean hasEffect(String chunkKey) {
        return activeEffects.containsKey(chunkKey);
    }
    
    public enum EffectType {
        SPIRAL,
        HELIX,
        RING,
        FOUNTAIN,
        PULSE
    }
    
    private class EffectTask extends BukkitRunnable {
        private final ClaimManager.Claim claim;
        private final EffectType type;
        private double angle = 0;
        private int ticks = 0;
        
        public EffectTask(ClaimManager.Claim claim, EffectType type) {
            this.claim = claim;
            this.type = type;
        }
        
        @Override
        public void run() {
            // Claim chunk'ından merkez location hesapla
            String[] parts = claim.getChunkKey().split(":");
            if (parts.length != 3) return;
            
            World world = plugin.getServer().getWorld(parts[0]);
            if (world == null) return;
            
            int chunkX = Integer.parseInt(parts[1]);
            int chunkZ = Integer.parseInt(parts[2]);
            
            // Chunk ortası koordinatları
            double centerX = (chunkX * 16) + 8;
            double centerZ = (chunkZ * 16) + 8;
            double centerY = world.getHighestBlockYAt((int)centerX, (int)centerZ) + 1;
            
            Location centerLoc = new Location(world, centerX, centerY, centerZ);
            
            Particle particle = getParticleType();
            int density = plugin.getConfig().getInt("claim_effects.density", 5);
            double height = plugin.getConfig().getDouble("claim_effects.height", 3.0);
            double speed = plugin.getConfig().getDouble("claim_effects.speed", 0.02);
            
            switch (type) {
                case SPIRAL:
                    drawSpiral(world, centerLoc, particle, density, height, speed);
                    break;
                case HELIX:
                    drawHelix(world, centerLoc, particle, density, height, speed);
                    break;
                case RING:
                    drawRing(world, centerLoc, particle, density, height);
                    break;
                case FOUNTAIN:
                    drawFountain(world, centerLoc, particle, density, height);
                    break;
                case PULSE:
                    drawPulse(world, centerLoc, particle, density);
                    break;
            }
            
            angle += speed * 10;
            if (angle >= Math.PI * 2) angle = 0;
            ticks++;
        }
        
        private void drawSpiral(World world, Location center, Particle particle, int density, double height, double speed) {
            for (int i = 0; i < density; i++) {
                double y = (ticks % 20) / 20.0 * height;
                double radius = 0.5 + (y / height) * 0.5;
                double currentAngle = angle + (i * Math.PI * 2 / density);
                
                double x = center.getX() + radius * Math.cos(currentAngle + y * 2);
                double z = center.getZ() + radius * Math.sin(currentAngle + y * 2);
                
                world.spawnParticle(particle, x, center.getY() + y, z, 1, 0, 0, 0, 0);
            }
        }
        
        private void drawHelix(World world, Location center, Particle particle, int density, double height, double speed) {
            for (int i = 0; i < density * 2; i++) {
                double y = (i / (density * 2.0)) * height;
                double currentAngle = angle + (y * 4);
                double radius = 0.7;
                
                double x = center.getX() + radius * Math.cos(currentAngle);
                double z = center.getZ() + radius * Math.sin(currentAngle);
                
                world.spawnParticle(particle, x, center.getY() + y, z, 1, 0, 0, 0, 0);
                
                // İkinci spiral (ters yönde)
                double x2 = center.getX() + radius * Math.cos(currentAngle + Math.PI);
                double z2 = center.getZ() + radius * Math.sin(currentAngle + Math.PI);
                world.spawnParticle(particle, x2, center.getY() + y, z2, 1, 0, 0, 0, 0);
            }
        }
        
        private void drawRing(World world, Location center, Particle particle, int density, double height) {
            double y = center.getY() + height / 2;
            double radius = 1.0;
            
            for (int i = 0; i < density * 4; i++) {
                double currentAngle = (i / (density * 4.0)) * Math.PI * 2;
                double x = center.getX() + radius * Math.cos(currentAngle);
                double z = center.getZ() + radius * Math.sin(currentAngle);
                
                world.spawnParticle(particle, x, y, z, 1, 0, 0, 0, 0);
            }
        }
        
        private void drawFountain(World world, Location center, Particle particle, int density, double height) {
            for (int i = 0; i < density; i++) {
                double randomAngle = Math.random() * Math.PI * 2;
                double randomRadius = Math.random() * 0.3;
                double randomHeight = Math.random() * height;
                
                double x = center.getX() + randomRadius * Math.cos(randomAngle);
                double z = center.getZ() + randomRadius * Math.sin(randomAngle);
                double y = center.getY() + randomHeight;
                
                world.spawnParticle(particle, x, y, z, 1, 0, 0.1, 0, 0.02);
            }
        }
        
        private void drawPulse(World world, Location center, Particle particle, int density) {
            double pulseRadius = (Math.sin(ticks * 0.1) + 1) * 0.5 + 0.5; // 0.5 - 1.5
            
            for (int i = 0; i < density * 3; i++) {
                double currentAngle = (i / (density * 3.0)) * Math.PI * 2;
                double x = center.getX() + pulseRadius * Math.cos(currentAngle);
                double z = center.getZ() + pulseRadius * Math.sin(currentAngle);
                double y = center.getY() + 1.5;
                
                world.spawnParticle(particle, x, y, z, 1, 0, 0, 0, 0);
            }
        }
        
        private Particle getParticleType() {
            try {
                String particleName = plugin.getConfig().getString("claim_effects.particle_type", "FLAME");
                return Particle.valueOf(particleName);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid particle type, using FLAME");
                return Particle.FLAME;
            }
        }
    }
}
