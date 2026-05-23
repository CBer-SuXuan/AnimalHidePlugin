package me.suxuan.animalhide.skill.hider.old;

import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.Disguise;
import me.suxuan.animalhide.game.ScoringConfig;
import me.suxuan.animalhide.manager.DecoyManager;
import me.suxuan.animalhide.skill.ItemBasedSkill;
import me.suxuan.animalhide.skill.SkillContext;
import me.suxuan.animalhide.skill.hider.HiderSkillSupport;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;

public class SafeTauntSkill extends ItemBasedSkill {

	public SafeTauntSkill() {
		super("safe_taunt", Material.PINK_DYE);
	}

	@Override
	protected boolean additionalMatches(SkillContext context, Entity target) {
		return target == null;
	}

	@Override
	public void execute(SkillContext context, Entity target) {
		if (!HiderSkillSupport.checkPlayableHider(context)) return;
		if (!HiderSkillSupport.ensureNoHidePhase(context, "还没到寻找者出动的时间，现在不能使用嘲讽哦！")) return;
		if (context.player().hasCooldown(getTriggerItem())) return;

		Location tauntLoc = resolveTauntLocation(context);
		ScoringConfig scoring = context.arena().getTemplate().getScoring();
		int scoreReward = scoring.getTauntSafe();

		playAnimalSound(context, tauntLoc);
		tauntLoc.getWorld().spawnParticle(Particle.NOTE, tauntLoc.clone().add(0, 1.5, 0), 3, 0.5, 0.5, 0.5, 1);
		context.player().sendMessage(Component.text("发动了 安全嘲讽！积分 +" + scoreReward, NamedTextColor.GREEN));
		HiderSkillSupport.applySharedTauntCooldownAndReward(context, 5, scoreReward);
	}

	private Location resolveTauntLocation(SkillContext context) {
		DecoyManager decoyManager = context.plugin().getDecoyManager();
		return decoyManager.getAnchorOrPlayerLocation(context.player(), context.arena());
	}

	private void playAnimalSound(SkillContext context, Location at) {
		Disguise disguise = DisguiseAPI.getDisguise(context.player());
		if (disguise == null) return;

		String typeName = disguise.getType().name();
		try {
			Sound sound = Sound.valueOf("ENTITY_" + typeName + "_AMBIENT");
			at.getWorld().playSound(at, sound, 1f, 1f);
		} catch (IllegalArgumentException e) {
			at.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.1f, 2f);
		}
	}
}
