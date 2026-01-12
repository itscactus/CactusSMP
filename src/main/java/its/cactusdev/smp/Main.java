package its.cactusdev.smp;

import its.cactusdev.smp.commands.ClaimCommands;
import its.cactusdev.smp.data.Database;
import its.cactusdev.smp.features.BorderEffects;
import its.cactusdev.smp.features.ClaimBank;
import its.cactusdev.smp.features.ClaimEffects;
import its.cactusdev.smp.features.ClaimHome;
import its.cactusdev.smp.features.ClaimMarket;
import its.cactusdev.smp.listeners.ProtectionListener;
import its.cactusdev.smp.managers.ClaimManager;
import its.cactusdev.smp.managers.ClaimStoneManager;
import its.cactusdev.smp.managers.PreviewManager;
import its.cactusdev.smp.managers.TrustManager;
import its.cactusdev.smp.menus.MenuHandler;
import its.cactusdev.smp.tasks.ExpiryTask;
import its.cactusdev.smp.utils.ActivityLogger;
import its.cactusdev.smp.utils.Messages;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Collections;
import java.util.List;

public final class Main extends JavaPlugin {

    private static Main instance;
    private Database database;
    private ClaimManager claimManager;
    private MenuHandler menuHandler;
    private PreviewManager previewManager;
    private Messages messages;
    private Economy economy;
    private ClaimStoneManager claimStoneManager;
    
    // Yeni sistemler
    private ClaimHome claimHome;
    private TrustManager trustManager;
    private ClaimMarket claimMarket;
    private BorderEffects borderEffects;
    private ClaimEffects claimEffects;
    private ActivityLogger activityLogger;
    private ClaimBank claimBank;

    public static Main get() { return instance; }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        if (!setupEconomy()) {
            getLogger().severe("Vault bulunamadı veya ekonomi sağlayıcısı yok. Plugin devre dışı bırakılıyor.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.database = new Database(this);
        this.database.init();
        this.claimManager = new ClaimManager(database);
        this.messages = new Messages(this);
        this.previewManager = new PreviewManager(this);
        this.menuHandler = new MenuHandler(this, claimManager, previewManager, messages);
        this.claimStoneManager = new ClaimStoneManager(this, claimManager, messages);
        
        // Yeni sistemleri başlat
        this.claimHome = new ClaimHome(this, database);
        this.trustManager = new TrustManager(this, database);
        this.claimMarket = new ClaimMarket(this, database);
        this.borderEffects = new BorderEffects(this);
        this.claimEffects = new ClaimEffects(this);
        this.activityLogger = new ActivityLogger(this, database);
        this.claimBank = new ClaimBank(this, database, activityLogger);

        // Paper: register command programmatically (no YAML, no PluginCommand subclass)
        ClaimCommands claimCommands = new ClaimCommands(this);
        registerCommand("claim", claimCommands);

        Bukkit.getPluginManager().registerEvents(new ProtectionListener(this, claimManager), this);
        
        // Mevcut claim'ler için claim stone oluştur
        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (ClaimManager.Claim claim : claimManager.getAllClaims()) {
                claimStoneManager.updateOrCreateStone(claim);
            }
        }, 40L); // 2 saniye sonra (chunk'lar yüklensin)

        new ExpiryTask(claimManager, database).runTaskTimerAsynchronously(this, 20L, 20L * 60L * 60L); // hourly
    }

    @Override
    public void onDisable() {
        if (claimStoneManager != null) claimStoneManager.cleanup();
        if (database != null) database.close();
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    public Economy economy() { return economy; }
    public ClaimManager claims() { return claimManager; }
    public PreviewManager preview() { return previewManager; }
    public Messages messages() { return messages; }
    public MenuHandler menuHandler() { return menuHandler; }
    public ClaimStoneManager claimStones() { return claimStoneManager; }
    public ClaimHome claimHome() { return claimHome; }
    public TrustManager trustManager() { return trustManager; }
    public ClaimMarket market() { return claimMarket; }
    public BorderEffects borderEffects() { return borderEffects; }
    public ClaimEffects claimEffects() { return claimEffects; }
    public ActivityLogger activityLogger() { return activityLogger; }
    public ClaimBank claimBank() { return claimBank; }
    public Economy getEconomy() { return economy; }
    public ActivityLogger getActivityLogger() { return activityLogger; }

    private void registerCommand(String name, CommandExecutor executor) {
        try {
            Object server = Bukkit.getServer();
            java.lang.reflect.Method m = server.getClass().getMethod("getCommandMap");
            org.bukkit.command.CommandMap map = (org.bukkit.command.CommandMap) m.invoke(server);
            map.register("cactussmp", new SimpleBukkitCommand(name, executor));
        } catch (Exception ex) {
            getLogger().severe("Komut kaydı başarısız: " + ex.getMessage());
        }
    }

    private static class SimpleBukkitCommand extends org.bukkit.command.Command {
        private final CommandExecutor executor;
        protected SimpleBukkitCommand(String name, CommandExecutor executor) {
            super(name);
            this.executor = executor;
            setDescription("Claim ana menüsü");
            setPermission("cactus.claim");
        }
        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            String perm = getPermission();
            if (perm != null && !perm.isEmpty() && !sender.hasPermission(perm)) {
                sender.sendMessage("Bu komutu kullanamazsınız.");
                return true;
            }
            return executor.onCommand(sender, this, label, args);
        }
        @Override
        public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
            if (executor instanceof TabCompleter tc) {
                List<String> res = tc.onTabComplete(sender, this, alias, args);
                return res != null ? res : Collections.emptyList();
            }
            return Collections.emptyList();
        }
    }
}
