package me.suxuan.animalhide.listeners;

import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.GameManager;
import me.suxuan.animalhide.game.GameState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

public class GameRuleListener implements Listener {

	private final GameManager gameManager;

	public GameRuleListener(GameManager gameManager) {
		this.gameManager = gameManager;
	}

	/**
	 * 防止玩家在游戏房间内破坏方块
	 */
	@EventHandler
	public void onBlockBreak(BlockBreakEvent event) {
		Player player = event.getPlayer();
		Arena arena = gameManager.getArenaByPlayer(player);
		if (arena != null) {
			event.setCancelled(true);
		}
	}

	/**
	 * 防止玩家在游戏房间内放置方块
	 */
	@EventHandler
	public void onBlockPlace(BlockPlaceEvent event) {
		Player player = event.getPlayer();
		Arena arena = gameManager.getArenaByPlayer(player);
		if (arena != null) {
			event.setCancelled(true);
		}
	}

	/**
	 * 取消玩家饥饿变化
	 */
	@EventHandler
	public void onFoodLevelChange(FoodLevelChangeEvent event) {
		if (event.getEntity() instanceof Player player) {
			if (gameManager.getArenaByPlayer(player) != null) {
				event.setCancelled(true);
				player.setFoodLevel(20);
			}
		}
	}

	/**
	 * 防止玩家在游戏中丢弃物品
	 */
	@EventHandler
	public void onPlayerDropItem(PlayerDropItemEvent event) {
		Player player = event.getPlayer();
		Arena arena = gameManager.getArenaByPlayer(player);

		// 如果玩家在游戏中，禁止丢弃任何物品
		if (arena != null && arena.getState() == GameState.PLAYING) {
			event.setCancelled(true);
		}
	}

	/**
	 * 全局环境伤害控制
	 */
	@EventHandler
	public void onEntityDamage(EntityDamageEvent event) {
		if (!(event.getEntity() instanceof Player player)) return;

		Arena arena = gameManager.getArenaByPlayer(player);
		if (arena == null) return;

		if (arena.getState() != GameState.PLAYING) {
			event.setCancelled(true);
			return;
		}

		if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK &&
				event.getCause() != EntityDamageEvent.DamageCause.PROJECTILE) {
			event.setCancelled(true);
		}
	}
}
