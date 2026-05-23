package me.suxuan.animalhide.skill;

import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.skill.hider.DecoyToggleSkill;
import me.suxuan.animalhide.skill.hider.DisguiseInvisibilitySkill;
import me.suxuan.animalhide.skill.hider.DisguiseWandSkill;
import me.suxuan.animalhide.skill.hider.PartyTauntSkill;
import me.suxuan.animalhide.skill.hider.PoopTauntSkill;
import me.suxuan.animalhide.skill.hider.ScreamTauntSkill;
import me.suxuan.animalhide.skill.hider.StinkyTauntSkill;
import me.suxuan.animalhide.skill.seeker.ExplosiveSheepSkill;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.List;

public class SkillManager {

	private final List<ActiveSkill> skills = new ArrayList<>();

	public SkillManager(AnimalHidePlugin plugin) {
		register(new PoopTauntSkill(plugin.getTauntTraceSupport()));
		register(new StinkyTauntSkill(plugin.getTauntTraceSupport()));
		register(new ScreamTauntSkill(plugin.getTauntTraceSupport()));
		register(new PartyTauntSkill(plugin.getTauntTraceSupport()));
		register(new DisguiseInvisibilitySkill());
		register(new DecoyToggleSkill());
		register(new DisguiseWandSkill());
		register(new ExplosiveSheepSkill(plugin));
	}

	public void register(ActiveSkill skill) {
		skills.add(skill);
	}

	public boolean handle(SkillContext context, Entity target) {
		for (ActiveSkill skill : skills) {
			if (skill.matches(context, target)) {
				skill.execute(context, target);
				return true;
			}
		}
		return false;
	}
}
