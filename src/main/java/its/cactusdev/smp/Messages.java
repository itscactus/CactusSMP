package its.cactusdev.smp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class Messages {
    private final Main plugin;
    private FileConfiguration cfg;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public Messages(Main plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        try {
            File data = plugin.getDataFolder();
            if (!data.exists()) data.mkdirs();
            File file = new File(data, "Messages.yml");
            if (!file.exists()) {
                try (InputStream in = plugin.getResource("Messages.yml")) {
                    if (in != null) Files.copy(in, file.toPath());
                    else file.createNewFile();
                }
            }
            this.cfg = YamlConfiguration.loadConfiguration(file);
        } catch (Exception e) {
            throw new RuntimeException("Messages.yml yüklenemedi", e);
        }
    }

    public String get(String path, String def) {
        return cfg.getString(path, def);
    }

    public Component getComponent(String path, String def) {
        String text;
        if (path == null || path.isEmpty()) {
            // Path boşsa direkt def değerini kullan
            text = def;
        } else {
            text = get(path, def);
        }
        if (text == null || text.isEmpty()) {
            text = def != null ? def : "";
        }
        return miniMessage.deserialize(text);
    }

    public Component getComponent(String path, String def, Map<String, String> placeholders) {
        String text;
        if (path == null || path.isEmpty()) {
            // Path boşsa direkt def değerini kullan
            text = def;
        } else {
            text = get(path, def);
        }
        if (text == null || text.isEmpty()) {
            text = def != null ? def : "";
        }
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                text = text.replace("{" + e.getKey() + "}", e.getValue());
            }
        }
        return miniMessage.deserialize(text);
    }

    public Component getComponent(String path, String def, Object... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), String.valueOf(kv[i+1]));
        }
        return getComponent(path, def, m);
    }

    public String format(String path, Map<String, String> vars, String def) {
        String s = get(path, def);
        if (vars == null) return s;
        for (Map.Entry<String,String> e : vars.entrySet()) {
            s = s.replace("{" + e.getKey() + "}", e.getValue());
        }
        return s;
    }

    public String format(String path, String def, Object... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), String.valueOf(kv[i+1]));
        }
        return format(path, m, def);
    }
}
