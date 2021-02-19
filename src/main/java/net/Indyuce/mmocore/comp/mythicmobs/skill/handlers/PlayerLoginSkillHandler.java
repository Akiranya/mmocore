package net.Indyuce.mmocore.comp.mythicmobs.skill.handlers;

import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.comp.mythicmobs.skill.MythicMobSkill;
import net.Indyuce.mmocore.comp.mythicmobs.skill.PassiveMythicMobSkillHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerLoginEvent;

public class PlayerLoginSkillHandler extends PassiveMythicMobSkillHandler {
    /**
     * Core class for all passive types
     *
     * @param skill
     */
    public PlayerLoginSkillHandler(MythicMobSkill skill) {
        super(skill);
    }

    @EventHandler
    private void event(PlayerLoginEvent e){
            castSkill(PlayerData.get((Player) e));
    }
}
