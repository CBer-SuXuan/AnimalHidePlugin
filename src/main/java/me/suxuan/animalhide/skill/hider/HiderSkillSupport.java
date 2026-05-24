package me.suxuan.animalhide.skill.hider;

import me.suxuan.animalhide.game.GameState;
import me.suxuan.animalhide.skill.SkillContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;

public final class HiderSkillSupport {

	public static final int SAFE_TAUNT_COOLDOWN_SECONDS = 5;
	public static final int STINKY_TAUNT_COOLDOWN_SECONDS = 15;
	public static final int SCREAM_TAUNT_COOLDOWN_SECONDS = 30;
	public static final int PARTY_TAUNT_COOLDOWN_SECONDS = 50;

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
		context.player().setCooldown(Material.COCOA_BEANS, ticks);
		context.player().setCooldown(Material.SLIME_BALL, ticks);
		context.player().setCooldown(Material.GOAT_HORN, ticks);
		context.player().setCooldown(Material.FIREWORK_STAR, ticks);
		context.arena().addMatchScore(context.player().getUniqueId(), scoreReward);
		context.player().playSound(context.player().getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
	}

	public static void healPlayerFromTaunt(SkillContext context, double healAmount, String tauntName, NamedTextColor color) {
		if (healAmount <= 0.0) {
			return;
		}
		AttributeInstance maxHealthAttribute = context.player().getAttribute(Attribute.MAX_HEALTH);
		double maxHealth = maxHealthAttribute != null ? maxHealthAttribute.getValue() : 20.0;
		double currentHealth = context.player().getHealth();
		double newHealth = Math.min(maxHealth, currentHealth + healAmount);
		if (newHealth <= currentHealth) {
			return;
		}
		context.player().setHealth(newHealth);
		context.player().sendActionBar(Component.text(tauntName + " 回复了 " + formatHealAmount(healAmount) + " 点生命", color));
	}

	public static int getPoopTauntCooldownSeconds(String arenaName) {
		return me.suxuan.animalhide.AnimalHidePlugin.getInstance() != null
				? me.suxuan.animalhide.AnimalHidePlugin.getInstance().getConfigManager().getPoopTauntCooldownSeconds(arenaName)
				: SAFE_TAUNT_COOLDOWN_SECONDS;
	}

	public static int getStinkyTauntCooldownSeconds(String arenaName) {
		return me.suxuan.animalhide.AnimalHidePlugin.getInstance() != null
				? me.suxuan.animalhide.AnimalHidePlugin.getInstance().getConfigManager().getStinkyTauntCooldownSeconds(arenaName)
				: STINKY_TAUNT_COOLDOWN_SECONDS;
	}

	public static int getScreamTauntCooldownSeconds(String arenaName) {
		return me.suxuan.animalhide.AnimalHidePlugin.getInstance() != null
				? me.suxuan.animalhide.AnimalHidePlugin.getInstance().getConfigManager().getScreamTauntCooldownSeconds(arenaName)
				: SCREAM_TAUNT_COOLDOWN_SECONDS;
	}

	public static int getPartyTauntCooldownSeconds(String arenaName) {
		return me.suxuan.animalhide.AnimalHidePlugin.getInstance() != null
				? me.suxuan.animalhide.AnimalHidePlugin.getInstance().getConfigManager().getPartyTauntCooldownSeconds(arenaName)
				: PARTY_TAUNT_COOLDOWN_SECONDS;
	}

	public static void broadcastTaunt(SkillContext context, String tauntName, NamedTextColor color) {
		Component levelTag = getTauntLevelTag(color);
		context.arena().broadcast(
				Component.text("【全场通报】", NamedTextColor.GOLD, TextDecoration.BOLD)
						.append(Component.text(" ", NamedTextColor.WHITE))
						.append(levelTag)
						.append(Component.text(" ", NamedTextColor.WHITE))
						.append(Component.text(context.player().getName(), NamedTextColor.AQUA))
						.append(Component.text(" 发动了 ", NamedTextColor.GRAY))
						.append(Component.text(tauntName, color, TextDecoration.BOLD))
		);
	}

	private static Component getTauntLevelTag(NamedTextColor color) {
		if (color == NamedTextColor.GREEN) {
			return Component.text("[低风险]", NamedTextColor.GREEN, TextDecoration.BOLD);
		}
		if (color == NamedTextColor.YELLOW) {
			return Component.text("[中风险]", NamedTextColor.YELLOW, TextDecoration.BOLD);
		}
		if (color == NamedTextColor.GOLD) {
			return Component.text("[高风险]", NamedTextColor.GOLD, TextDecoration.BOLD);
		}
		if (color == NamedTextColor.DARK_RED) {
			return Component.text("[极高风险]", NamedTextColor.DARK_RED, TextDecoration.BOLD);
		}
		return Component.text("[风险未知]", NamedTextColor.GRAY, TextDecoration.BOLD);
	}

	private static String formatHealAmount(double healAmount) {
		if (Math.abs(healAmount - Math.rint(healAmount)) < 1.0E-9) {
			return String.valueOf((int) Math.rint(healAmount));
		}
		return String.format(java.util.Locale.ROOT, "%.1f", healAmount);
	}
}
