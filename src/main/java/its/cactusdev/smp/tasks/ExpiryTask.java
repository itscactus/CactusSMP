package its.cactusdev.smp.tasks;

import its.cactusdev.smp.Main;
import its.cactusdev.smp.managers.ClaimManager;
import its.cactusdev.smp.data.Database;
import org.bukkit.scheduler.BukkitRunnable;

public class ExpiryTask extends BukkitRunnable {
    private final ClaimManager claimManager;

    public ExpiryTask(ClaimManager cm, Database db) {
        this.claimManager = cm;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        // Silinmeden önce claim'leri al (Async DB okuma - OK)
        var expiredClaims = claimManager.getExpiredClaims(now);
        
        if (!expiredClaims.isEmpty()) {
            // Claim stone'u kaldır (Sync olmalı, entity/block işlemi)
            Main.get().getServer().getScheduler().runTask(Main.get(), () -> {
                for (ClaimManager.Claim claim : expiredClaims) {
                    Main.get().claimStones().removeClaimStone(claim);
                }
            });
        }
        
        // Veritabanından ve cache'den sil (Async - OK)
        claimManager.removeExpired(now);
    }
}
