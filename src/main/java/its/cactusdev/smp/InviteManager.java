package its.cactusdev.smp;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InviteManager {
    private final Main plugin;
    private final boolean enabled;
    private final long defaultExpiry;
    
    public static class Invite {
        public final UUID inviteId;
        public final String chunkKey;
        public final UUID from;
        public final UUID to;
        public final long expiresAt;
        public final TrustManager.TrustLevel level;
        
        public Invite(UUID inviteId, String chunkKey, UUID from, UUID to, long expiresAt, TrustManager.TrustLevel level) {
            this.inviteId = inviteId;
            this.chunkKey = chunkKey;
            this.from = from;
            this.to = to;
            this.expiresAt = expiresAt;
            this.level = level;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
    
    private final Map<UUID, List<Invite>> pendingInvites = new ConcurrentHashMap<>();
    private final Map<UUID, Invite> activeInvites = new ConcurrentHashMap<>();
    
    public InviteManager(Main plugin) {
        this.plugin = plugin;
        
        plugin.getConfig().addDefault("invite_system.enabled", true);
        plugin.getConfig().addDefault("invite_system.default_expiry_minutes", 10);
        plugin.getConfig().addDefault("invite_system.auto_trust_duration_minutes", 60);
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
        
        this.enabled = plugin.getConfig().getBoolean("invite_system.enabled", true);
        this.defaultExpiry = plugin.getConfig().getLong("invite_system.default_expiry_minutes", 10) * 60 * 1000;
        
        // Cleanup task
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupExpiredInvites();
            }
        }.runTaskTimer(plugin, 20 * 60, 20 * 60); // Her dakika
    }
    
    public Invite sendInvite(UUID from, UUID to, String chunkKey, TrustManager.TrustLevel level) {
        if (!enabled) return null;
        
        // Mevcut davetleri kontrol et
        List<Invite> toInvites = pendingInvites.computeIfAbsent(to, k -> new ArrayList<>());
        
        // Aynı chunk için bekleyen davet varsa iptal et
        toInvites.removeIf(inv -> inv.chunkKey.equals(chunkKey) && inv.from.equals(from));
        
        UUID inviteId = UUID.randomUUID();
        long expiresAt = System.currentTimeMillis() + defaultExpiry;
        Invite invite = new Invite(inviteId, chunkKey, from, to, expiresAt, level);
        
        toInvites.add(invite);
        
        // Oyuncuya bildirim gönder
        Player toPlayer = plugin.getServer().getPlayer(to);
        if (toPlayer != null && toPlayer.isOnline()) {
            Player fromPlayer = plugin.getServer().getPlayer(from);
            String fromName = fromPlayer != null ? fromPlayer.getName() : "Bilinmeyen";
            
            toPlayer.sendMessage(plugin.messages().getComponent("messages.invite_received",
                "<gold>✉ {player} seni claim'ine davet etti!</gold>\n<gray>/claim accept {id} ile kabul et</gray>")
                .replaceText(b -> b.matchLiteral("{player}").replacement(fromName))
                .replaceText(b -> b.matchLiteral("{id}").replacement(inviteId.toString().substring(0, 8))));
        }
        
        return invite;
    }
    
    public boolean acceptInvite(UUID playerId, UUID inviteId) {
        List<Invite> invites = pendingInvites.get(playerId);
        if (invites == null) return false;
        
        Invite invite = invites.stream()
            .filter(inv -> inv.inviteId.equals(inviteId))
            .findFirst()
            .orElse(null);
        
        if (invite == null) return false;
        if (invite.isExpired()) {
            invites.remove(invite);
            return false;
        }
        
        // Daimi trust ekle
        plugin.trustManager().setTrust(invite.chunkKey, playerId, invite.level);
        
        invites.remove(invite);
        activeInvites.put(playerId, invite);
        
        // Otomatik süre dolduktan sonra kaldır
        long autoTrustDuration = plugin.getConfig().getLong("invite_system.auto_trust_duration_minutes", 60) * 60 * 1000;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (activeInvites.get(playerId) == invite) {
                    plugin.trustManager().removeTrust(invite.chunkKey, playerId);
                    activeInvites.remove(playerId);
                    
                    Player player = plugin.getServer().getPlayer(playerId);
                    if (player != null && player.isOnline()) {
                        player.sendMessage(plugin.messages().getComponent("messages.invite_expired",
                            "<yellow>Claim davet süreniz doldu.</yellow>"));
                    }
                }
            }
        }.runTaskLater(plugin, autoTrustDuration / 50);
        
        return true;
    }
    
    public boolean declineInvite(UUID playerId, UUID inviteId) {
        List<Invite> invites = pendingInvites.get(playerId);
        if (invites == null) return false;
        
        return invites.removeIf(inv -> inv.inviteId.equals(inviteId));
    }
    
    public List<Invite> getPendingInvites(UUID playerId) {
        List<Invite> invites = pendingInvites.get(playerId);
        if (invites == null) return Collections.emptyList();
        
        // Süresi dolmuşları temizle
        invites.removeIf(Invite::isExpired);
        return new ArrayList<>(invites);
    }
    
    private void cleanupExpiredInvites() {
        for (List<Invite> invites : pendingInvites.values()) {
            invites.removeIf(Invite::isExpired);
        }
        
        // Boş listeleri temizle
        pendingInvites.entrySet().removeIf(e -> e.getValue().isEmpty());
    }
}
