package me.suxuan.animalhide.skill.hider;

import me.suxuan.animalhide.game.ScoringConfig;
import me.suxuan.animalhide.skill.ItemBasedSkill;
import me.suxuan.animalhide.skill.SkillContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;

public class ScreamTauntSkill extends ItemBasedSkill {

	private final TauntTraceSupport tauntTraceSupport;

	public ScreamTauntSkill(TauntTraceSupport tauntTraceSupport) {
		super("scream_taunt", Material.FIREWORK_ROCKET);
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
		int scoreReward = scoring.getTauntFirework();

		tauntTraceSupport.createPoopMarker(context, tauntLoc, context.player().getName() + " 的尖叫现场");
		tauntTraceSupport.playAnimalSound(context, tauntLoc, 2f, 1.2f);
		tauntLoc.getWorld().playSound(tauntLoc, Sound.ENTITY_GOAT_SCREAMING_PREPARE_RAM, 1.2f, 1.4f);
		tauntTraceSupport.spawnBeaconColumn(context.player(), 70L, Particle.END_ROD, 3, 0.08);
		tauntTraceSupport.spawnBeaconColumn(context.player(), 70L, Particle.CLOUD, 2, 0.12);
		tauntTraceSupport.refreshSeekers(context.arena(), null, true);
		tauntTraceSupport.pulseSeekerAudio(context.arena(), tauntLoc, Sound.BLOCK_BELL_RESONATE, 0.8f, 1.6f);

		context.player().sendMessage(Component.text("发动了 尖叫嘲讽！远处也能看到你的动静，积分 +" + scoreReward, NamedTextColor.GOLD));
		HiderSkillSupport.applySharedTauntCooldownAndReward(context, 20, scoreReward);
	}
}
