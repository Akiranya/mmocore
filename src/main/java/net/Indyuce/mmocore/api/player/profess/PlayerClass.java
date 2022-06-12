package net.Indyuce.mmocore.api.player.profess;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.UtilityMethods;
import io.lumine.mythic.lib.api.MMOLineConfig;
import io.lumine.mythic.lib.api.util.PostLoadObject;
import io.lumine.mythic.lib.version.VersionMaterial;
import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.player.profess.event.EventTrigger;
import net.Indyuce.mmocore.api.player.profess.resource.ManaDisplayOptions;
import net.Indyuce.mmocore.api.player.profess.resource.PlayerResource;
import net.Indyuce.mmocore.api.player.profess.resource.ResourceRegeneration;
import net.Indyuce.mmocore.api.util.MMOCoreUtils;
import net.Indyuce.mmocore.api.util.math.formula.LinearValue;
import net.Indyuce.mmocore.experience.EXPSource;
import net.Indyuce.mmocore.experience.ExpCurve;
import net.Indyuce.mmocore.experience.ExperienceObject;
import net.Indyuce.mmocore.experience.droptable.ExperienceTable;
import net.Indyuce.mmocore.experience.source.type.ExperienceSource;
import net.Indyuce.mmocore.loot.chest.particle.CastingParticle;
import net.Indyuce.mmocore.player.playerclass.ClassTrigger;
import net.Indyuce.mmocore.player.playerclass.ClassTriggerType;
import net.Indyuce.mmocore.player.stats.StatInfo;
import net.Indyuce.mmocore.skill.ClassSkill;
import net.Indyuce.mmocore.skill.RegisteredSkill;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Level;

public class PlayerClass extends PostLoadObject implements ExperienceObject {
    private final String name, id, actionBarFormat;
    private final List<String> description = new ArrayList<>(), attrDescription = new ArrayList<>();
    private final ItemStack icon;
    private final Map<ClassOption, Boolean> options = new HashMap<>();
    private final ManaDisplayOptions manaDisplay;
    private final int maxLevel, displayOrder;
    private final ExpCurve expCurve;
    private final ExperienceTable expTable;

    private final Map<String, LinearValue> stats = new HashMap<>();
    private final Map<String, ClassSkill> skills = new LinkedHashMap<>();
    private final List<Subclass> subclasses = new ArrayList<>();

    @Deprecated
    private final Map<String, ClassTrigger> classTriggers = new HashMap<>();

    private final Map<PlayerResource, ResourceRegeneration> resourceHandlers = new HashMap<>();

    @Deprecated
    private final Map<String, EventTrigger> eventTriggers = new HashMap<>();

    private final CastingParticle castParticle;

    public PlayerClass(String id, FileConfiguration config) {
        super(config);

        this.id = id.toUpperCase().replace("-", "_").replace(" ", "_");

        name = MythicLib.plugin.parseColors(config.getString("display.name", "INVALID DISPLAY NAME"));
        icon = MMOCoreUtils.readIcon(config.getString("display.item", "BARRIER"));

        if (config.contains("display.texture") && icon.getType() == VersionMaterial.PLAYER_HEAD.toMaterial())
            try {
                ItemMeta meta = icon.getItemMeta();
                Field profileField = meta.getClass().getDeclaredField("profile");
                profileField.setAccessible(true);
                GameProfile gp = new GameProfile(UUID.randomUUID(), null);
                gp.getProperties().put("textures", new Property("textures", config.getString("display.texture")));
                profileField.set(meta, gp);
                icon.setItemMeta(meta);
            } catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException
                    | SecurityException exception) {
                throw new IllegalArgumentException("Could not apply playerhead texture: " + exception.getMessage());
            }

        for (String string : config.getStringList("display.lore"))
            description.add(ChatColor.GRAY + MythicLib.plugin.parseColors(string));
        for (String string : config.getStringList("display.attribute-lore"))
            attrDescription.add(ChatColor.GRAY + MythicLib.plugin.parseColors(string));
        manaDisplay = config.contains("mana") ? new ManaDisplayOptions(config.getConfigurationSection("mana"))
                : ManaDisplayOptions.DEFAULT;
        maxLevel = config.getInt("max-level");
        displayOrder = config.getInt("display.order");
        actionBarFormat = config.contains("action-bar", true) ? config.getString("action-bar") : null;

        expCurve = config.contains("exp-curve")
                ? MMOCore.plugin.experience.getCurveOrThrow(
                config.get("exp-curve").toString().toLowerCase().replace("_", "-").replace(" ", "-"))
                : ExpCurve.DEFAULT;

        ExperienceTable expTable = null;
        if (config.contains("exp-table"))
            try {
                expTable = MMOCore.plugin.experience.loadExperienceTable(config.get("exp-table"));
            } catch (RuntimeException exception) {
                MMOCore.plugin.getLogger().log(Level.WARNING, "Could not load exp table from class '" + id + "': " + exception.getMessage());
            }
        this.expTable = expTable;

        if (config.contains("triggers"))
            for (String key : config.getConfigurationSection("triggers").getKeys(false)) {
                try {
                    String format = key.toLowerCase().replace("_", "-").replace(" ", "-");
                    eventTriggers.put(format, new EventTrigger(format, config.getStringList("triggers." + key)));
                } catch (IllegalArgumentException exception) {
                    MMOCore.log(Level.WARNING, "Could not load trigger '" + key + "' from class '" + id + "':" + exception.getMessage());
                }
            }

        if (config.contains("attributes"))
            for (String key : config.getConfigurationSection("attributes").getKeys(false))
                try {
                    stats.put(UtilityMethods.enumName(key),
                            new LinearValue(config.getConfigurationSection("attributes." + key)));
                } catch (IllegalArgumentException exception) {
                    MMOCore.plugin.getLogger().log(Level.WARNING, "Could not load stat info '" + key + "' from class '"
                            + id + "': " + exception.getMessage());
                }

        if (config.contains("skills"))
            for (String key : config.getConfigurationSection("skills").getKeys(false))
                try {
                    RegisteredSkill registered = MMOCore.plugin.skillManager.getSkillOrThrow(UtilityMethods.enumName(key));
                    skills.put(registered.getHandler().getId(), new ClassSkill(registered, config.getConfigurationSection("skills." + key)));
                } catch (RuntimeException exception) {
                    MMOCore.plugin.getLogger().log(Level.WARNING, "Could not load skill info '" + key + "' from class '"
                            + id + "': " + exception.getMessage());
                }

        castParticle = config.contains("cast-particle")
                ? new CastingParticle(config.getConfigurationSection("cast-particle"))
                : new CastingParticle(Particle.SPELL_INSTANT);

        if (config.contains("options"))
            for (String key : config.getConfigurationSection("options").getKeys(false))
                try {
                    setOption(ClassOption.valueOf(key.toUpperCase().replace("-", "_").replace(" ", "_")),
                            config.getBoolean("options." + key));
                } catch (IllegalArgumentException exception) {
                    MMOCore.plugin.getLogger().log(Level.WARNING,
                            "Could not load option '" + key + "' from class '" + key + "': " + exception.getMessage());
                }

        if (config.contains("main-exp-sources")) {
            for (String key : config.getStringList("main-exp-sources"))
                try {
                    ExperienceSource<?> source = MMOCore.plugin.loadManager.loadExperienceSource(new MMOLineConfig(key), this);
                    MMOCore.plugin.experience.registerSource(source);
                } catch (IllegalArgumentException exception) {
                    MMOCore.plugin.getLogger().log(Level.WARNING, "Could not load exp source '" + key + "' from class '"
                            + id + "': " + exception.getMessage());
                }
        }

        /*
         * Must make sure all the resourceHandlers are registered
         * when the placer class is initialized.
         */
        for (PlayerResource resource : PlayerResource.values()) {
            if (config.isConfigurationSection("resource." + resource.name().toLowerCase()))
                try {
                    resourceHandlers.put(resource, new ResourceRegeneration(resource,
                            config.getConfigurationSection("resource." + resource.name().toLowerCase())));
                } catch (IllegalArgumentException exception) {
                    MMOCore.log(Level.WARNING, "Could not load special " + resource.name().toLowerCase() + " regen from class '"
                            + id + "': " + exception.getMessage());
                    resourceHandlers.put(resource, new ResourceRegeneration(resource));
                }
            else
                resourceHandlers.put(resource, new ResourceRegeneration(resource));
        }
    }

    /**
     * Used to generate the default Human class if no one is
     * specified after loading all the player classes. This is
     * a very basic class that will make sure MMOCore can still
     * continue to run without having to stop the server because
     * some option was not provided
     */
    public PlayerClass(String id, String name, Material material) {
        super(null);

        this.id = id;
        this.name = name;
        manaDisplay = ManaDisplayOptions.DEFAULT;
        maxLevel = 0;
        displayOrder = 0;
        expCurve = ExpCurve.DEFAULT;
        expTable = null;
        castParticle = new CastingParticle(Particle.SPELL_INSTANT);
        actionBarFormat = "";

        this.icon = new ItemStack(material);
        setOption(ClassOption.DISPLAY, false);
        setOption(ClassOption.DEFAULT, false);

        for (PlayerResource resource : PlayerResource.values())
            resourceHandlers.put(resource, new ResourceRegeneration(resource));
    }

    @Override
    protected void whenPostLoaded(ConfigurationSection config) {
        if (config.contains("subclasses"))
            for (String key : config.getConfigurationSection("subclasses").getKeys(false))
                try {
                    subclasses.add(new Subclass(
                            MMOCore.plugin.classManager
                                    .getOrThrow(key.toUpperCase().replace("-", "_").replace(" ", "_")),
                            config.getInt("subclasses." + key)));
                } catch (IllegalArgumentException exception) {
                    MMOCore.plugin.getLogger().log(Level.WARNING, "Could not load subclass '" + key + "' from class '"
                            + getId() + "': " + exception.getMessage());
                }
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public String getKey() {
        return "class." + getId();
    }

    @NotNull
    public ManaDisplayOptions getManaDisplay() {
        return manaDisplay;
    }

    @NotNull
    public ResourceRegeneration getHandler(PlayerResource resource) {
        return resourceHandlers.get(resource);
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    @Override
    public ExpCurve getExpCurve() {
        return expCurve;
    }

    @NotNull
    public ExperienceTable getExperienceTable() {
        return Objects.requireNonNull(expTable, "Class has no exp table");
    }

    @Override
    public boolean hasExperienceTable() {
        return expTable != null;
    }

    public ItemStack getIcon() {
        return icon.clone();
    }

    public CastingParticle getCastParticle() {
        return castParticle;
    }

    public List<String> getDescription() {
        return description;
    }

    public List<String> getAttributeDescription() {
        return attrDescription;
    }

    public void setOption(ClassOption option, boolean value) {
        options.put(option, value);
    }

    public boolean hasOption(ClassOption option) {
        return options.containsKey(option) ? options.get(option) : option.getDefault();
    }

    @Override
    public void giveExperience(PlayerData playerData, double experience, @Nullable Location hologramLocation, EXPSource source) {
        hologramLocation = !MMOCore.plugin.getConfig().getBoolean("display-main-class-exp-holograms") ? null
                : hologramLocation;
        playerData.giveExperience(experience, source, hologramLocation, true);
    }

    @Override
    public boolean shouldHandle(PlayerData playerData) {
        return equals(playerData.getProfess());
    }

    @Nullable
    @Deprecated
    public ClassTrigger getClassTrigger(ClassTriggerType type) {
        return classTriggers.get(type);
    }

    @Deprecated
    public Set<String> getEventTriggers() {
        return eventTriggers.keySet();
    }

    @Deprecated
    public boolean hasEventTriggers(String name) {
        return eventTriggers.containsKey(name);
    }

    @Deprecated
    public EventTrigger getEventTriggers(String name) {
        return eventTriggers.get(name);
    }

    public void setDefaultStatFormula(String type, LinearValue value) {
        stats.put(UtilityMethods.enumName(type), value);
    }

    public double calculateStat(String stat, int level) {
        return getStatInfo(stat).calculate(level);
    }

    public List<Subclass> getSubclasses() {
        return subclasses;
    }

    /**
     * Recursive method which checks if the given class
     * is a child of the current class in the 'subclass tree'.
     *
     * @param profess Some player class
     * @return If given class is a subclass of the current class
     */
    public boolean hasSubclass(PlayerClass profess) {
        for (Subclass sub : subclasses)
            if (sub.getProfess().equals(profess) || sub.getProfess().hasSubclass(profess))
                return true;
        return false;
    }

    public boolean hasSkill(RegisteredSkill skill) {
        return hasSkill(skill.getHandler().getId());
    }

    public boolean hasSkill(String id) {
        return skills.containsKey(id);
    }

    public ClassSkill getSkill(RegisteredSkill skill) {
        return getSkill(skill.getHandler().getId());
    }

    /**
     * Reduces map checkups when skills are being checked on events that are
     * commonly called like EntityDamageEvent or regen events.
     * <p>
     * Examples:
     * - {@link net.Indyuce.mmocore.skill.list.Neptune_Gift}
     * - {@link net.Indyuce.mmocore.skill.list.Ambers}
     */
    public Optional<ClassSkill> findSkill(RegisteredSkill skill) {
        ClassSkill found = skills.get(skill.getHandler().getId());
        return found == null ? Optional.empty() : Optional.of(found);
    }

    @Nullable
    public ClassSkill getSkill(String id) {
        return skills.get(id);
    }

    public Collection<ClassSkill> getSkills() {
        return skills.values();
    }

    @NotNull
    private LinearValue getStatInfo(String stat) {
        LinearValue found = stats.get(stat);
        return found == null ? StatInfo.valueOf(stat).getDefaultFormula() : found;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof PlayerClass && ((PlayerClass) obj).id.equals(id);
    }

    public String getActionBar() {
        return actionBarFormat;
    }

    public boolean hasActionBar() {
        return actionBarFormat != null;
    }
}
