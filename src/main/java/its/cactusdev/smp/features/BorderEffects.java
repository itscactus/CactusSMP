package its.cactusdev.smp.features;

import its.cactusdev.smp.Main;
import its.cactusdev.smp.managers.ClaimManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BorderEffects implements Listener {
    private final Main plugin;
    private final Map<UUID, String> lastChunk = new HashMap<>();
    
    private final boolean enabled;
    private final boolean showTitle;
    private final boolean playSound;
    private final String soundType;
    private final float soundVolume;
    private final float soundPitch;
    
    public BorderEffects(Main plugin) {
        this.plugin = plugin;
        
        // Config defaults
        plugin.getConfig().addDefault("border_effects.enabled", true);
        plugin.getConfig().addDefault("border_effects.show_title", true);
        plugin.getConfig().addDefault("border_effects.play_sound", true);
        plugin.getConfig().addDefault("border_effects.sound_type", "ENTITY_EXPERIENCE_ORB_PICKUP");
        plugin.getConfig().addDefault("border_effects.sound_volume", 0.7);
        plugin.getConfig().addDefault("border_effects.sound_pitch", 1.2);
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
        
        this.enabled = plugin.getConfig().getBoolean("border_effects.enabled", true);
        this.showTitle = plugin.getConfig().getBoolean("border_effects.show_title", true);
        this.playSound = plugin.getConfig().getBoolean("border_effects.play_sound", true);
        this.soundType = plugin.getConfig().getString("border_effects.sound_type", "ENTITY_EXPERIENCE_ORB_PICKUP");
        this.soundVolume = (float) plugin.getConfig().getDouble("border_effects.sound_volume", 0.7);
        this.soundPitch = (float) plugin.getConfig().getDouble("border_effects.sound_pitch", 1.2);
        
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!enabled) return;
        
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        
        // Sadece chunk değişiminde kontrol et
        if (from.getChunk().equals(to.getChunk())) return;
        
        Player player = event.getPlayer();
        Chunk toChunk = to.getChunk();
        String chunkKey = ClaimManager.getChunkKey(toChunk);
        
        // Aynı chunk'a tekrar girdiyse efekt gösterme
        String lastKey = lastChunk.get(player.getUniqueId());
        if (chunkKey.equals(lastKey)) return;
        
        lastChunk.put(player.getUniqueId(), chunkKey);
        
        ClaimManager.Claim claim = plugin.claims().getClaim(chunkKey);
        
        if (claim != null) {
            showClaimEntry(player, claim);
        } else {
            showWildernessEntry(player);
        }
    }
    
    private void showClaimEntry(Player player, ClaimManager.Claim claim) {
        final String claimName;
        if (claim.getClaimName() == null || claim.getClaimName().isEmpty()) {
            claimName = plugin.getServer().getOfflinePlayer(claim.getOwner()).getName() + "'ın Claimi";
        } else {
            claimName = claim.getClaimName();
        }
        
        boolean isOwner = claim.getOwner().equals(player.getUniqueId());
        
        // Sadece başkasının claim'ine girince göster
        if (!isOwner) {
            Component actionBar = plugin.messages().getComponent("messages.border_entry_claim",
                "<gradient:#ff5555:#aa0000><b>{name}</b></gradient>")
                .replaceText(b -> b.matchLiteral("{name}").replacement(claimName));
            
            player.sendActionBar(actionBar);
            
            if (playSound) {
                try {
                    player.playSound(player.getLocation(), org.bukkit.Sound.valueOf(soundType), soundVolume, soundPitch);
                } catch (Exception e) {
                    plugin.getLogger().warning("Invalid sound type: " + soundType);
                }
            }
        }
    }
    
    private void showWildernessEntry(Player player) {
        // Korumasız bölgeye girince hiçbir şey gösterme
    }
}
