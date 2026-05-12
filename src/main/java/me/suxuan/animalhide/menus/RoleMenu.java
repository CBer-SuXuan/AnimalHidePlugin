package me.suxuan.animalhide.menus;

import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.PlayerRole;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class RoleMenu {

	public static void openMenu(Player player) {
		RoleMenuHolder holder = new RoleMenuHolder();
		Inventory inv = Bukkit.createInventory(holder, 9, Component.text("选择期望身份", NamedTextColor.DARK_GRAY));
		holder.setInventory(inv);

		Arena arena = AnimalHidePlugin.getInstance().getGameManager().getArenaByPlayer(player);
		int seekerWanted = arena.getRolePreferenceCount(PlayerRole.SEEKER);
		int hiderWanted = arena.getRolePreferenceCount(PlayerRole.HIDER);

		inv.setItem(2, createItem(Material.DIAMOND_SWORD,
				Component.text("我想要当: ", NamedTextColor.GRAY).append(Component.text("寻找者", NamedTextColor.RED)),
				List.of(
						Component.text("你将追捕动物。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
						Component.text(""),
						Component.text("已有 " + seekerWanted + " 人选择", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))));

		inv.setItem(4, createItem(Material.OAK_LEAVES,
				Component.text("我想要当: ", NamedTextColor.GRAY).append(Component.text("躲藏者", NamedTextColor.GREEN)),
				List.of(
						Component.text("你将化身为动物，融入环境。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
						Component.text(""),
						Component.text("已有 " + hiderWanted + " 人选择", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))));

		inv.setItem(6, createItem(Material.BARRIER,
				Component.text("无所谓/随机", NamedTextColor.WHITE),
				List.of(Component.text("完全服从系统分配。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))));

		player.openInventory(inv);
	}

	private static ItemStack createItem(Material material, Component name, List<Component> lore) {
		ItemStack item = new ItemStack(material);
		ItemMeta meta = item.getItemMeta();
		meta.displayName(name.decoration(TextDecoration.ITALIC, false));
		meta.lore(lore);
		item.setItemMeta(meta);
		return item;
	}
}