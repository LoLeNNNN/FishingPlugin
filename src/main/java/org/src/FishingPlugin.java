package org.src;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.*;

public class FishingPlugin extends JavaPlugin implements Listener {
    private final double CONFIG_VERSION = 1.0;
    private LocaleManager localeManager;
    private LootManager lootManager;
    private JavaPlugin plugin;
    private Set<UUID> FishingStatus = new HashSet<>();

    public FishingPlugin() {
    }

    @Override
    public void onEnable() {
        getServer().getLogger().info("[FishingPlugin] Hello, world!");
        this.localeManager = new LocaleManager(this);
        this.plugin = this;
        Bukkit.getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        updateConfig();

        // Initialize LootManager after config is loaded
        this.lootManager = new LootManager(this);

        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }
    }
    private void updateConfig() {
        FileConfiguration config = getConfig();


        double currentConfigVersion = config.getDouble("config-version", 1.0);

        if (currentConfigVersion < CONFIG_VERSION) {
            getLogger().info("Updated config upto " + CONFIG_VERSION);
            config.set("config-version", CONFIG_VERSION);
            saveConfig();
        }
    }

    @Override
    public void onDisable() {
        getServer().getLogger().info("[FishingPlugin] Bye, world!");
    }

    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            event.setCancelled(true);
            Entity caught = event.getCaught();
            if (caught instanceof Item) {
                ItemStack caughtItem = ((Item) caught).getItemStack();

                // Get Luck of the Sea level from fishing rod
                ItemStack fishingRod = player.getInventory().getItemInMainHand();
                int luckLevel = 0;
                if (fishingRod != null && fishingRod.getType() == org.bukkit.Material.FISHING_ROD) {
                    luckLevel = fishingRod.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.LUCK_OF_THE_SEA);
                }

                // Try to replace vanilla item with custom loot (FIX for Issue #3)
                // Now with Luck of the Sea support!
                ItemStack customLoot = lootManager.tryReplaceVanillaItem(caughtItem, luckLevel);
                if (customLoot != null) {
                    caughtItem = customLoot;
                }

                if (!skillCheckTasks.containsKey(event.getPlayer().getUniqueId())) {
                    if (!FishingStatus.contains(playerId)) {
                        FishingStatus.add(playerId);
                        startSkillCheck(event.getPlayer(), caughtItem);
                    }
                }
            }
        }
        if (event.getState() == PlayerFishEvent.State.BITE) {
            if (FishingStatus.contains(playerId)){
                FishingStatus.remove(playerId);
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getAction().name().contains("RIGHT")) {
            if (skillCheckTasks.containsKey(player.getUniqueId())) {
                skillCheckTasks.get(player.getUniqueId()).checkInput();
            }
        }
    }

    private final Map<UUID, SkillCheckTask> skillCheckTasks = new HashMap<>();

    private void startSkillCheck(Player player, ItemStack caughtItem) {
        SkillCheckTask task = new SkillCheckTask(player, caughtItem);
        skillCheckTasks.put(player.getUniqueId(), task);
        task.runTaskTimer(this, 0L, 1L);
    }

    private class SkillCheckTask extends BukkitRunnable {
        private final Player player;
        private final ItemStack caughtItem;
        private int cursorPos = 0;
        private int direction = 1;
        private final int barLength = 50;
        private final int successStart;
        private final int successEnd;
        private boolean isActive = true;

        public SkillCheckTask(Player player, ItemStack caughtItem) {
            this.player = player;
            this.caughtItem = caughtItem;
            int successZoneLength = barLength / 4;
            successStart = (int) (Math.random() * (barLength - successZoneLength));
            successEnd = successStart + successZoneLength;
        }

        @Override
        public void run() {
            if (!isActive) {
                return;
            }

            cursorPos += direction;
            if (cursorPos >= barLength || cursorPos <= 0) {
                direction *= -1;
            }

            StringBuilder bar = new StringBuilder();
            for (int i = 0; i < barLength; i++) {
                if (i == cursorPos) {
                    bar.append(ChatColor.GRAY + "v");
                } else if (i >= successStart && i <= successEnd) {
                    bar.append(ChatColor.GREEN + "|");
                } else {
                    bar.append(ChatColor.RED + "|");
                }
            }

            player.sendActionBar(bar.toString());
        }

        public void checkInput() {
            if (!isActive) {
                return;
            }

            isActive = false;
            this.cancel();

            if (cursorPos >= successStart && cursorPos <= successEnd) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                player.sendMessage(localeManager.getMessage(player, "fishing_success", caughtItem.getType().name()));
                player.getInventory().addItem(caughtItem);
                player.giveExp(plugin.getConfig().getInt("config.ExperiencePerCatch"));
            } else {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                player.sendMessage(localeManager.getMessage(player, "fishing_failed"));
            }
            skillCheckTasks.remove(player.getUniqueId());
        }
    }
}

//todo Опыт, разный шанс рыбалки(в зависимости от предмета, импорт уникальных предметов из config.yml)