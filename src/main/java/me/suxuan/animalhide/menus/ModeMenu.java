package me.suxuan.animalhide.menus;

import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.ArenaMode;
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

public class ModeMenu {

	public static void openMenu(Player player) {
		ModeMenuHolder holder = new ModeMenuHolder();
		Inventory inv = Bukkit.createInventory(holder, 9, Component.text("选择游戏模式", NamedTextColor.DARK_GRAY));
		holder.setInventory(inv);

		Arena arena = AnimalHidePlugin.getInstance().getGameManager().getArenaByPlayer(player);
		int animalVotes = arena.getModeVoteCount(ArenaMode.ANIMAL);
		int monsterVotes = arena.getModeVoteCount(ArenaMode.MONSTER);
		inv.setItem(3, createModeItem(Material.PIG_SPAWN_EGG,
				Component.text("生物模式", NamedTextColor.GREEN),
				List.of(
						Component.text("经典的躲猫猫体验", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
						Component.text("伪装成温顺的动物。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
						Component.text(""),
						Component.text("当前票数: ", NamedTextColor.WHITE).append(Component.text(animalVotes, NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false)
				)));

		inv.setItem(5, createModeItem(Material.ZOMBIE_HEAD,
				Component.text("怪物模式", NamedTextColor.RED),
				List.of(
						Component.text("更具挑战性的模式", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
						Component.text("伪装成具有攻击性的怪物。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
						Component.text(""),
						Component.text("当前票数: ", NamedTextColor.WHITE).append(Component.text(monsterVotes, NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false)
				)));

		player.openInventory(inv);
	}

	private static ItemStack createModeItem(Material material, Component name, List<Component> lore) {
		ItemStack item = new ItemStack(material);
		ItemMeta meta = item.getItemMeta();
		meta.displayName(name.decoration(TextDecoration.ITALIC, false));
		meta.lore(lore);
		item.setItemMeta(meta);
		return item;
	}
}