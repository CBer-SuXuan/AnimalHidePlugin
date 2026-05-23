package me.suxuan.animalhide.skill;

import org.bukkit.entity.Entity;

public interface ActiveSkill {

	String getId();

	boolean matches(SkillContext context, Entity target);

	void execute(SkillContext context, Entity target);
}
