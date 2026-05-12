package me.suxuan.animalhide.listeners;

import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.game.*;
import me.suxuan.animalhide.menus.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
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
	public void onInventoryClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) return;
		Arena arena = gameManager.getArenaByPlayer(player);

		if (arena != null && (arena.getState() == GameState.WAITING || arena.getState() == GameState.STARTING)) {
			event.setCancelled(true);
		}

		// 处理选择模式GUI
		if (event.getInventory().getHolder() instanceof ModeMenuHolder) {
			event.setCancelled(true);
			ItemStack clicked = event.getCurrentItem();
			if (clicked == null || arena == null) return;

			ArenaMode votedMode = (clicked.getType() == Material.PIG_SPAWN_EGG) ? ArenaMode.ANIMAL : ArenaMode.MONSTER;

			arena.getModeVotes().put(player.getUniqueId(), votedMode);
			player.sendMessage(Component.text("✔ 投票成功！当前选择: " + votedMode.getDisplayName(), NamedTextColor.GREEN));

			player.closeInventory();
			return;
		}

		// 处理选择期望身份GUI
		if (event.getInventory().getHolder() instanceof RoleMenuHolder) {
			event.setCancelled(true);
			ItemStack clicked = event.getCurrentItem();
			if (clicked == null || arena == null) return;

			PlayerRole preference = null;
			if (clicked.getType() == Material.DIAMOND_SWORD) preference = PlayerRole.SEEKER;
			else if (clicked.getType() == Material.OAK_LEAVES) preference = PlayerRole.HIDER;

			if (preference != null) {
				arena.getRolePreferences().put(player.getUniqueId(), preference);
				player.sendMessage(Component.text("✔ 已记录你的意向身份: ", NamedTextColor.GREEN).append(Component.text(preference.getDisplayName(), NamedTextColor.YELLOW)));
			} else {
				arena.getRolePreferences().remove(player.getUniqueId());
				player.sendMessage(Component.text("✔ 已重置为随机分配。", NamedTextColor.GREEN));
			}
			player.closeInventory();
		}

		// 处理选择伪装生物GUI
		if (arena != null && arena.getState() == GameState.PLAYING) {
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
	}

	/**
	 * 监听玩家右键
	 */
	@EventHandler
	public void onPlayerInteract(PlayerInteractEvent event) {
		Player player = event.getPlayer();
		Arena arena = gameManager.getArenaByPlayer(player);

		if (arena != null) {
			// 处理大厅等待阶段的物品右键
			if (arena.getState() == GameState.WAITING || arena.getState() == GameState.STARTING) {
				ItemStack item = event.getItem();
				if (item == null) return;

				event.setCancelled(true);

				if (item.getType() == Material.RED_BED) {
					player.performCommand("hide leave");
				} else if (item.getType() == Material.RECOVERY_COMPASS) {
					ModeMenu.openMenu(player);
				} else if (item.getType() == Material.DIAMOND_HELMET) {
					RoleMenu.openMenu(player);
				}
				return;
			}
			if (arena.getState() != GameState.PLAYING) return;
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
