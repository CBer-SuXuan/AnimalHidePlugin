package me.suxuan.animalhide.listeners;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * 全局防护：禁止玩家在任何世界中通过右键打开门、活板门、栅栏门、按钮、拉杆等可交互方块。
 * 使用 setUseInteractedBlock(DENY) 仅否决方块交互，不影响手上物品的正常使用（嘲讽道具、菜单物品、食物、盾牌等）。
 * 创造模式不受限，便于管理员搭建。
 */
public class GlobalProtectionListener implements Listener {

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

		Player player = event.getPlayer();
		if (player.getGameMode() == GameMode.CREATIVE) return;
		if (player.hasPermission("animalhide.bypass.interact")) return;

		Block block = event.getClickedBlock();
		if (block == null) return;

		if (isProtectedInteractable(block.getType())) {
			event.setUseInteractedBlock(Event.Result.DENY);
		}
	}

	/**
	 * 判定是否属于"会被玩家右键改变状态/打开"的可交互方块
	 */
	private boolean isProtectedInteractable(Material type) {
		return Tag.DOORS.isTagged(type)
				|| Tag.TRAPDOORS.isTagged(type)
				|| Tag.FENCE_GATES.isTagged(type)
				|| Tag.BUTTONS.isTagged(type)
				|| Tag.SIGNS.isTagged(type)
				|| Tag.SHULKER_BOXES.isTagged(type)
				|| type == Material.LEVER;
	}
}
