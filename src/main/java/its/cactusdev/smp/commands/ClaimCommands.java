package its.cactusdev.smp.commands;

import its.cactusdev.smp.Main;
import its.cactusdev.smp.managers.ClaimManager;
import org.bukkit.Chunk;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class ClaimCommands implements CommandExecutor, TabCompleter {
    private final Main plugin;
    
    public ClaimCommands(Main plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Bu komut sadece oyuncular tarafından kullanılabilir!");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length == 0) {
            plugin.menuHandler().openMain(player);
            return true;
        }
        
        String subCmd = args[0].toLowerCase();
        
        switch (subCmd) {
            case "home":
                return handleHome(player, args);
            case "sethome":
                return handleSetHome(player);
            case "trust":
                return handleTrust(player, args);
            case "untrust":
                return handleUntrust(player, args);
            case "sell":
                return handleSell(player, args);
            case "unsell":
                return handleUnsell(player);
            case "market":
                return handleMarket(player);
            case "buy":
                return handleBuy(player, args);
            case "logs":
                return handleLogs(player);
            case "effect":
                return handleEffect(player, args);
            case "reload":
                return handleReload(player);
            case "menu":
                plugin.menuHandler().openMain(player);
                return true;
            case "help":
                sendHelp(player);
                return true;
            default:
                player.sendMessage(plugin.messages().getComponent("messages.invalid_command",
                    "<red>Bilinmeyen komut! /claim help ile yardım alın.</red>"));
                return true;
        }
    }
    
    private boolean handleHome(Player player, String[] args) {
        Chunk chunk = player.getLocation().getChunk();
        ClaimManager.Claim claim = plugin.claims().getClaim(ClaimManager.getChunkKey(chunk));
        
        if (claim == null || !claim.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(plugin.messages().getComponent("messages.not_your_claim",
                "<red>Bu chunk size ait değil!</red>"));
            return true;
        }
        
        plugin.claimHome().teleportHome(player, ClaimManager.getChunkKey(chunk));
        return true;
    }
    
    private boolean handleSetHome(Player player) {
        Chunk chunk = player.getLocation().getChunk();
        ClaimManager.Claim claim = plugin.claims().getClaim(ClaimManager.getChunkKey(chunk));
        
        if (claim == null || !claim.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(plugin.messages().getComponent("messages.not_your_claim",
                "<red>Bu chunk size ait değil!</red>"));
            return true;
        }
        
        if (plugin.claimHome().setHome(player, ClaimManager.getChunkKey(chunk))) {
            player.sendMessage(plugin.messages().getComponent("messages.home_set",
                "<green>✓ Claim home noktası belirlendi!</green>"));
        } else {
            player.sendMessage(plugin.messages().getComponent("messages.home_set_failed",
                "<red>Home noktası ayarlanamadı!</red>"));
        }
        return true;
    }
    
    private boolean handleTrust(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(plugin.messages().getComponent("messages.trust_usage",
                "<yellow>Kullanım: /claim trust <oyuncu> <visitor|builder|manager></yellow>"));
            return true;
        }
        
        Chunk chunk = player.getLocation().getChunk();
        ClaimManager.Claim claim = plugin.claims().getClaim(ClaimManager.getChunkKey(chunk));
        
        if (claim == null || !claim.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(plugin.messages().getComponent("messages.not_your_claim",
                "<red>Bu chunk size ait değil!</red>"));
            return true;
        }
        
        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(plugin.messages().getComponent("messages.player_not_found",
                "<red>Oyuncu bulunamadı!</red>"));
            return true;
        }
        
        TrustManager.TrustLevel level;
        try {
            level = TrustManager.TrustLevel.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(plugin.messages().getComponent("messages.invalid_trust_level",
                "<red>Geçersiz trust seviyesi! (visitor, builder, manager)</red>"));
            return true;
        }
        
        plugin.trustManager().setTrust(ClaimManager.getChunkKey(chunk), target.getUniqueId(), level);
        player.sendMessage(plugin.messages().getComponent("messages.trust_added",
            "<green>✓ {player} artık {level} yetkisine sahip!</green>")
            .replaceText(b -> b.matchLiteral("{player}").replacement(target.getName()))
            .replaceText(b -> b.matchLiteral("{level}").replacement(level.name())));
        
        return true;
    }
    
    private boolean handleUntrust(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.messages().getComponent("messages.untrust_usage",
                "<yellow>Kullanım: /claim untrust <oyuncu></yellow>"));
            return true;
        }
        
        Chunk chunk = player.getLocation().getChunk();
        ClaimManager.Claim claim = plugin.claims().getClaim(ClaimManager.getChunkKey(chunk));
        
        if (claim == null || !claim.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(plugin.messages().getComponent("messages.not_your_claim",
                "<red>Bu chunk size ait değil!</red>"));
            return true;
        }
        
        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(plugin.messages().getComponent("messages.player_not_found",
                "<red>Oyuncu bulunamadı!</red>"));
            return true;
        }
        
        plugin.trustManager().removeTrust(ClaimManager.getChunkKey(chunk), target.getUniqueId());
        player.sendMessage(plugin.messages().getComponent("messages.trust_removed",
            "<green>✓ {player} yetkisi kaldırıldı!</green>")
            .replaceText(b -> b.matchLiteral("{player}").replacement(target.getName())));
        
        return true;
    }
    
    private boolean handleSell(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.messages().getComponent("messages.sell_usage",
                "<yellow>Kullanım: /claim sell <fiyat></yellow>"));
            return true;
        }
        
        Chunk chunk = player.getLocation().getChunk();
        ClaimManager.Claim claim = plugin.claims().getClaim(ClaimManager.getChunkKey(chunk));
        
        if (claim == null || !claim.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(plugin.messages().getComponent("messages.not_your_claim",
                "<red>Bu chunk size ait değil!</red>"));
            return true;
        }
        
        double price;
        try {
            price = Double.parseDouble(args[1]);
            if (price <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.messages().getComponent("messages.invalid_price",
                "<red>Geçersiz fiyat!</red>"));
            return true;
        }
        
        if (plugin.market().listClaim(ClaimManager.getChunkKey(chunk), price)) {
            player.sendMessage(plugin.messages().getComponent("messages.claim_listed",
                "<green>✓ Claim {price} fiyata satışa çıkarıldı!</green>")
                .replaceText(b -> b.matchLiteral("{price}").replacement(String.valueOf(price))));
        } else {
            player.sendMessage(plugin.messages().getComponent("messages.claim_list_failed",
                "<red>Claim satışa çıkarılamadı!</red>"));
        }
        
        return true;
    }
    
    private boolean handleUnsell(Player player) {
        Chunk chunk = player.getLocation().getChunk();
        ClaimManager.Claim claim = plugin.claims().getClaim(ClaimManager.getChunkKey(chunk));
        
        if (claim == null || !claim.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(plugin.messages().getComponent("messages.not_your_claim",
                "<red>Bu chunk size ait değil!</red>"));
            return true;
        }
        
        if (plugin.market().unlistClaim(ClaimManager.getChunkKey(chunk))) {
            player.sendMessage(plugin.messages().getComponent("messages.claim_unlisted",
                "<green>✓ Claim satıştan kaldırıldı!</green>"));
        } else {
            player.sendMessage(plugin.messages().getComponent("messages.claim_not_listed",
                "<yellow>Bu claim satışta değil!</yellow>"));
        }
        
        return true;
    }
    
    private boolean handleMarket(Player player) {
        plugin.menuHandler().openMarketMenu(player);
        return true;
    }
    
    private boolean handleBuy(Player player, String[] args) {
        player.sendMessage(plugin.messages().getComponent("messages.buy_via_menu",
            "<yellow>Claim satın almak için ilgili chunk'a gidin ve menüyü açın.</yellow>"));
        return true;
    }
    
    private boolean handleReload(Player player) {
        if (!player.hasPermission("claim.admin.reload")) {
            player.sendMessage(plugin.messages().getComponent("messages.no_permission",
                "<red>Bu komutu kullanma yetkiniz yok!</red>"));
            return true;
        }
        
        plugin.reloadConfig();
        player.sendMessage(plugin.messages().getComponent("messages.config_reloaded",
            "<green>✓ Config yeniden yüklendi!</green>"));
        return true;
    }
    
    private boolean handleLogs(Player player) {
        Chunk chunk = player.getLocation().getChunk();
        ClaimManager.Claim claim = plugin.claims().getClaim(ClaimManager.getChunkKey(chunk));
        
        if (claim == null || !claim.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(plugin.messages().getComponent("messages.not_your_claim",
                "<red>Bu chunk size ait değil!</red>"));
            return true;
        }
        
        List<ActivityLogger.LogEntry> logs = plugin.activityLogger().getRecentLogs(ClaimManager.getChunkKey(chunk), 10);
        
        if (logs.isEmpty()) {
            player.sendMessage(plugin.messages().getComponent("messages.no_logs",
                "<yellow>Bu claim için log kaydı yok!</yellow>"));
            return true;
        }
        
        player.sendMessage(plugin.messages().getComponent("messages.logs_header",
            "<gold>===== Claim Logları =====</gold>"));
        
        for (ActivityLogger.LogEntry log : logs) {
            String playerName = plugin.getServer().getOfflinePlayer(log.playerUuid).getName();
            player.sendMessage(plugin.messages().getComponent("messages.log_entry",
                "<gray>[{time}] <white>{player}</white>: {action}</gray>")
                .replaceText(b -> b.matchLiteral("{time}").replacement(formatTime(log.timestamp)))
                .replaceText(b -> b.matchLiteral("{player}").replacement(playerName))
                .replaceText(b -> b.matchLiteral("{action}").replacement(log.details)));
        }
        
        return true;
    }
    
    private boolean handleEffect(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.messages().getComponent("messages.effect_usage",
                "<yellow>Kullanım: /claim effect <spiral|helix|ring|fountain|pulse|stop></yellow>"));
            return true;
        }
        
        Chunk chunk = player.getLocation().getChunk();
        ClaimManager.Claim claim = plugin.claims().getClaim(ClaimManager.getChunkKey(chunk));
        
        if (claim == null || !claim.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(plugin.messages().getComponent("messages.not_your_claim",
                "<red>Bu chunk size ait değil!</red>"));
            return true;
        }
        
        String effectType = args[1].toLowerCase();
        
        if (effectType.equals("stop")) {
            plugin.claimEffects().stopEffect(ClaimManager.getChunkKey(chunk));
            player.sendMessage(plugin.messages().getComponent("messages.effect_stopped",
                "<yellow>Efekt durduruldu.</yellow>"));
            return true;
        }
        
        try {
            ClaimEffects.EffectType type = ClaimEffects.EffectType.valueOf(effectType.toUpperCase());
            plugin.claimEffects().startEffect(claim, type);
            player.sendMessage(plugin.messages().getComponent("messages.effect_started",
                "<green>✓ {effect} efekti başlatıldı!</green>")
                .replaceText(b -> b.matchLiteral("{effect}").replacement(effectType)));
        } catch (IllegalArgumentException e) {
            player.sendMessage(plugin.messages().getComponent("messages.invalid_effect",
                "<red>Geçersiz efekt tipi!</red>"));
        }
        
        return true;
    }
    
    private void sendHelp(Player player) {
        player.sendMessage(plugin.messages().getComponent("messages.help_header",
            "<gold>===== Claim Komutları =====</gold>"));
        player.sendMessage(plugin.messages().getComponent("messages.help_home",
            "<yellow>/claim home</yellow> <gray>- Claim home'a ışınlan</gray>"));
        player.sendMessage(plugin.messages().getComponent("messages.help_sethome",
            "<yellow>/claim sethome</yellow> <gray>- Claim home noktası belirle</gray>"));
        player.sendMessage(plugin.messages().getComponent("messages.help_trust",
            "<yellow>/claim trust <oyuncu> <level></yellow> <gray>- Oyuncuya yetki ver</gray>"));
        player.sendMessage(plugin.messages().getComponent("messages.help_untrust",
            "<yellow>/claim untrust <oyuncu></yellow> <gray>- Oyuncunun yetkisini kaldır</gray>"));
        player.sendMessage(plugin.messages().getComponent("messages.help_sell",
            "<yellow>/claim sell <fiyat></yellow> <gray>- Claim'i satışa çıkar</gray>"));
        player.sendMessage(plugin.messages().getComponent("messages.help_market",
            "<yellow>/claim market</yellow> <gray>- Satılık claim'leri göster</gray>"));
        player.sendMessage(plugin.messages().getComponent("messages.help_logs",
            "<yellow>/claim logs</yellow> <gray>- Claim loglarını göster</gray>"));
        player.sendMessage(plugin.messages().getComponent("messages.help_effect",
            "<yellow>/claim effect <tip></yellow> <gray>- Claim efekti başlat</gray>"));
    }
    
    private String formatTime(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        long minutes = diff / (60 * 1000);
        long hours = diff / (60 * 60 * 1000);
        long days = diff / (24 * 60 * 60 * 1000);
        
        if (days > 0) return days + "g";
        if (hours > 0) return hours + "s";
        if (minutes > 0) return minutes + "d";
        return "şimdi";
    }
    
    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("home", "sethome", "trust", "untrust", "sell", "market", "logs", "effect")
                .stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        if (args.length == 2 && args[0].equalsIgnoreCase("trust")) {
            return null; // Player names
        }
        
        if (args.length == 3 && args[0].equalsIgnoreCase("trust")) {
            return Arrays.asList("visitor", "builder", "manager")
                .stream()
                .filter(s -> s.startsWith(args[2].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        if (args.length == 2 && args[0].equalsIgnoreCase("effect")) {
            return Arrays.asList("spiral", "helix", "ring", "fountain", "pulse", "stop")
                .stream()
                .filter(s -> s.startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        return Collections.emptyList();
    }
}
