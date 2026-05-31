package me.suxuan.animalhide.skill.hider;

import me.suxuan.animalhide.game.ScoringConfig;
import me.suxuan.animalhide.skill.ItemBasedSkill;
import me.suxuan.animalhide.skill.SkillContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;

public class PoopTauntSkill extends ItemBasedSkill {

	private final TauntTraceSupport tauntTraceSupport;

	public PoopTauntSkill(TauntTraceSupport tauntTraceSupport) {
		super("poop_taunt", Material.COCOA_BEANS);
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
		if (!HiderSkillSupport.ensureTauntUnlocked(context)) return;
		if (context.player().hasCooldown(getTriggerItem())) return;

		Location tauntLoc = tauntTraceSupport.resolveTauntLocation(context);
		ScoringConfig scoring = context.arena().getTemplate().getScoring();
		int scoreReward = scoring.getTauntSafe();
		int cooldownSeconds = HiderSkillSupport.getPoopTauntCooldownSeconds(context.arena().getArenaName());

		tauntTraceSupport.createPoopMarker(context, tauntLoc, context.player().getName() + " 的便便");
		tauntTraceSupport.playAnimalSound(context, tauntLoc, 1f, 1f);
		tauntTraceSupport.spawnPoopParticles(context.player());
		tauntTraceSupport.refreshSeekers(context.arena(), null, true);

		context.arena().incrementSkillUse("poop_taunt");
		context.player().sendMessage(Component.text("发动了 便便嘲讽！留下一坨线索，积分 +" + scoreReward, NamedTextColor.GREEN));
		HiderSkillSupport.applySharedTauntCooldownAndReward(context, cooldownSeconds, scoreReward);
	}
}
