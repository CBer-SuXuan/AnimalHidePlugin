package me.suxuan.animalhide.skill;

import org.bukkit.Material;
import org.bukkit.entity.Entity;

public abstract class ItemBasedSkill implements ActiveSkill {

	private final String id;
	private final Material triggerItem;

	protected ItemBasedSkill(String id, Material triggerItem) {
		this.id = id;
		this.triggerItem = triggerItem;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public boolean matches(SkillContext context, Entity target) {
		return context.item() != null && context.item().getType() == triggerItem && additionalMatches(context, target);
	}

	protected boolean additionalMatches(SkillContext context, Entity target) {
		return true;
	}

	protected Material getTriggerItem() {
		return triggerItem;
	}
}
