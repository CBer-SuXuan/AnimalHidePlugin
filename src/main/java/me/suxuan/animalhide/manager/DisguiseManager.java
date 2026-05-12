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
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
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
	 * 将玩家变身为指定的动物
	 *
	 * @param player 目标玩家
	 * @param type   变身的动物类型 (例如 DisguiseType.PIG)
	 */
	public void disguisePlayer(Player player, DisguiseType type) {
		if (!type.isMob()) {
			plugin.getComponentLogger().warn("尝试将玩家变为非生物类型: {}", type.name());
			return;
		}

		MobDisguise disguise = new MobDisguise(type);

		// 配置变身属性
		disguise.setViewSelfDisguise(true);  // 设置玩家自己能看到自己的变身形态
		disguise.setHideArmorFromSelf(false);
		disguise.setHideHeldItemFromSelf(false);
		disguise.setHearSelfDisguise(false);  // 静音

		// 配置观察者属性
		LivingWatcher watcher = disguise.getWatcher();
		watcher.setGlowing(false);
		watcher.setCustomNameVisible(false);
		watcher.setPitchLock(0f);

		if (watcher instanceof SheepWatcher sheepWatcher) {
			sheepWatcher.setColor(DyeColor.WHITE);
		} else if (watcher instanceof WolfWatcher wolfWatcher) {
			try {
				wolfWatcher.setVariant(Registry.WOLF_VARIANT.get(NamespacedKey.minecraft("pale")));
			} catch (Throwable ignored) {
			}
		} else if (watcher instanceof CatWatcher catWatcher) {
			try {
				catWatcher.setType(Registry.CAT_VARIANT.get(NamespacedKey.minecraft("tabby")));
			} catch (Throwable ignored) {
			}
		}

		// 应用变身
		DisguiseAPI.disguiseToAll(player, disguise);

		giveDisguiseItemUI(player, type);

		player.setCollidable(false);

		float actualSpeed = getVanillaSpeed(player, type);
		player.setWalkSpeed(actualSpeed);
	}

	/**
	 * 移除玩家的变身状态
	 *
	 * @param player 目标玩家
	 */
	public void undisguisePlayer(Player player) {
		if (DisguiseAPI.isDisguised(player)) {
			DisguiseAPI.undisguiseToAll(player);
			player.setCollidable(true);
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
}