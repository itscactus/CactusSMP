package its.cactusdev.smp.listeners;

import its.cactusdev.smp.Main;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ChatInput implements Listener {
    private static final Map<UUID, Consumer<String>> waiters = new ConcurrentHashMap<>();

    static {
        Bukkit.getPluginManager().registerEvents(new ChatInput(), Main.get());
    }

    public static void waitFor(UUID uuid, Consumer<String> handler) {
        waiters.put(uuid, handler);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Consumer<String> c = waiters.remove(e.getPlayer().getUniqueId());
        if (c != null) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(Main.get(), () -> c.accept(e.getMessage()));
        }
    }
}
