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
import org.bukkit.entity.Player;

import java.util.UUID;

public class PartyTauntSkill extends ItemBasedSkill {

	private final TauntTraceSupport tauntTraceSupport;

	public PartyTauntSkill(TauntTraceSupport tauntTraceSupport) {
		super("party_taunt", Material.REDSTONE_TORCH);
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
		int scoreReward = scoring.getTauntDangerous();

		tauntTraceSupport.createPoopMarker(context, tauntLoc, context.player().getName() + " 的派对现场");
		tauntTraceSupport.playAnimalSound(context, tauntLoc, 2.2f, 1.1f);
		tauntLoc.getWorld().playSound(tauntLoc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.2f, 1.1f);
		tauntLoc.getWorld().playSound(tauntLoc, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.1f, 1.8f);
		tauntTraceSupport.spawnPartyBurst(context.player(), 120L);
		tauntTraceSupport.spawnBeaconColumn(context.player(), 120L, Particle.TOTEM_OF_UNDYING, 4, 0.12);
		tauntTraceSupport.refreshSeekers(context.arena(), tauntLoc, true);

		for (UUID seekerId : context.arena().getSeekers()) {
			Player seeker = org.bukkit.Bukkit.getPlayer(seekerId);
			if (seeker == null) continue;
			seeker.sendMessage(Component.text("全场派对开始了！顺着指南针和特效去抓它！", NamedTextColor.RED));
			seeker.playSound(seeker.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.8f);
		}

		context.player().sendMessage(Component.text("发动了 派对嘲讽！全场都会注意到这场表演，积分 +" + scoreReward, NamedTextColor.DARK_RED));
		HiderSkillSupport.applySharedTauntCooldownAndReward(context, 45, scoreReward);
	}
}
