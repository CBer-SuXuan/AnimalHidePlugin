package me.suxuan.animalhide.manager;

import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.MobDisguise;
import me.libraryaddict.disguise.disguisetypes.watchers.CatWatcher;
import me.libraryaddict.disguise.disguisetypes.watchers.LivingWatcher;
import me.libraryaddict.disguise.disguisetypes.watchers.SheepWatcher;
import me.libraryaddict.disguise.disguisetypes.watchers.WolfWatcher;
import me.suxuan.animalhide.AnimalHidePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * 变身管理器
 * 负责调用 LibsDisguises API 实现玩家的变身与复原
 */
public class DisguiseManager {

	private final AnimalHidePlugin plugin;

	public DisguiseManager(AnimalHidePlugin plugin) {
		this.plugin = plugin;
	}

	/**
	 * 移除玩家的变身状态
	 *
	 * @param player 目标玩家
	 */
	public void undisguisePlayer(Player player) {
		if (DisguiseAPI.isDisguised(player)) {
			DisguiseAPI.undisguiseToAll(player);
			player.setWalkSpeed(0.2f);
			player.sendMessage(Component.text("你的伪装已解除！", NamedTextColor.RED));
		}
	}

	/**
	 * 给躲藏者发放表明身份的物品
	 */
	private void giveDisguiseItemUI(Player player, DisguiseType type) {
		Material material = Material.matchMaterial(type.name() + "_SPAWN_EGG");
		if (material == null) {
			material = Material.SPAWNER;
		}

		Component localizedEntityName;
		try {
			EntityType entityType = EntityType.valueOf(type.name());
			localizedEntityName = Component.translatable(entityType.translationKey(), NamedTextColor.GREEN);
		} catch (IllegalArgumentException e) {
			localizedEntityName = Component.text(type.name(), NamedTextColor.GREEN);
		}

		ItemStack uiItem = new ItemStack(material);
		ItemMeta meta = uiItem.getItemMeta();

		// 设置无斜体、带颜色的显示名称
		meta.displayName(Component.text("▶ 你的当前伪装: ", NamedTextColor.GRAY)
				.append(localizedEntityName)
				.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));

		// 设置 Lore 描述
		meta.lore(List.of(
				Component.empty(),
				Component.text("这就是你现在的样子！", NamedTextColor.YELLOW)
						.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
				Component.text("请融入环境，不要被寻找者发现。", NamedTextColor.GRAY)
						.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
		));

		uiItem.setItemMeta(meta);

		player.getInventory().setItem(7, uiItem);
	}

	/**
	 * 动态获取原版生物的真实移速并换算为玩家 WalkSpeed
	 */
	private float getVanillaSpeed(Player player, DisguiseType type) {
		try {
			EntityType entityType = EntityType.valueOf(type.name());

			if (entityType.isAlive() && entityType.getEntityClass() != null) {
				Entity dummy = player.getWorld().createEntity(player.getLocation(), entityType.getEntityClass());

				if (dummy instanceof LivingEntity livingDummy) {
					AttributeInstance speedAttr = livingDummy.getAttribute(Attribute.MOVEMENT_SPEED);
					if (speedAttr != null) {
						double baseSpeed = speedAttr.getBaseValue();
						float scaledSpeed = (float) (baseSpeed * 0.7);
						return Math.clamp(scaledSpeed, 0.12f, 0.35f);
					}
				}
			}
		} catch (IllegalArgumentException ignored) {
		}
		return 0.2f;
	}

	public void disguisePlayerAsEntity(Player player, Entity targetEntity) {
		DisguiseType type = DisguiseType.getType(targetEntity.getType());
		if (!type.isMob()) {
			plugin.getComponentLogger().warn("尝试将玩家变为非生物类型: {}", type.name());
			return;
		}

		MobDisguise disguise = new MobDisguise(type);

		// 1. 基础配置 (与原来一致)
		disguise.setViewSelfDisguise(true);
		disguise.setHideArmorFromSelf(false);
		disguise.setHideHeldItemFromSelf(false);
		disguise.setHearSelfDisguise(false);

		LivingWatcher watcher = disguise.getWatcher();
		watcher.setGlowing(false);
		watcher.setCustomNameVisible(false);
		watcher.setPitchLock(0f);

		// 复制羊的颜色
		switch (targetEntity) {
			case Sheep sheepTarget when watcher instanceof SheepWatcher sheepWatcher ->
					sheepWatcher.setColor(sheepTarget.getColor());


			// 复制狼的变种 / 项圈颜色
			case Wolf wolfTarget when watcher instanceof WolfWatcher wolfWatcher -> {
				try {
					wolfWatcher.setVariant(wolfTarget.getVariant());
					if (wolfTarget.isTamed()) {
						wolfWatcher.setCollarColor(wolfTarget.getCollarColor());
					}
				} catch (Throwable ignored) {
				}
			}

			// 复制猫的品种
			case Cat catTarget when watcher instanceof CatWatcher catWatcher -> {
				try {
					catWatcher.setType(catTarget.getCatType());
				} catch (Throwable ignored) {
				}
			}

			// 复制猪的鞍
			case Pig pigTarget when watcher instanceof me.libraryaddict.disguise.disguisetypes.watchers.PigWatcher pigWatcher ->
					pigWatcher.setSaddled(pigTarget.hasSaddle());
			default -> {
			}
		}

		// 3. 应用变身
		DisguiseAPI.disguiseToAll(player, disguise);
		giveDisguiseItemUI(player, type);

		// 4. 调整移速
		float actualSpeed = getVanillaSpeed(player, type);
		player.setWalkSpeed(actualSpeed);
	}
}