package net.Indyuce.mmocore.gui.api;

import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.UtilityMethods;
import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.gui.api.adaptor.AdaptorType;
import net.Indyuce.mmocore.gui.api.item.InventoryItem;
import net.Indyuce.mmocore.gui.api.item.TriggerItem;
import org.apache.commons.lang.Validate;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.text.DecimalFormat;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Level;

public abstract class EditableInventory {
    private final String id;

    private FileConfiguration config;
    private AdaptorType adaptorType;
    private String name;
    private int slots;

    /*
     * This set is linked so it keeps the order/priority in
     * which the items are loaded from the config.
     */
    private final Set<InventoryItem> items = new LinkedHashSet<>();

    protected static final DecimalFormat decimal = MythicLib.plugin.getMMOConfig().decimal;

    public EditableInventory(String id) {
        this.id = id;
        Validate.notNull(id, "ID must not be null");
    }

    public void reload(FileConfiguration config) {
        this.config = config;
        this.adaptorType = AdaptorType.valueOf(UtilityMethods.enumName(config.getString("adaptor-type", "classic-adaptor")));
        Validate.notNull(adaptorType, config.getString("adaptor-type") + " does not correspond to an adaptor-type.");//TODO

        this.name = config.getString("name");
        Validate.notNull(name, "Name must not be null");

        this.slots = Math.min(Math.max(9, config.getInt("slots")), 54);
        Validate.isTrue((slots % 9) == 0, "Slots must be a multiple of 9");

        items.clear();
        if (config.contains("items")) {
            Validate.notNull(config.getConfigurationSection("items"), "Could not load item list");
            for (String key : config.getConfigurationSection("items").getKeys(false))
                try {
                    ConfigurationSection section = config.getConfigurationSection("items." + key);
                    Validate.notNull(section, "Could not load config");
                    InventoryItem loaded = loadInventoryItem(section);
                    items.add(loaded);
                } catch (IllegalArgumentException exception) {
                    MMOCore.log(Level.WARNING, "Could not load item '" + key + "' from inventory '" + getId() + "': " + exception.getMessage());
                }
        }
    }

    public String getId() {
        return id;
    }

    public Set<InventoryItem> getItems() {
        return items;
    }

    public String getName() {
        return name;
    }

    public int getSlots() {
        return slots;
    }

    public AdaptorType getAdaptorType() {
        return adaptorType;
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public InventoryItem getByFunction(String function) {
        for (InventoryItem item : items)
            if (item.getFunction().equals(function))
                return item;
        return null;
    }

    public abstract InventoryItem load(String function, ConfigurationSection config);

    private InventoryItem loadInventoryItem(ConfigurationSection config) {
        String function = config.contains("function") ? config.getString("function").toLowerCase() : "";

        if (function.startsWith("trigger:"))
            return new TriggerItem(config, function.substring(8));

        return load(function, config);
    }
}
