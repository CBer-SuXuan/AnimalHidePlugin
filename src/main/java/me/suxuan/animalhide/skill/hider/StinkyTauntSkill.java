package me.suxuan.animalhide.skill.hider;

import me.suxuan.animalhide.game.ScoringConfig;
import me.suxuan.animalhide.skill.ItemBasedSkill;
import me.suxuan.animalhide.skill.SkillContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;

public class StinkyTauntSkill extends ItemBasedSkill {

	private final TauntTraceSupport tauntTraceSupport;

	public StinkyTauntSkill(TauntTraceSupport tauntTraceSupport) {
		super("stinky_taunt", Material.SLIME_BALL);
		this.tauntTraceSupport = tauntTraceSupport;
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

		Location tauntLoc = tauntTraceSupport.resolveTauntLocation(context);
		ScoringConfig scoring = context.arena().getTemplate().getScoring();
		int scoreReward = scoring.getTauntRisky();
		double healAmount = context.plugin().getConfigManager().getStinkyTauntHealAmount(context.arena().getArenaName());
		int cooldownSeconds = HiderSkillSupport.getStinkyTauntCooldownSeconds(context.arena().getArenaName());

		tauntTraceSupport.createPoopMarker(context, tauntLoc, context.player().getName() + " 的臭便便");
		tauntTraceSupport.playAnimalSound(context, tauntLoc, 1.2f, 0.9f);
		tauntLoc.getWorld().playSound(tauntLoc, Sound.ENTITY_SLIME_SQUISH, 1f, 0.7f);
		tauntTraceSupport.spawnPoopParticles(context.player());
		tauntTraceSupport.spawnStinkCloud(context.player(), 80L);
		tauntTraceSupport.refreshSeekers(context.arena(), null, true);

		context.player().sendMessage(Component.text("发动了 臭气嘲讽！臭味会在原地停留一会儿，积分 +" + scoreReward, NamedTextColor.YELLOW));
		HiderSkillSupport.healPlayerFromTaunt(context, healAmount, "臭气嘲讽", NamedTextColor.YELLOW);
		HiderSkillSupport.applySharedTauntCooldownAndReward(context, cooldownSeconds, scoreReward);
	}
}
