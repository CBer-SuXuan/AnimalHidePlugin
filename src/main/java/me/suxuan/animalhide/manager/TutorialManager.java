package me.suxuan.animalhide.manager;

import me.suxuan.animalhide.AnimalHidePlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
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
import java.util.Set;

public class TutorialManager {

	private final AnimalHidePlugin plugin;
	private final List<Entity> tutorialEntities = new ArrayList<>();

	public TutorialManager(AnimalHidePlugin plugin) {
		this.plugin = plugin;
		// 插件启动时，从配置文件加载所有已保存的 NPC
		loadTutorialNPCs();
		startAnimationLoop();
	}

	/**
	 * 根据类型生成 NPC 并保存到配置文件
	 */
	public void spawnByType(Location loc, String type) {
		if (type.equalsIgnoreCase("all")) {
			spawnAll(loc);
			return;
		}

		// 1. 先生成实体
		Entity entity = createSpecificNPC(loc, type);
		if (entity != null) {
			tutorialEntities.add(entity);
			// 2. 保存到 config.yml 确保重启不丢失
			saveNPCToConfig(loc, type);
		}
	}

	private void spawnAll(Location center) {
		spawnByType(center.clone().add(-3, 0, 0), "pig");
		spawnByType(center.clone().add(-1, 0, 0), "sheep");
		spawnByType(center.clone().add(1, 0, 0), "cow");
		spawnByType(center.clone().add(3, 0, 0), "chicken");
	}

	/**
	 * 内部方法：根据类型名创建对应的实体
	 */
	private Entity createSpecificNPC(Location loc, String type) {
		switch (type.toLowerCase()) {
			case "pig":
				return createNPC(loc, EntityType.PIG, "§a§l安全嘲讽 §7(粉色染料)");
			case "sheep":
				return createNPC(loc, EntityType.SHEEP, "§e§l发光嘲讽 §7(荧石粉)");
			case "cow":
				return createNPC(loc, EntityType.COW, "§6§l烟花嘲讽 §7(烟花火箭)");
			case "chicken":
				return createNPC(loc, EntityType.CHICKEN, "§c§l危险嘲讽 §7(红石火把)");
			default:
				return null;
		}
	}

	private Entity createNPC(Location loc, EntityType type, String name) {
		LivingEntity entity = (LivingEntity) loc.getWorld().spawnEntity(loc, type);
		entity.setAI(false);
		entity.setInvulnerable(true);
		entity.setSilent(true);
		entity.setCollidable(false);
		// 依然保持 false，由插件逻辑控制生命周期
		entity.setPersistent(false);
		entity.customName(Component.text(name));
		entity.setCustomNameVisible(true);
		return entity;
	}

	/**
	 * 将 NPC 信息存入 config.yml
	 */
	private void saveNPCToConfig(Location loc, String type) {
		ConfigurationSection section = plugin.getConfig().getConfigurationSection("tutorial-npcs");
		if (section == null) section = plugin.getConfig().createSection("tutorial-npcs");

		// 使用时间戳或随机 ID 作为键
		String id = String.valueOf(System.currentTimeMillis()) + (int) (Math.random() * 100);
		ConfigurationSection npcSec = section.createSection(id);
		npcSec.set("type", type);
		npcSec.set("location", loc);

		plugin.saveConfig();
	}

	/**
	 * 启动时加载所有 NPC
	 */
	private void loadTutorialNPCs() {
		ConfigurationSection section = plugin.getConfig().getConfigurationSection("tutorial-npcs");
		if (section == null) return;

		Set<String> keys = section.getKeys(false);
		for (String key : keys) {
			String type = section.getString(key + ".type");
			Location loc = section.getLocation(key + ".location");
			if (type != null && loc != null) {
				Entity entity = createSpecificNPC(loc, type);
				if (entity != null) tutorialEntities.add(entity);
			}
		}
	}

	/**
	 * 清理所有 NPC 并清空配置
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