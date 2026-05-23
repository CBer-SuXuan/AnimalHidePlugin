package me.suxuan.animalhide.skill.hider;

import me.suxuan.animalhide.game.GameState;
import me.suxuan.animalhide.skill.SkillContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;

public final class HiderSkillSupport {

	private HiderSkillSupport() {
	}

	public static boolean checkPlayableHider(SkillContext context) {
		return context.arena() != null
				&& context.arena().getState() == GameState.PLAYING
				&& context.arena().getHiders().contains(context.player().getUniqueId());
	}

	public static boolean ensureNoHidePhase(SkillContext context, String message) {
		if (!context.arena().isHidePhase()) return true;
		context.player().sendActionBar(Component.text(message, NamedTextColor.RED));
		return false;
	}

	public static void applySharedTauntCooldownAndReward(SkillContext context, int cooldownSeconds, int scoreReward) {
		int ticks = cooldownSeconds * 20;
		context.player().setCooldown(Material.PINK_DYE, ticks);
		context.player().setCooldown(Material.GLOWSTONE_DUST, ticks);
		context.player().setCooldown(Material.FIREWORK_ROCKET, ticks);
		context.player().setCooldown(Material.REDSTONE_TORCH, ticks);
		context.arena().addMatchScore(context.player().getUniqueId(), scoreReward);
		context.player().playSound(context.player().getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
	}
}
