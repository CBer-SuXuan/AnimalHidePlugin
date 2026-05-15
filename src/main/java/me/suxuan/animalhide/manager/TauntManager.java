package me.suxuan.animalhide.manager;

import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.Disguise;
import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.game.Arena;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Random;
import java.util.UUID;

public class TauntManager {

	private final AnimalHidePlugin plugin;
	private final Random random = new Random();

	public TauntManager(AnimalHidePlugin plugin) {
		this.plugin = plugin;
	}

	public void handleTaunt(Player player, Arena arena, Material tauntType) {
		if (player.hasCooldown(tauntType)) return;

		int cooldownSeconds = 0;
		int scoreReward = 0;

		if (tauntType == Material.PINK_DYE) {
			// ==============================
			// 1. 安全嘲讽 (Safe Taunt) - CD 5秒
			// ==============================
			cooldownSeconds = 5;
			scoreReward = 2;
			playAnimalSound(player);
			player.getWorld().spawnParticle(Particle.NOTE, player.getLocation().add(0, 1.5, 0), 3, 0.5, 0.5, 0.5, 1);
			player.sendMessage(Component.text("发动了 安全嘲讽！积分 +2", NamedTextColor.GREEN));

		} else if (tauntType == Material.GLOWSTONE_DUST) {
			// ==============================
			// 2. 冒险嘲讽 (Risky Taunt) - CD 15秒，拉便便
			// ==============================
			cooldownSeconds = 15;
			scoreReward = 5;

			// 播放喧闹的声音 (随机选取)
			Sound[] noisySounds = {Sound.ENTITY_VILLAGER_NO, Sound.BLOCK_ANVIL_LAND, Sound.ENTITY_DONKEY_ANGRY};
			player.getWorld().playSound(player.getLocation(), noisySounds[random.nextInt(noisySounds.length)], 1f, 1f);
			player.getWorld().spawnParticle(Particle.NOTE, player.getLocation().add(0, 1.5, 0), 5, 0.5, 0.5, 0.5, 1);

			// 掉落“便便” (可可豆)
			ItemStack poop = new ItemStack(Material.COCOA_BEANS);
			ItemMeta meta = poop.getItemMeta();
			meta.displayName(Component.text(player.getName() + " 的便便", NamedTextColor.GOLD));
			poop.setItemMeta(meta);
			Item itemEntity = player.getWorld().dropItem(player.getLocation(), poop);
			itemEntity.setCustomName("§6" + player.getName() + " 的便便");
			itemEntity.setCustomNameVisible(true);

			player.sendMessage(Component.text("发动了 冒险嘲讽！积分 +5", NamedTextColor.YELLOW));

		} else if (tauntType == Material.FIREWORK_ROCKET) {
			// ==============================
			// 3. 烟花嘲讽 (Firework Taunt) - CD 15秒，每局限5次
			// ==============================
			int uses = arena.getFireworkUses().getOrDefault(player.getUniqueId(), 0);
			if (uses >= 5) {
				player.sendMessage(Component.text("本局烟花嘲讽次数已用尽！", NamedTextColor.RED));
				player.getInventory().setItem(5, new ItemStack(Material.AIR)); // 没收物品
				return;
			}
			arena.getFireworkUses().put(player.getUniqueId(), uses + 1);

			cooldownSeconds = 15;
			scoreReward = 7;

			Firework fw = (Firework) player.getWorld().spawnEntity(player.getLocation(), EntityType.FIREWORK_ROCKET);
			FireworkMeta fwm = fw.getFireworkMeta();
			fwm.addEffect(FireworkEffect.builder().withColor(Color.RED, Color.YELLOW).with(FireworkEffect.Type.BALL_LARGE).build());
			fwm.setPower(2); // 射得更高
			fw.setFireworkMeta(fwm);

			player.sendMessage(Component.text("发动了 烟花嘲讽！(剩余次数: " + (4 - uses) + ") 积分 +7", NamedTextColor.GOLD));

		} else if (tauntType == Material.REDSTONE_TORCH) {
			// ==============================
			// 4. 危险嘲讽 (Dangerous Taunt) - CD 60秒
			// ==============================
			cooldownSeconds = 60;
			scoreReward = 10;

			// 给躲藏者加速，并禁止变身 10 秒
			player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1, false, false, false));
			arena.getDisguiseLockouts().put(player.getUniqueId(), System.currentTimeMillis() + 10000L); // 锁 10 秒

			// 获取模糊坐标
			Location loc = player.getLocation();
			int approxX = ((int) loc.getX() / 10) * 10;
			int approxZ = ((int) loc.getZ() / 10) * 10;

			String animalName = "未知生物";
			Disguise disguise = DisguiseAPI.getDisguise(player);
			if (disguise != null) animalName = disguise.getType().name();

			// 向所有寻找者发送警告
			Component warnMsg = Component.text("⚠ 发现躲藏者！伪装: ", NamedTextColor.RED)
					.append(Component.text(animalName, NamedTextColor.YELLOW))
					.append(Component.text(" 大致坐标: X:" + approxX + " ~ " + (approxX + 10) + ", Z:" + approxZ + " ~ " + (approxZ + 10), NamedTextColor.GRAY));

			for (UUID seekerId : arena.getSeekers()) {
				Player seeker = org.bukkit.Bukkit.getPlayer(seekerId);
				if (seeker != null) {
					seeker.sendMessage(warnMsg);
					seeker.playSound(seeker.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);
				}
			}

			player.sendMessage(Component.text("发动了 危险嘲讽！你的位置已被通报，且10秒内无法变换伪装！快跑！积分 +15", NamedTextColor.DARK_RED));
		}

		if (cooldownSeconds > 0) {
			int ticks = cooldownSeconds * 20;
			
			player.setCooldown(Material.PINK_DYE, ticks);
			player.setCooldown(Material.GLOWSTONE_DUST, ticks);
			player.setCooldown(Material.FIREWORK_ROCKET, ticks);
			player.setCooldown(Material.REDSTONE_TORCH, ticks);

			arena.addMatchScore(player.getUniqueId(), scoreReward);
			player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
		}
	}

	/**
	 * 根据玩家当前的伪装播放原版动物声音
	 */
	private void playAnimalSound(Player player) {
		Disguise disguise = DisguiseAPI.getDisguise(player);
		if (disguise == null) return;

		String typeName = disguise.getType().name();
		try {
			// 尝试映射如 ENTITY_PIG_AMBIENT
			Sound sound = Sound.valueOf("ENTITY_" + typeName + "_AMBIENT");
			player.getWorld().playSound(player.getLocation(), sound, 1f, 1f);
		} catch (IllegalArgumentException e) {
			// 兜底声音
			player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.1f, 2f);
		}
	}
}