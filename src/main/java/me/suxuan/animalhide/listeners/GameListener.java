package me.suxuan.animalhide.listeners;

import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.GameManager;
import me.suxuan.animalhide.game.GameState;
import me.suxuan.animalhide.game.PlayerRole;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 游戏事件监听器
 * 负责处理伤害判定、防止地图破坏以及掉线处理
 */
public class GameListener implements Listener {

	private final GameManager gameManager;

	public GameListener(GameManager gameManager) {
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
	 * 处理中途断开连接的玩家
	 */
	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		Arena arena = gameManager.getArenaByPlayer(player);
		if (arena != null) {
			arena.removePlayer(player);
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

	/**
	 * 核心战斗与击杀逻辑判定
	 */
	@EventHandler
	public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
		// 确保攻击者和受害者都是玩家
		if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player victim)) {
			return;
		}

		Arena arena = gameManager.getArenaByPlayer(attacker);

		if (arena == null || arena != gameManager.getArenaByPlayer(victim) || arena.getState() != GameState.PLAYING) {
			return;
		}

		boolean attackerIsSeeker = arena.getSeekers().contains(attacker.getUniqueId());
		boolean victimIsHider = arena.getHiders().contains(victim.getUniqueId());

		// 规则 1：只允许寻找者攻击躲藏者
		if (attackerIsSeeker && victimIsHider) {
			// 检查这次攻击是否致命
			if (victim.getHealth() - event.getFinalDamage() <= 0) {
				event.setCancelled(true); // 取消原版死亡事件，防止掉落物品和重生屏幕
				handleHiderDeath(arena, victim, attacker);
			}
		} else {
			// 禁止同阵营互相攻击，禁止躲藏者攻击寻找者
			event.setCancelled(true);
		}
	}

	/**
	 * 处理躲藏者被击杀的逻辑
	 */
	private void handleHiderDeath(Arena arena, Player victim, Player killer) {
		arena.broadcast(Component.text("☠ ", NamedTextColor.GRAY)
				.append(Component.text(victim.getName(), NamedTextColor.RED))
				.append(Component.text(" 被 ", NamedTextColor.GRAY))
				.append(Component.text(killer.getName(), NamedTextColor.AQUA))
				.append(Component.text(" 找到了！", NamedTextColor.GRAY)));

		arena.getHiders().remove(victim.getUniqueId());
		arena.getSeekers().add(victim.getUniqueId());

		victim.setHealth(20.0);
		AnimalHidePlugin.getInstance().getDisguiseManager().undisguisePlayer(victim);
		victim.teleportAsync(arena.getSeekerSpawn());
		victim.sendMessage(Component.text("你已经被发现！现在你加入了寻找者阵营！", NamedTextColor.YELLOW));

		if (arena.getHiders().isEmpty()) {
			gameManager.endGame(arena, PlayerRole.SEEKER);
		}
	}

}