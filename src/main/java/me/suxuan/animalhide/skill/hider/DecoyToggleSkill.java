package me.suxuan.animalhide.skill.hider;

import me.suxuan.animalhide.skill.ItemBasedSkill;
import me.suxuan.animalhide.skill.SkillContext;
import org.bukkit.Material;
import org.bukkit.entity.Entity;

public class DecoyToggleSkill extends ItemBasedSkill {

	public DecoyToggleSkill() {
		super("decoy_toggle", Material.LEAD);
	}

	@Override
	protected boolean additionalMatches(SkillContext context, Entity target) {
		return target == null;
	}

	@Override
	public void execute(SkillContext context, Entity target) {
		if (!HiderSkillSupport.checkPlayableHider(context)) return;
		context.plugin().getDecoyManager().toggle(context.player(), context.arena());
	}
}
