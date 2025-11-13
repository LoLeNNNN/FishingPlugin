package org.src;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.logging.Logger;

public class LootManager {
    private final FishingPlugin plugin;
    private final Logger logger;
    private final Map<ItemStack, Double> lootTable = new HashMap<>();
    private double totalWeight = 0.0;

    public LootManager(FishingPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        loadLootTable();
    }

    private void loadLootTable() {
        lootTable.clear();
        totalWeight = 0.0;

        ConfigurationSection lootSection = plugin.getConfig().getConfigurationSection("loot");
        if (lootSection == null) {
            logger.warning("No loot section found in config.yml! Using default loot.");
            addDefaultLoot();
            return;
        }

        for (String key : lootSection.getKeys(false)) {
            ConfigurationSection itemSection = lootSection.getConfigurationSection(key);
            if (itemSection == null) {
                logger.warning("Invalid loot entry: " + key);
                continue;
            }

            try {
                Material material = Material.valueOf(itemSection.getString("material", "COD").toUpperCase());
                int amount = itemSection.getInt("amount", 1);
                double weight = itemSection.getDouble("weight", 1.0);
                String displayName = itemSection.getString("displayName");
                List<String> lore = itemSection.getStringList("lore");

                ItemStack item = new ItemStack(material, amount);
                ItemMeta meta = item.getItemMeta();

                if (meta != null) {
                    if (displayName != null && !displayName.isEmpty()) {
                        meta.setDisplayName(displayName);
                    }
                    if (!lore.isEmpty()) {
                        meta.setLore(lore);
                    }

                    // Enchantments
                    ConfigurationSection enchantSection = itemSection.getConfigurationSection("enchantments");
                    if (enchantSection != null) {
                        for (String enchantKey : enchantSection.getKeys(false)) {
                            try {
                                Enchantment enchant = Enchantment.getByName(enchantKey.toUpperCase());
                                if (enchant != null) {
                                    int level = enchantSection.getInt(enchantKey);
                                    meta.addEnchant(enchant, level, true);
                                }
                            } catch (Exception e) {
                                logger.warning("Invalid enchantment: " + enchantKey);
                            }
                        }
                    }

                    item.setItemMeta(meta);
                }

                lootTable.put(item, weight);
                totalWeight += weight;
                logger.info("Loaded loot: " + material.name() + " with weight " + weight);

            } catch (IllegalArgumentException e) {
                logger.warning("Invalid material in loot entry: " + key);
            } catch (Exception e) {
                logger.warning("Error loading loot entry " + key + ": " + e.getMessage());
            }
        }

        if (lootTable.isEmpty()) {
            logger.warning("No valid loot entries found! Using default loot.");
            addDefaultLoot();
        }
    }

    private void addDefaultLoot() {
        lootTable.put(new ItemStack(Material.COD), 40.0);
        lootTable.put(new ItemStack(Material.SALMON), 30.0);
        lootTable.put(new ItemStack(Material.TROPICAL_FISH), 20.0);
        lootTable.put(new ItemStack(Material.PUFFERFISH), 10.0);
        totalWeight = 100.0;
    }

    /**
     * Gets a random item from the loot table based on weighted probabilities.
     * FIX for Issue #3: Proper null checking to prevent NullPointerException
     *
     * @return A random ItemStack from the loot table, or null if table is empty
     */
    public ItemStack getRandomItem() {
        if (lootTable.isEmpty()) {
            logger.warning("Loot table is empty! Cannot get random item.");
            return null;
        }

        if (totalWeight <= 0) {
            logger.warning("Total weight is zero or negative! Cannot get random item.");
            return null;
        }

        double random = Math.random() * totalWeight;
        double currentWeight = 0.0;

        for (Map.Entry<ItemStack, Double> entry : lootTable.entrySet()) {
            // FIX: Check if weight is null before calling doubleValue()
            Double weight = entry.getValue();
            if (weight == null) {
                logger.warning("Found null weight in loot table for item: " + entry.getKey().getType());
                continue;
            }

            currentWeight += weight;
            if (random <= currentWeight) {
                return entry.getKey().clone();
            }
        }

        // Fallback: return the last item if somehow we didn't return anything
        ItemStack fallback = lootTable.keySet().iterator().next();
        logger.warning("Weighted random selection failed, returning fallback item: " + fallback.getType());
        return fallback.clone();
    }

    /**
     * Tries to replace vanilla fishing item with custom loot.
     * FIX for Issue #3: Added null checks and proper error handling
     *
     * @param vanillaItem The vanilla caught item
     * @return Custom loot item if replacement should occur, null otherwise
     */
    public ItemStack tryReplaceVanillaItem(ItemStack vanillaItem) {
        if (vanillaItem == null) {
            return null;
        }

        // Get replacement chance from config
        double replacementChance = plugin.getConfig().getDouble("loot.replacement-chance", 0.5);

        if (Math.random() < replacementChance) {
            return getRandomItem();
        }

        return null;
    }

    /**
     * Reloads the loot table from config
     */
    public void reload() {
        logger.info("Reloading loot table...");
        loadLootTable();
        logger.info("Loot table reloaded with " + lootTable.size() + " entries (total weight: " + totalWeight + ")");
    }

    /**
     * Gets the current loot table
     */
    public Map<ItemStack, Double> getLootTable() {
        return new HashMap<>(lootTable);
    }

    /**
     * Gets the total weight of all items in the loot table
     */
    public double getTotalWeight() {
        return totalWeight;
    }
}
