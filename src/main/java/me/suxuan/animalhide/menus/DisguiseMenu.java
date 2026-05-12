package me.suxuan.animalhide.menus;

import me.suxuan.animalhide.config.ConfigManager;
import me.suxuan.animalhide.game.Arena;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * 伪装选择菜单构建器
 */
public class DisguiseMenu {

	/**
	 * 为玩家打开伪装选择菜单
	 */
	public static void openMenu(Player player, Arena arena, ConfigManager configManager) {
		// 获取该地图允许的动物列表
		List<String> allowedAnimals = configManager.getArenaConfigs()
				.get(arena.getArenaName())
				.getStringList("allowed-animals");

		// 根据动物数量动态计算菜单行数 (每行9格)
		int size = Math.max(9, ((allowedAnimals.size() - 1) / 9 + 1) * 9);

		// 创建自定义 Holder 和箱子界面
		DisguiseMenuHolder holder = new DisguiseMenuHolder();
		Inventory inv = Bukkit.createInventory(holder, size, Component.text("▶ 选择你的伪装形态", NamedTextColor.DARK_GRAY));
		holder.setInventory(inv);

		// 遍历列表并填充物品
		for (String animalStr : allowedAnimals) {
			Material eggMat = Material.matchMaterial(animalStr.toUpperCase() + "_SPAWN_EGG");
			if (eggMat == null) eggMat = Material.SPAWNER;

			ItemStack item = new ItemStack(eggMat);
			ItemMeta meta = item.getItemMeta();

			Component localizedName;
			try {
				EntityType entityType = EntityType.valueOf(animalStr.toUpperCase());
				localizedName = Component.translatable(entityType.translationKey(), NamedTextColor.GREEN);
			} catch (IllegalArgumentException e) {
				localizedName = Component.text(animalStr, NamedTextColor.GREEN);
			}

			// 设置菜单内图标的展示信息
			meta.displayName(Component.text("✦ 伪装成: ", NamedTextColor.YELLOW)
					.append(localizedName)
					.decoration(TextDecoration.ITALIC, false));
			meta.lore(List.of(
					Component.empty(),
					Component.text("点击即可切换你的伪装形态！", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
			));

			item.setItemMeta(meta);
			inv.addItem(item);
		}

		// 为玩家打开菜单
		player.openInventory(inv);
	}
}