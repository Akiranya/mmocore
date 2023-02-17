package net.Indyuce.mmocore.manager.data.mysql;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.sql.DataSynchronizer;
import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.player.profess.PlayerClass;
import net.Indyuce.mmocore.api.player.profess.SavedClassInformation;
import net.Indyuce.mmocore.api.util.MMOCoreUtils;
import net.Indyuce.mmocore.guild.provided.Guild;
import net.Indyuce.mmocore.skill.ClassSkill;
import net.Indyuce.mmocore.skilltree.SkillTreeNode;
import net.Indyuce.mmocore.skilltree.tree.SkillTree;
import org.apache.commons.lang.Validate;
import org.jetbrains.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class MMOCoreDataSynchronizer extends DataSynchronizer {
    private final PlayerData data;
    private final MySQLPlayerDataManager manager;

    public MMOCoreDataSynchronizer(MySQLPlayerDataManager manager, PlayerData data) {
        super("mmocore_playerdata", "uuid", manager.getProvider(), data.getUniqueId());

        this.manager = manager;
        this.data = data;
    }

    @Override
    public void loadData(ResultSet result) throws SQLException {

        // Initialize custom resources
        data.setMana(result.getFloat("mana"));
        data.setStellium(result.getFloat("stellium"));
        data.setStamina(result.getFloat("stamina"));

        data.setClassPoints(result.getInt("class_points"));
        data.setSkillPoints(result.getInt("skill_points"));
        data.setSkillReallocationPoints(result.getInt("skill_reallocation_points"));
        data.setSkillTreeReallocationPoints(result.getInt("skill_tree_reallocation_points"));
        data.setAttributePoints(result.getInt("attribute_points"));
        data.setAttributeReallocationPoints(result.getInt("attribute_realloc_points"));
        data.setLevel(result.getInt("level"));
        data.setExperience(result.getInt("experience"));

        if (!isEmpty(result.getString("class")))
            data.setClass(MMOCore.plugin.classManager.get(result.getString("class")));

        if (!isEmpty(result.getString("times_claimed"))) {
            JsonObject json = new JsonParser().parse(result.getString("times_claimed")).getAsJsonObject();
            json.entrySet().forEach(entry -> data.getItemClaims().put(entry.getKey(), entry.getValue().getAsInt()));
        }
        if (!isEmpty(result.getString("skill_tree_points"))) {
            JsonObject json = new JsonParser().parse(result.getString("skill_tree_points")).getAsJsonObject();
            for (SkillTree skillTree : MMOCore.plugin.skillTreeManager.getAll()) {
                data.setSkillTreePoints(skillTree.getId(), json.has(skillTree.getId()) ? json.get(skillTree.getId()).getAsInt() : 0);
            }
            data.setSkillTreePoints("global", json.has("global") ? json.get("global").getAsInt() : 0);

        }
        if (!isEmpty(result.getString("skill_tree_levels"))) {
            JsonObject json = new JsonParser().parse(result.getString("skill_tree_levels")).getAsJsonObject();
            for (SkillTreeNode skillTreeNode : MMOCore.plugin.skillTreeManager.getAllNodes()) {
                data.setNodeLevel(skillTreeNode, json.has(skillTreeNode.getFullId()) ? json.get(skillTreeNode.getFullId()).getAsInt() : 0);
            }
        }
        data.setupSkillTree();


        if (!isEmpty(result.getString("guild"))) {
            Guild guild = MMOCore.plugin.dataProvider.getGuildManager().getGuild(result.getString("guild"));
            data.setGuild(guild.hasMember(data.getUniqueId()) ? guild : null);
        }
        if (!isEmpty(result.getString("attributes")))
            data.getAttributes().load(result.getString("attributes"));
        if (!isEmpty(result.getString("professions")))
            data.getCollectionSkills().load(result.getString("professions"));
        if (!isEmpty(result.getString("quests")))
            data.getQuestData().load(result.getString("quests"));
        data.getQuestData().updateBossBar();
        if (!isEmpty(result.getString("waypoints")))
            data.getWaypoints().addAll(MMOCoreUtils.jsonArrayToList(result.getString("waypoints")));
        if (!isEmpty(result.getString("friends")))
            MMOCoreUtils.jsonArrayToList(result.getString("friends")).forEach(str -> data.getFriends().add(UUID.fromString(str)));
        if (!isEmpty(result.getString("skills"))) {
            JsonObject object = new Gson().fromJson(result.getString("skills"), JsonObject.class);
            for (Map.Entry<String, JsonElement> entry : object.entrySet())
                data.setSkillLevel(entry.getKey(), entry.getValue().getAsInt());
        }
        if (!isEmpty(result.getString("bound_skills")))
            for (String id : MMOCoreUtils.jsonArrayToList(result.getString("bound_skills")))
                if (data.getProfess().hasSkill(id)) {
                    ClassSkill skill = data.getProfess().getSkill(id);
                    if (skill.getSkill().getTrigger().isPassive())
                        data.bindPassiveSkill(-1, skill.toPassive(data));
                    else
                        data.getBoundSkills().add(skill);
                }
        if (!isEmpty(result.getString("class_info"))) {
            JsonObject object = new Gson().fromJson(result.getString("class_info"), JsonObject.class);
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                try {
                    PlayerClass profess = MMOCore.plugin.classManager.get(entry.getKey());
                    Validate.notNull(profess, "Could not find class '" + entry.getKey() + "'");
                    data.applyClassInfo(profess, new SavedClassInformation(entry.getValue().getAsJsonObject()));
                } catch (IllegalArgumentException exception) {
                    MMOCore.log(Level.WARNING, "Could not load class info '" + entry.getKey() + "': " + exception.getMessage());
                }
            }
        }

        MythicLib.debug("MMOCoreSQL", String.format("{ class: %s, level: %d }", data.getProfess().getId(), data.getLevel()));
        data.setFullyLoaded();
    }

    private boolean isEmpty(@Nullable String str) {
        return str == null || str.equalsIgnoreCase("null") || str.equalsIgnoreCase("{}") || str.equalsIgnoreCase("[]") || str.equalsIgnoreCase("");
    }

    @Override
    public void loadEmptyData() {
        data.setLevel(manager.getDefaultData().getLevel());
        data.setClassPoints(manager.getDefaultData().getClassPoints());
        data.setSkillPoints(manager.getDefaultData().getSkillPoints());
        data.setSkillReallocationPoints(manager.getDefaultData().getSkillReallocationPoints());
        data.setAttributePoints(manager.getDefaultData().getAttributePoints());
        data.setAttributeReallocationPoints(manager.getDefaultData().getAttributeReallocationPoints());
        data.setExperience(0);
        data.getQuestData().updateBossBar();

        data.setFullyLoaded();
        MythicLib.debug("MMOCoreSQL", "Loaded DEFAULT data for: '" + data.getUniqueId() + "' as no saved data was found.");
    }
}