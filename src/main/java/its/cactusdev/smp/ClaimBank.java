package its.cactusdev.smp;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ClaimBank {
    private final Main plugin;
    private final Database db;
    private final ActivityLogger logger;
    
    public static class Transaction {
        public final int id;
        public final String chunkKey;
        public final UUID playerUuid;
        public final String type; // DEPOSIT, WITHDRAW
        public final double amount;
        public final double balanceBefore;
        public final double balanceAfter;
        public final long timestamp;
        
        public Transaction(int id, String chunkKey, UUID playerUuid, String type, double amount, 
                          double balanceBefore, double balanceAfter, long timestamp) {
            this.id = id;
            this.chunkKey = chunkKey;
            this.playerUuid = playerUuid;
            this.type = type;
            this.amount = amount;
            this.balanceBefore = balanceBefore;
            this.balanceAfter = balanceAfter;
            this.timestamp = timestamp;
        }
    }
    
    public ClaimBank(Main plugin, Database db, ActivityLogger logger) {
        this.plugin = plugin;
        this.db = db;
        this.logger = logger;
    }
    
    /**
     * Get current balance of a claim
     */
    public double getBalance(String chunkKey) {
        String sql = "SELECT balance FROM claim_banks WHERE chunk_key = ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, chunkKey);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getDouble("balance");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0.0;
    }
    
    /**
     * Deposit money into claim bank
     */
    public boolean deposit(Player player, String chunkKey, double amount) {
        if (amount <= 0) {
            player.sendMessage(plugin.messages().getComponent("messages.bank_invalid_amount", 
                "<red>Geçersiz miktar!</red>"));
            return false;
        }
        
        // Check if player has enough money
        if (!plugin.getEconomy().has(player, amount)) {
            player.sendMessage(plugin.messages().getComponent("messages.bank_insufficient_funds", 
                "<red>Yeterli paran yok!</red>"));
            return false;
        }
        
        double currentBalance = getBalance(chunkKey);
        
        try {
            // Withdraw from player
            if (!plugin.getEconomy().withdrawPlayer(player, amount).transactionSuccess()) {
                return false;
            }
            
            // Update claim bank
            String sql = "INSERT INTO claim_banks (chunk_key, balance, last_transaction) VALUES (?, ?, ?) " +
                        "ON CONFLICT(chunk_key) DO UPDATE SET balance = balance + ?, last_transaction = ?";
            try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
                long now = System.currentTimeMillis();
                ps.setString(1, chunkKey);
                ps.setDouble(2, amount);
                ps.setLong(3, now);
                ps.setDouble(4, amount);
                ps.setLong(5, now);
                ps.executeUpdate();
            }
            
            // Log transaction
            logTransaction(chunkKey, player.getUniqueId(), "DEPOSIT", amount, currentBalance, currentBalance + amount);
            logger.log(chunkKey, player.getUniqueId(), "BANK_DEPOSIT", 
                String.format("%.2f TL yatırdı (Yeni bakiye: %.2f TL)", amount, currentBalance + amount));
            
            player.sendMessage(plugin.messages().getComponent("messages.bank_deposit_success", 
                "<green>✓ Claim bankasına {amount} TL yatırıldı!</green>", 
                java.util.Map.of("amount", String.format("%.2f", amount))));
            
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            // Refund player on error
            plugin.getEconomy().depositPlayer(player, amount);
            return false;
        }
    }
    
    /**
     * Withdraw money from claim bank
     */
    public boolean withdraw(Player player, String chunkKey, double amount) {
        if (amount <= 0) {
            player.sendMessage(plugin.messages().getComponent("messages.bank_invalid_amount", 
                "<red>Geçersiz miktar!</red>"));
            return false;
        }
        
        double currentBalance = getBalance(chunkKey);
        
        if (currentBalance < amount) {
            player.sendMessage(plugin.messages().getComponent("messages.bank_insufficient_balance", 
                "<red>Claim bankasında yeterli para yok!</red>"));
            return false;
        }
        
        try {
            // Update claim bank
            String sql = "UPDATE claim_banks SET balance = balance - ?, last_transaction = ? WHERE chunk_key = ?";
            try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
                ps.setDouble(1, amount);
                ps.setLong(2, System.currentTimeMillis());
                ps.setString(3, chunkKey);
                ps.executeUpdate();
            }
            
            // Deposit to player
            plugin.getEconomy().depositPlayer(player, amount);
            
            // Log transaction
            logTransaction(chunkKey, player.getUniqueId(), "WITHDRAW", amount, currentBalance, currentBalance - amount);
            logger.log(chunkKey, player.getUniqueId(), "BANK_WITHDRAW", 
                String.format("%.2f TL çekti (Yeni bakiye: %.2f TL)", amount, currentBalance - amount));
            
            player.sendMessage(plugin.messages().getComponent("messages.bank_withdraw_success", 
                "<green>✓ Claim bankasından {amount} TL çekildi!</green>", 
                java.util.Map.of("amount", String.format("%.2f", amount))));
            
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Get transaction history for a claim
     */
    public List<Transaction> getTransactions(String chunkKey, int limit) {
        List<Transaction> transactions = new ArrayList<>();
        String sql = "SELECT * FROM claim_bank_transactions WHERE chunk_key = ? ORDER BY timestamp DESC LIMIT ?";
        
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, chunkKey);
            ps.setInt(2, limit);
            ResultSet rs = ps.executeQuery();
            
            while (rs.next()) {
                transactions.add(new Transaction(
                    rs.getInt("id"),
                    rs.getString("chunk_key"),
                    UUID.fromString(rs.getString("player_uuid")),
                    rs.getString("type"),
                    rs.getDouble("amount"),
                    rs.getDouble("balance_before"),
                    rs.getDouble("balance_after"),
                    rs.getLong("timestamp")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return transactions;
    }
    
    /**
     * Log a transaction to database
     */
    private void logTransaction(String chunkKey, UUID playerUuid, String type, double amount, 
                                double balanceBefore, double balanceAfter) {
        String sql = "INSERT INTO claim_bank_transactions (chunk_key, player_uuid, type, amount, balance_before, balance_after, timestamp) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, chunkKey);
            ps.setString(2, playerUuid.toString());
            ps.setString(3, type);
            ps.setDouble(4, amount);
            ps.setDouble(5, balanceBefore);
            ps.setDouble(6, balanceAfter);
            ps.setLong(7, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Delete claim bank when claim is deleted
     */
    public void deleteBank(String chunkKey) {
        try {
            String sql1 = "DELETE FROM claim_banks WHERE chunk_key = ?";
            try (PreparedStatement ps = db.getConnection().prepareStatement(sql1)) {
                ps.setString(1, chunkKey);
                ps.executeUpdate();
            }
            
            String sql2 = "DELETE FROM claim_bank_transactions WHERE chunk_key = ?";
            try (PreparedStatement ps = db.getConnection().prepareStatement(sql2)) {
                ps.setString(1, chunkKey);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
