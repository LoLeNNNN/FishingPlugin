package org.src;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class LocaleManager {
    private final JavaPlugin plugin;
    private final Map<String, YamlConfiguration> locales = new HashMap<>();

    public LocaleManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadLocales();
    }

    private void loadLocales() {
        File localeFolder = new File(plugin.getDataFolder(), "locales");
        if (!localeFolder.exists()) {
            localeFolder.mkdirs();
        }

        for (String locale : new String[]{"en", "ru"}) {
            File localeFile = new File(localeFolder, "messages_" + locale + ".yml");
            if (!localeFile.exists()) {
                plugin.saveResource("locales/messages_" + locale + ".yml", false);
            }
            locales.put(locale, YamlConfiguration.loadConfiguration(localeFile));
        }
    }

    private String translateColors(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public String getMessage(Player player, String key, Object... args) {
        String localeCode = player.getLocale().split("_")[0];
        YamlConfiguration locale = locales.getOrDefault(localeCode, locales.get("en"));

        String message = locale.getString(key, "Message not found: " + key);

        for (int i = 0; i < args.length; i++) {
            message = message.replace("{" + i + "}", String.valueOf(args[i]));
        }

        return translateColors(message);
    }
}