package me.suxuan.animalhide.skill.hider;

import me.suxuan.animalhide.skill.ItemBasedSkill;
import me.suxuan.animalhide.skill.SkillContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

public class DisguiseInvisibilitySkill extends ItemBasedSkill {

	private static final int DURATION_TICKS = 100;

	public DisguiseInvisibilitySkill() {
		super("disguise_invisibility", Material.AMETHYST_SHARD);
	}

	@Override
	protected boolean additionalMatches(SkillContext context, Entity target) {
		return target == null;
	}

	@Override
	public void execute(SkillContext context, Entity target) {
		if (!HiderSkillSupport.checkPlayableHider(context)) return;
		if (!HiderSkillSupport.ensureNoHidePhase(context, "还没到寻找者出动的时间，现在不能使用隐身哦！")) return;
		if (!context.plugin().getConfigManager().isHiderDisguiseInvisibilityEnabled()) {
			context.player().sendMessage(Component.text("该技能当前已被服务器关闭。", NamedTextColor.RED));
			context.player().playSound(context.player().getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
			return;
		}

		boolean success = context.plugin().getDisguiseManager().makeDisguiseInvisibleOnce(context.player(), DURATION_TICKS);
		if (!success) {
			context.player().playSound(context.player().getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
			return;
		}

		ItemStack hand = context.player().getInventory().getItemInMainHand();
		if (hand.getAmount() <= 1) {
			context.player().getInventory().setItem(context.player().getInventory().getHeldItemSlot(), null);
		} else {
			hand.setAmount(hand.getAmount() - 1);
		}

		context.player().sendMessage(Component.text("发动了 一次性伪装隐身！你的伪装将在 5 秒内暂时消失。", NamedTextColor.LIGHT_PURPLE));
		context.player().playSound(context.player().getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1f, 1.2f);
	}
}
