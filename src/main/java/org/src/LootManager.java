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
    private final Map<ItemStack, LootEntry> lootTable = new HashMap<>();
    private final Map<String, Integer> customRarityLevels = new HashMap<>();
    private double totalWeight = 0.0;

    // Inner class to store loot entry data
    private static class LootEntry {
        final double baseWeight;
        final int rarity; // Numeric rarity level (higher = rarer)

        LootEntry(double baseWeight, int rarity) {
            this.baseWeight = baseWeight;
            this.rarity = rarity;
        }
    }

    public LootManager(FishingPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        loadCustomRarities();
        loadLootTable();
    }

    /**
     * Loads custom rarity definitions from config
     */
    private void loadCustomRarities() {
        customRarityLevels.clear();

        // Load default rarities for backward compatibility
        customRarityLevels.put("common", 1);
        customRarityLevels.put("uncommon", 2);
        customRarityLevels.put("rare", 3);
        customRarityLevels.put("epic", 4);
        customRarityLevels.put("legendary", 5);

        // Load custom rarities from config if defined
        ConfigurationSection raritiesSection = plugin.getConfig().getConfigurationSection("loot.custom-rarities");
        if (raritiesSection != null) {
            for (String rarityName : raritiesSection.getKeys(false)) {
                int level = raritiesSection.getInt(rarityName);
                customRarityLevels.put(rarityName.toLowerCase(), level);
                logger.info("Loaded custom rarity: " + rarityName + " = " + level);
            }
        }
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
                int rarity = parseRarity(itemSection.getString("rarity", "common"));

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

                lootTable.put(item, new LootEntry(weight, rarity));
                totalWeight += weight;
                logger.info("Loaded loot: " + material.name() + " with weight " + weight + " and rarity " + rarity);

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
        lootTable.put(new ItemStack(Material.COD), new LootEntry(40.0, 1));
        lootTable.put(new ItemStack(Material.SALMON), new LootEntry(30.0, 1));
        lootTable.put(new ItemStack(Material.TROPICAL_FISH), new LootEntry(20.0, 2));
        lootTable.put(new ItemStack(Material.PUFFERFISH), new LootEntry(10.0, 3));
        totalWeight = 100.0;
    }

    /**
     * Parses rarity value to integer level
     * Supports:
     * 1. Direct numeric values (e.g., "1", "10", "100")
     * 2. Named rarities from custom-rarities config (e.g., "mythic", "divine")
     * 3. Default rarities (common, uncommon, rare, epic, legendary)
     *
     * @param rarityStr The rarity string or number
     * @return Rarity level (numeric value, higher = rarer)
     */
    private int parseRarity(String rarityStr) {
        if (rarityStr == null) return 1;

        // Try parsing as direct number first
        try {
            int directValue = Integer.parseInt(rarityStr);
            if (directValue < 1) {
                logger.warning("Rarity level must be >= 1, got: " + directValue + ", defaulting to 1");
                return 1;
            }
            return directValue;
        } catch (NumberFormatException e) {
            // Not a number, try looking up as named rarity
            String lowerRarity = rarityStr.toLowerCase();
            if (customRarityLevels.containsKey(lowerRarity)) {
                return customRarityLevels.get(lowerRarity);
            } else {
                logger.warning("Unknown rarity: " + rarityStr + ", defaulting to common (1)");
                return 1;
            }
        }
    }

    /**
     * Gets a random item from the loot table based on weighted probabilities.
     * FIX for Issue #3: Proper null checking to prevent NullPointerException
     *
     * @param luckLevel Luck of the Sea enchantment level (0-3)
     * @return A random ItemStack from the loot table, or null if table is empty
     */
    public ItemStack getRandomItem(int luckLevel) {
        if (lootTable.isEmpty()) {
            logger.warning("Loot table is empty! Cannot get random item.");
            return null;
        }

        if (totalWeight <= 0) {
            logger.warning("Total weight is zero or negative! Cannot get random item.");
            return null;
        }

        // Get luck multiplier from config
        double luckMultiplier = plugin.getConfig().getDouble("loot.luck-multiplier-per-level", 0.25);

        // Calculate modified weights based on luck and rarity
        Map<ItemStack, Double> modifiedWeights = new HashMap<>();
        double modifiedTotalWeight = 0.0;

        for (Map.Entry<ItemStack, LootEntry> entry : lootTable.entrySet()) {
            LootEntry lootEntry = entry.getValue();
            if (lootEntry == null) {
                logger.warning("Found null loot entry for item: " + entry.getKey().getType());
                continue;
            }

            // Formula: higher rarity gets bigger boost from luck
            // modifiedWeight = baseWeight * (1 + luckLevel * luckMultiplier * (rarity - 1))
            double rarityBonus = luckLevel * luckMultiplier * (lootEntry.rarity - 1);
            double modifiedWeight = lootEntry.baseWeight * (1.0 + rarityBonus);

            modifiedWeights.put(entry.getKey(), modifiedWeight);
            modifiedTotalWeight += modifiedWeight;
        }

        // Select random item based on modified weights
        double random = Math.random() * modifiedTotalWeight;
        double currentWeight = 0.0;

        for (Map.Entry<ItemStack, Double> entry : modifiedWeights.entrySet()) {
            Double weight = entry.getValue();
            if (weight == null) {
                logger.warning("Found null weight in modified weights for item: " + entry.getKey().getType());
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
     * Gets a random item without luck bonus (for backward compatibility)
     * @return A random ItemStack from the loot table
     */
    public ItemStack getRandomItem() {
        return getRandomItem(0);
    }

    /**
     * Tries to replace vanilla fishing item with custom loot.
     * FIX for Issue #3: Added null checks and proper error handling
     * Now supports Luck of the Sea enchantment for better rare item chances
     *
     * @param vanillaItem The vanilla caught item
     * @param luckLevel Luck of the Sea enchantment level (0-3)
     * @return Custom loot item if replacement should occur, null otherwise
     */
    public ItemStack tryReplaceVanillaItem(ItemStack vanillaItem, int luckLevel) {
        if (vanillaItem == null) {
            return null;
        }

        // Get replacement chance from config
        double replacementChance = plugin.getConfig().getDouble("loot.replacement-chance", 0.5);

        if (Math.random() < replacementChance) {
            return getRandomItem(luckLevel);
        }

        return null;
    }

    /**
     * Backward compatibility method without luck level
     */
    public ItemStack tryReplaceVanillaItem(ItemStack vanillaItem) {
        return tryReplaceVanillaItem(vanillaItem, 0);
    }

    /**
     * Reloads the loot table and custom rarities from config
     */
    public void reload() {
        logger.info("Reloading loot manager...");
        loadCustomRarities();
        loadLootTable();
        logger.info("Loot table reloaded with " + lootTable.size() + " entries (total weight: " + totalWeight + ")");
        logger.info("Custom rarities loaded: " + customRarityLevels.size());
    }

    /**
     * Gets the current loot table with base weights (without luck modifiers)
     */
    public Map<ItemStack, Double> getLootTable() {
        Map<ItemStack, Double> baseWeights = new HashMap<>();
        for (Map.Entry<ItemStack, LootEntry> entry : lootTable.entrySet()) {
            baseWeights.put(entry.getKey(), entry.getValue().baseWeight);
        }
        return baseWeights;
    }

    /**
     * Gets the total weight of all items in the loot table
     */
    public double getTotalWeight() {
        return totalWeight;
    }
}
