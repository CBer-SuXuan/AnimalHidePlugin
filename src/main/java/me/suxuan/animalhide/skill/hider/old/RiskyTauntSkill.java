package me.suxuan.animalhide.skill.hider.old;

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
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Random;

public class RiskyTauntSkill extends ItemBasedSkill {

	private final Random random = new Random();

	public RiskyTauntSkill() {
		super("risky_taunt", Material.GLOWSTONE_DUST);
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
		int scoreReward = scoring.getTauntRisky();

		Sound[] noisySounds = {Sound.ENTITY_VILLAGER_NO, Sound.BLOCK_ANVIL_LAND, Sound.ENTITY_DONKEY_ANGRY};
		tauntLoc.getWorld().playSound(tauntLoc, noisySounds[random.nextInt(noisySounds.length)], 1f, 1f);
		tauntLoc.getWorld().spawnParticle(Particle.NOTE, tauntLoc.clone().add(0, 1.5, 0), 5, 0.5, 0.5, 0.5, 1);

		ItemStack poop = new ItemStack(Material.COCOA_BEANS);
		ItemMeta meta = poop.getItemMeta();
		meta.displayName(Component.text(context.player().getName() + " 的便便", NamedTextColor.GOLD));
		poop.setItemMeta(meta);
		Item itemEntity = tauntLoc.getWorld().dropItem(tauntLoc, poop);
		itemEntity.setCustomName("§6" + context.player().getName() + " 的便便");
		itemEntity.setCustomNameVisible(true);

		context.player().sendMessage(Component.text("发动了 冒险嘲讽！积分 +" + scoreReward, NamedTextColor.YELLOW));
		HiderSkillSupport.applySharedTauntCooldownAndReward(context, 15, scoreReward);
	}

	private Location resolveTauntLocation(SkillContext context) {
		DecoyManager decoyManager = context.plugin().getDecoyManager();
		return decoyManager.getAnchorOrPlayerLocation(context.player(), context.arena());
	}
}
