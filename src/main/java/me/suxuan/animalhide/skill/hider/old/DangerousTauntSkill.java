package me.suxuan.animalhide.skill.hider.old;

import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.Disguise;
import me.suxuan.animalhide.game.ScoringConfig;
import me.suxuan.animalhide.manager.DecoyManager;
import me.suxuan.animalhide.skill.ItemBasedSkill;
import me.suxuan.animalhide.skill.SkillContext;
import me.suxuan.animalhide.skill.hider.HiderSkillSupport;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

public class DangerousTauntSkill extends ItemBasedSkill {

	public DangerousTauntSkill() {
		super("dangerous_taunt", Material.REDSTONE_TORCH);
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

		DecoyManager decoyManager = context.plugin().getDecoyManager();
		Location tauntLoc = decoyManager.getAnchorOrPlayerLocation(context.player(), context.arena());
		ScoringConfig scoring = context.arena().getTemplate().getScoring();
		int scoreReward = scoring.getTauntDangerous();

		if (decoyManager.isAnchored(context.arena(), context.player().getUniqueId())) {
			decoyManager.deactivate(context.player(), context.arena());
		}

		context.player().addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1, false, false, false));
		context.arena().getDisguiseLockouts().put(context.player().getUniqueId(), System.currentTimeMillis() + 10000L);

		int approxX = ((int) tauntLoc.getX() / 10) * 10;
		int approxZ = ((int) tauntLoc.getZ() / 10) * 10;

		String animalName = "未知生物";
		Disguise disguise = DisguiseAPI.getDisguise(context.player());
		if (disguise != null) animalName = disguise.getType().name();

		Component warnMsg = Component.text("⚠ 发现躲藏者！伪装: ", NamedTextColor.RED)
				.append(Component.text(animalName, NamedTextColor.YELLOW))
				.append(Component.text(" 大致坐标: X:" + approxX + " ~ " + (approxX + 10) + ", Z:" + approxZ + " ~ " + (approxZ + 10), NamedTextColor.GRAY));

		for (UUID seekerId : context.arena().getSeekers()) {
			Player seeker = Bukkit.getPlayer(seekerId);
			if (seeker != null) {
				seeker.sendMessage(warnMsg);
				seeker.playSound(seeker.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);
			}
		}

		context.player().sendMessage(Component.text("发动了 危险嘲讽！你的位置已被通报，且10秒内无法变换伪装！快跑！积分 +" + scoreReward, NamedTextColor.DARK_RED));
		HiderSkillSupport.applySharedTauntCooldownAndReward(context, 60, scoreReward);
	}
}
