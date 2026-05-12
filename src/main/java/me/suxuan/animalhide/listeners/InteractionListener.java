package me.suxuan.animalhide.listeners;

import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.GameManager;
import me.suxuan.animalhide.game.GameState;
import me.suxuan.animalhide.menus.DisguiseMenu;
import me.suxuan.animalhide.menus.DisguiseMenuHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class InteractionListener implements Listener {

	private final GameManager gameManager;

	public InteractionListener(GameManager gameManager) {
		this.gameManager = gameManager;
	}
	
	/**
	 * 防止玩家在游戏中移动物品栏内的 UI 物品
	 */
	@EventHandler
	public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) return;
		Arena arena = gameManager.getArenaByPlayer(player);

		if (arena != null && arena.getState() == GameState.PLAYING) {
			event.setCancelled(true);
		}

		// 处理自定义 GUI 菜单点击
		if (event.getInventory().getHolder() instanceof DisguiseMenuHolder) {
			event.setCancelled(true);

			ItemStack clickedItem = event.getCurrentItem();
			if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

			// 根据点击物品的材质反推 DisguiseType
			// (例如 PIG_SPAWN_EGG -> 截取 PIG)
			String materialName = clickedItem.getType().name();
			if (materialName.endsWith("_SPAWN_EGG")) {
				String animalName = materialName.replace("_SPAWN_EGG", "");
				try {
					DisguiseType newType = DisguiseType.valueOf(animalName);
					AnimalHidePlugin.getInstance().getDisguiseManager().disguisePlayer(player, newType);
					player.closeInventory();

					player.sendMessage(Component.text("✔ 伪装切换成功！", NamedTextColor.GREEN));
				} catch (IllegalArgumentException e) {
					player.sendMessage(Component.text("✘ 无法切换到该伪装。", NamedTextColor.RED));
				}
			}
		}
	}

	/**
	 * 监听玩家右键使用“伪装选择器”
	 */
	@EventHandler
	public void onPlayerInteract(PlayerInteractEvent event) {
		Player player = event.getPlayer();
		Arena arena = gameManager.getArenaByPlayer(player);

		if (arena != null && arena.getState() == GameState.PLAYING) {
			if (arena.getHiders().contains(player.getUniqueId())) {

				if (player.getInventory().getHeldItemSlot() == 8) {
					event.setCancelled(true);
					return;
				}

				if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
					ItemStack item = event.getItem();
					if (item != null && item.getType() == Material.NETHER_STAR) {
						event.setCancelled(true);
						DisguiseMenu.openMenu(player, arena, AnimalHidePlugin.getInstance().getConfigManager());
					}
				}
			}
		}
	}

}
