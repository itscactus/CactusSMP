package its.cactusdev.smp;

import org.bukkit.scheduler.BukkitRunnable;

public class ExpiryTask extends BukkitRunnable {
    private final ClaimManager claimManager;

    public ExpiryTask(ClaimManager cm, Database db) {
        this.claimManager = cm;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        // Silinmeden önce claim'leri al
        var expiredClaims = claimManager.getExpiredClaims(now);
        for (ClaimManager.Claim claim : expiredClaims) {
            // Claim stone'u kaldır
            Main.get().claimStones().removeClaimStone(claim);
        }
        claimManager.removeExpired(now);
    }
}
