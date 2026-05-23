package me.suxuan.animalhide.listeners;

import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.GameManager;
import me.suxuan.animalhide.game.GameState;
import me.suxuan.animalhide.game.PlayerRole;
import me.suxuan.animalhide.menus.ModeMenu;
import me.suxuan.animalhide.menus.ModeMenuHolder;
import me.suxuan.animalhide.menus.RoleMenu;
import me.suxuan.animalhide.menus.RoleMenuHolder;
import me.suxuan.animalhide.skill.SkillContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class InteractionListener implements Listener {

	private final GameManager gameManager;
	private final AnimalHidePlugin plugin;

	public InteractionListener(GameManager gameManager) {
		this.gameManager = gameManager;
		this.plugin = AnimalHidePlugin.getInstance();
	}

	/**
	 * 防止玩家在游戏中移动物品栏内的 UI 物品，并处理选择类菜单。
	 */
	@EventHandler
	public void onInventoryClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) return;
		Arena arena = gameManager.getArenaByPlayer(player);

		if (arena != null) {
			event.setCancelled(true);
		}

		if (event.getInventory().getHolder() instanceof ModeMenuHolder) {
			event.setCancelled(true);
			player.closeInventory();
			return;
		}

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
	}

	/**
	 * 监听普通右键：大厅 UI、旁观者退出、道具技能分发。
	 */
	@EventHandler
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (event.getHand() != EquipmentSlot.HAND) return;

		Player player = event.getPlayer();
		Arena arena = gameManager.getArenaByPlayer(player);
		if (arena == null) return;

		if (arena.getState() == GameState.WAITING || arena.getState() == GameState.STARTING) {
			handleLobbyInteract(event, player);
			return;
		}
		if (arena.getState() != GameState.PLAYING) return;

		if (arena.getSpectators().contains(player.getUniqueId())) {
			handleSpectatorInteract(event, player);
			return;
		}

		ItemStack item = event.getItem();
		if (item == null) return;

		if (arena.getHiders().contains(player.getUniqueId())) {
			if (player.getInventory().getHeldItemSlot() == 8) {
				event.setCancelled(true);
				return;
			}
			if (item.getType() == Material.BOW && (arena.isHidePhase() || arena.getDecoyAnchors().containsKey(player.getUniqueId()))) {
				event.setCancelled(true);
				player.sendActionBar(Component.text(
						arena.isHidePhase() ? "躲藏阶段无法使用弓箭！" : "定点伪装中无法使用弓箭！",
						NamedTextColor.RED));
				return;
			}
		}

		if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

		SkillContext context = new SkillContext(plugin, player, arena, item, event.getAction());
		if (plugin.getSkillManager().handle(context, null)) {
			event.setCancelled(true);
		}
	}

	/**
	 * 监听实体右键：变身魔杖、对实体释放爆炸羊等也统一走技能分发。
	 */
	@EventHandler
	public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {

		if (event.getHand() != EquipmentSlot.HAND) return;

		Player player = event.getPlayer();
		Arena arena = gameManager.getArenaByPlayer(player);
		if (arena == null || arena.getState() != GameState.PLAYING) return;

		ItemStack item = player.getInventory().getItemInMainHand();
		SkillContext context = new SkillContext(plugin, player, arena, item, Action.RIGHT_CLICK_BLOCK);
		if (plugin.getSkillManager().handle(context, event.getRightClicked())) {
			event.setCancelled(true);
		}
	}

	private void handleLobbyInteract(PlayerInteractEvent event, Player player) {
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
	}

	private void handleSpectatorInteract(PlayerInteractEvent event, Player player) {
		event.setCancelled(true);
		ItemStack item = event.getItem();
		if (item != null && item.getType() == Material.RED_BED) {
			player.performCommand("hide leave");
		}
	}
}
