package me.suxuan.animalhide.manager;

import me.suxuan.animalhide.AnimalHidePlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public class TutorialManager {

	private final AnimalHidePlugin plugin;
	private final List<Entity> tutorialEntities = new ArrayList<>();

	public TutorialManager(AnimalHidePlugin plugin) {
		this.plugin = plugin;
		startAnimationLoop();   // 开启粒子循环
	}

	/**
	 * 根据类型在指定位置生成单个 NPC
	 *
	 * @param loc  位置
	 * @param type "pig", "sheep", "cow", "chicken", 或 "all"
	 */
	public void spawnByType(Location loc, String type) {
		if (type.equalsIgnoreCase("all")) {
			spawnAll(loc);
			return;
		}

		switch (type.toLowerCase()) {
			case "pig":
				tutorialEntities.add(createNPC(loc, EntityType.PIG, "§a§l安全嘲讽 §7(粉色染料)"));
				break;
			case "sheep":
				tutorialEntities.add(createNPC(loc, EntityType.SHEEP, "§e§l发光嘲讽 §7(荧石粉)"));
				break;
			case "cow":
				tutorialEntities.add(createNPC(loc, EntityType.COW, "§6§l烟花嘲讽 §7(烟花火箭)"));
				break;
			case "chicken":
				tutorialEntities.add(createNPC(loc, EntityType.CHICKEN, "§c§l危险嘲讽 §7(红石火把)"));
				break;
			default:
				// 如果输入的类型不对，不做任何事
				break;
		}
	}

	private void spawnAll(Location center) {
		spawnByType(center.clone().add(-3, 0, 0), "pig");
		spawnByType(center.clone().add(-1, 0, 0), "sheep");
		spawnByType(center.clone().add(1, 0, 0), "cow");
		spawnByType(center.clone().add(3, 0, 0), "chicken");
	}

	/**
	 * 创建“雕像”属性的实体
	 */
	private Entity createNPC(Location loc, EntityType type, String name) {
		LivingEntity entity = (LivingEntity) loc.getWorld().spawnEntity(loc, type);
		entity.setAI(false); // 取消AI，呆在原地
		entity.setInvulnerable(true); // 无敌
		entity.setSilent(true); // 禁音
		entity.setCollidable(false); // 取消碰撞箱推挤
		entity.setPersistent(false); // 不保存到世界文件，防止重启无限复制
		entity.customName(Component.text(name));
		entity.setCustomNameVisible(true);
		return entity;
	}

	/**
	 * 清理所有教程 NPC
	 */
	public void clearTutorialNPCs() {
		for (Entity entity : tutorialEntities) {
			if (entity != null && entity.isValid()) {
				entity.remove();
			}
		}
		tutorialEntities.clear();
	}

	/**
	 * 核心动画循环：每 3 秒触发一次技能效果演示
	 */
	private void startAnimationLoop() {
		new BukkitRunnable() {
			@Override
			public void run() {
				for (Entity entity : tutorialEntities) {
					if (entity == null || !entity.isValid()) continue;

					Location loc = entity.getLocation();

					// 根据不同的动物展示不同的技能特效
					switch (entity.getType()) {
						case PIG: // 展示安全嘲讽 (爱心)
							loc.getWorld().spawnParticle(Particle.HEART, loc.add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0);
							break;
						case SHEEP: // 展示发光嘲讽
							((LivingEntity) entity).addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, false, false, false));
							break;
						case COW: // 展示烟花嘲讽 (升空的火星特效)
							Firework fw = (Firework) entity.getWorld().spawnEntity(entity.getLocation(), EntityType.FIREWORK_ROCKET);
							FireworkMeta fwm = fw.getFireworkMeta();
							fwm.addEffect(FireworkEffect.builder().withColor(Color.RED).with(FireworkEffect.Type.BALL_LARGE).build());
							fwm.setPower(1);
							fw.setFireworkMeta(fwm);
							fw.setSilent(true);
							break;
						case CHICKEN: // 展示危险嘲讽 (减速浓烟)
							((LivingEntity) entity).addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, false, false, false));
							loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc.add(0, 1.5, 0), 200, 0, 2, 0, 0.05);
							break;
						default:
							break;
					}
				}
			}
		}.runTaskTimer(plugin, 0L, 60L);
	}
}