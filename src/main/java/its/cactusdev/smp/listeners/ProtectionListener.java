package its.cactusdev.smp.listeners;

import its.cactusdev.smp.Main;
import its.cactusdev.smp.managers.ClaimManager;
import its.cactusdev.smp.utils.ActivityLogger;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.Optional;

public class ProtectionListener implements Listener {
    private final ClaimManager claimManager;
    private final Main plugin;

    public ProtectionListener(Main plugin, ClaimManager cm) { 
        this.plugin = plugin;
        this.claimManager = cm; 
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Chunk c = e.getBlock().getChunk();
        if (!claimManager.canBuild(p, c)) {
            e.setCancelled(true);
        } else {
            // Log yap
            if (plugin.activityLogger() != null && claimManager.getClaimAt(c) != null) {
                plugin.activityLogger().logBlockBreak(c, p, e.getBlock());
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        Chunk c = e.getBlock().getChunk();
        if (!claimManager.canBuild(p, c)) {
            e.setCancelled(true);
        } else {
            // Log yap
            if (plugin.activityLogger() != null && claimManager.getClaimAt(c) != null) {
                plugin.activityLogger().logBlockPlace(c, p, e.getBlock());
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() == Action.PHYSICAL && e.getClickedBlock() == null) return;
        Player p = e.getPlayer();
        
        Chunk c;
        if (e.getClickedBlock() != null) {
            c = e.getClickedBlock().getChunk();
        } else {
            Location loc = e.getInteractionPoint() != null ? e.getInteractionPoint() : p.getLocation();
            c = loc.getChunk();
        }

        ClaimManager.Claim claim = claimManager.getClaimAt(c);
        if (claim == null) return;
        boolean isOwner = claim.getOwner().equals(p.getUniqueId());
        boolean isMember = claim.getMembers().contains(p.getUniqueId());
        if (isOwner || isMember) return;
        if (e.getClickedBlock() != null && isDoorLike(e.getClickedBlock().getType())) {
            if (claim.isAllowOpenDoors()) return; // allow door/gate/trapdoor open
        }
        if (!claim.isAllowInteract()) e.setCancelled(true);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        Chunk from = e.getFrom().getChunk();
        Chunk to = e.getTo().getChunk();
        if (from.getX() == to.getX() && from.getZ() == to.getZ() && from.getWorld().equals(to.getWorld())) return;
        ClaimManager.Claim claim = claimManager.getClaimAt(to);
        if (claim != null) {
            Player p = e.getPlayer();
            String ownerName = Optional.ofNullable(p.getServer().getOfflinePlayer(claim.getOwner()).getName()).orElse("Bilinmiyor");
            p.sendActionBar(Main.get().messages().getComponent("actionbar.enter_claim", "<yellow>Bu arazi {owner} kişisine aittir</yellow>", "owner", ownerName));
        }
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        // Patlama koruması: Her bir blok için ayrı kontrol yapılmalı
        // Patlamanın merkezi korumasız olabilir ama etkisi korumalı alana taşabilir
        e.blockList().removeIf(block -> {
            ClaimManager.Claim claim = claimManager.getClaimAt(block.getChunk());
            return claim != null && !claim.isAllowExplosions();
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (e.getDamager() instanceof Player) attacker = (Player) e.getDamager();
        else if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player) attacker = (Player) proj.getShooter();
        if (attacker == null) return;
        
        // 1. Kurban güvenli bölgede mi?
        Chunk victimChunk = victim.getLocation().getChunk();
        ClaimManager.Claim victimClaim = claimManager.getClaimAt(victimChunk);
        if (victimClaim != null && !victimClaim.isAllowPvp()) {
            e.setCancelled(true);
            return;
        }

        // 2. Saldırgan güvenli bölgede mi? (Safe-zone camping exploit fix)
        Chunk attackerChunk = attacker.getLocation().getChunk();
        ClaimManager.Claim attackerClaim = claimManager.getClaimAt(attackerChunk);
        if (attackerClaim != null && !attackerClaim.isAllowPvp()) {
            e.setCancelled(true);
            // Mesaj sistemi entegrasyonu varsa mesaj gönderilebilir
            // attacker.sendMessage(ChatColor.RED + "Güvenli bölgeden saldıramazsınız!");
        }
    }

    private boolean isDoorLike(Material m) {
        String n = m.name();
        return n.endsWith("_DOOR") || n.endsWith("TRAPDOOR") || n.endsWith("FENCE_GATE");
    }
    
    // ============ ADVANCED PROTECTION ============
    
    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent e) {
        if (e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) return; // Plugin spawns
        if (e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) return; // Player spawns
        
        Chunk c = e.getLocation().getChunk();
        ClaimManager.Claim claim = claimManager.getClaimAt(c);
        if (claim != null && !claim.isAllowMobSpawn()) {
            e.setCancelled(true);
        }
    }
    
    @EventHandler
    public void onFireSpread(BlockSpreadEvent e) {
        if (e.getSource().getType() != Material.FIRE) return;
        
        Chunk c = e.getBlock().getChunk();
        ClaimManager.Claim claim = claimManager.getClaimAt(c);
        if (claim != null && !claim.isAllowFireSpread()) {
            e.setCancelled(true);
        }
    }
    
    @EventHandler
    public void onFireIgnite(BlockIgniteEvent e) {
        if (e.getCause() == BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL) return; // Player ignite
        
        Chunk c = e.getBlock().getChunk();
        ClaimManager.Claim claim = claimManager.getClaimAt(c);
        if (claim != null && !claim.isAllowFireSpread()) {
            e.setCancelled(true);
        }
    }
    
    @EventHandler
    public void onCropGrow(BlockGrowEvent e) {
        Chunk c = e.getBlock().getChunk();
        ClaimManager.Claim claim = claimManager.getClaimAt(c);
        if (claim != null && !claim.isAllowCropGrowth()) {
            e.setCancelled(true);
        }
    }
    
    @EventHandler
    public void onStructureGrow(StructureGrowEvent e) {
        Chunk c = e.getLocation().getChunk();
        ClaimManager.Claim claim = claimManager.getClaimAt(c);
        if (claim != null && !claim.isAllowCropGrowth()) {
            e.setCancelled(true);
        }
    }
    
    @EventHandler
    public void onLeafDecay(LeavesDecayEvent e) {
        Chunk c = e.getBlock().getChunk();
        ClaimManager.Claim claim = claimManager.getClaimAt(c);
        if (claim != null && !claim.isAllowLeafDecay()) {
            e.setCancelled(true);
        }
    }
    
    @EventHandler
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        // Enderman pickup, falling blocks, etc.
        if (!(e.getEntity() instanceof Enderman)) return;
        
        Chunk c = e.getBlock().getChunk();
        ClaimManager.Claim claim = claimManager.getClaimAt(c);
        if (claim != null && !claim.isAllowMobGrief()) {
            e.setCancelled(true);
        }
    }
    
    @EventHandler
    public void onEntityExplode(EntityExplodeEvent e) {
        // Creeper, TNT, etc.
        Chunk c = e.getLocation().getChunk();
        ClaimManager.Claim claim = claimManager.getClaimAt(c);
        if (claim != null && !claim.isAllowMobGrief()) {
            e.blockList().clear();
        }
    }
}
