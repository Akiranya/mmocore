package net.Indyuce.mmocore.experience.droptable;

import io.lumine.mythic.lib.api.MMOLineConfig;
import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.quest.trigger.Trigger;
import org.apache.commons.lang.Validate;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ExperienceItem {
    private final String id;
    private final int period;
    private final double claimChance, failReduction;
    private final List<Trigger> triggers;

    private static final Random random = new Random();

    /**
     * One item for an experience table
     *
     * @param period        The experience item is claimed every X level ups
     * @param claimChance   Chance for that item to be claimed every X level ups
     * @param failReduction Between 0 and 1, by how much the fail chance is reduced
     *                      every time the item is not claimed when leveling up.
     *                      <p>
     *                      Failing chance follows a geometric sequence therefore
     *                      <code>successChance = 1 - (1 - initialSuccessChance) * failReduction^n</code>
     *                      where n is the amount of successive claiming fails
     * @param triggers      Actions cast when the exp item is claimed
     */
    public ExperienceItem(String id, int period, double claimChance, double failReduction, List<Trigger> triggers) {
        this.id = id;
        this.period = period;
        this.claimChance = claimChance;
        this.failReduction = failReduction;
        this.triggers = triggers;
    }

    public ExperienceItem(ConfigurationSection config) {
        Validate.notNull(config, "Config cannot be null");
        Validate.isTrue(config.contains("triggers"));
        id = config.getName();
        period = config.getInt("period", 1);
        claimChance = config.getDouble("chance", 100) / 100;
        failReduction = config.getDouble("fail-reduction", 80) / 100;
        triggers = new ArrayList<>();

        for (String triggerFormat : config.getStringList("triggers"))
            triggers.add(MMOCore.plugin.loadManager.loadTrigger(new MMOLineConfig(triggerFormat)));
    }

    public String getId() {
        return id;
    }

    /**
     * @param professionLevel The profession level the player just reached
     * @param timesCollected  Amount of times the exp item has already been claimed by the player
     * @return If the item should be claimed right now taking into
     *         account the randomness factor from the 'chance' parameter
     */
    public boolean roll(int professionLevel, int timesCollected) {
        int claimsRequired = professionLevel - (timesCollected + 1) * period;
        if (claimsRequired < 1)
            return false;

        double chance = 1 - (1 - claimChance) * Math.pow(failReduction, claimsRequired);
        return random.nextDouble() < chance;
    }

    public void applyTriggers(PlayerData levelingUp) {
        for (Trigger trigger : triggers)
            trigger.apply(levelingUp);
    }
}