package me.suxuan.animalhide.skill.hider.old;

import me.suxuan.animalhide.game.ScoringConfig;
import me.suxuan.animalhide.manager.DecoyManager;
import me.suxuan.animalhide.skill.ItemBasedSkill;
import me.suxuan.animalhide.skill.SkillContext;
import me.suxuan.animalhide.skill.hider.HiderSkillSupport;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;

public class FireworkTauntSkill extends ItemBasedSkill {

	public FireworkTauntSkill() {
		super("firework_taunt", Material.FIREWORK_ROCKET);
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

		int uses = context.arena().getFireworkUses().getOrDefault(context.player().getUniqueId(), 0);
		if (uses >= 5) {
			context.player().sendMessage(Component.text("本局烟花嘲讽次数已用尽！", NamedTextColor.RED));
			context.player().getInventory().setItem(5, new ItemStack(Material.AIR));
			return;
		}
		context.arena().getFireworkUses().put(context.player().getUniqueId(), uses + 1);

		int remaining = 5 - (uses + 1);
		if (remaining > 0) {
			ItemStack fwItem = context.player().getInventory().getItem(5);
			if (fwItem != null && fwItem.getType() == Material.FIREWORK_ROCKET) {
				fwItem.setAmount(remaining);
			}
		} else {
			context.player().getInventory().setItem(5, new ItemStack(Material.AIR));
		}

		Location tauntLoc = resolveTauntLocation(context);
		ScoringConfig scoring = context.arena().getTemplate().getScoring();
		int scoreReward = scoring.getTauntFirework();

		Firework fw = (Firework) tauntLoc.getWorld().spawnEntity(tauntLoc, EntityType.FIREWORK_ROCKET);
		FireworkMeta fwm = fw.getFireworkMeta();
		fwm.addEffect(FireworkEffect.builder().withColor(Color.RED, Color.YELLOW).with(FireworkEffect.Type.BALL_LARGE).build());
		fwm.setPower(2);
		fw.setFireworkMeta(fwm);

		context.player().sendMessage(Component.text("发动了 烟花嘲讽！(剩余次数: " + remaining + ") 积分 +" + scoreReward, NamedTextColor.GOLD));
		HiderSkillSupport.applySharedTauntCooldownAndReward(context, 15, scoreReward);
	}

	private Location resolveTauntLocation(SkillContext context) {
		DecoyManager decoyManager = context.plugin().getDecoyManager();
		return decoyManager.getAnchorOrPlayerLocation(context.player(), context.arena());
	}
}
