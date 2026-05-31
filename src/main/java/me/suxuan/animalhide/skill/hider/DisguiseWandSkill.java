package me.suxuan.animalhide.skill.hider;

import me.suxuan.animalhide.game.ArenaMode;
import me.suxuan.animalhide.skill.ItemBasedSkill;
import me.suxuan.animalhide.skill.SkillContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;

import java.util.List;

public class DisguiseWandSkill extends ItemBasedSkill {

	public DisguiseWandSkill() {
		super("disguise_wand", Material.BLAZE_ROD);
	}

	@Override
	protected boolean additionalMatches(SkillContext context, Entity target) {
		return target != null;
	}

	@Override
	public void execute(SkillContext context, Entity target) {
		if (!HiderSkillSupport.checkPlayableHider(context)) return;

		Long lockoutTime = context.arena().getDisguiseLockouts().get(context.player().getUniqueId());
		if (lockoutTime != null && System.currentTimeMillis() < lockoutTime) {
			long remainSec = (lockoutTime - System.currentTimeMillis()) / 1000;
			context.player().sendActionBar(Component.text("危险嘲讽副作用！" + remainSec + " 秒内无法变换伪装！", NamedTextColor.RED));
			context.player().playSound(context.player().getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
			return;
		}

		String typeName = target.getType().name();
		String listKey = (context.arena().getArenaMode() == ArenaMode.ANIMAL) ? "allowed-animals" : "allowed-monsters";
		List<String> allowed = context.plugin().getConfigManager().getArenaConfigs().get(context.arena().getTemplate().getConfigKey()).getStringList(listKey);

		if (allowed.contains(typeName)) {
			try {
				context.plugin().getDecoyManager().clear(context.player(), context.arena());
				context.plugin().getDisguiseManager().disguisePlayerAsEntity(context.player(), target);

				Component localizedName = Component.translatable(target.getType().translationKey(), NamedTextColor.YELLOW);
				context.player().sendMessage(Component.text("✔ 已利用魔杖变身为: ", NamedTextColor.GREEN).append(localizedName));
				context.player().playSound(context.player().getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1f);
			} catch (Exception ignored) {
				context.player().sendMessage(Component.text("✘ 变身发生异常。", NamedTextColor.RED));
			}
		} else {
			context.player().sendMessage(Component.text("✘ 这种生物不能用来伪装！", NamedTextColor.RED));
		}
	}
}
