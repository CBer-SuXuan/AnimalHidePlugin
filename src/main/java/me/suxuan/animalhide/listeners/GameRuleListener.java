package me.suxuan.animalhide.listeners;

import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.GameManager;
import me.suxuan.animalhide.game.GameState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
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
		if (arena != null) {
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

	/**
	 * 防止玩家在游戏中捡起地上的掉落物
	 */
	@EventHandler
	public void onEntityPickupItem(EntityPickupItemEvent event) {
		if (event.getEntity() instanceof Player player) {
			Arena arena = gameManager.getArenaByPlayer(player);

			if (arena != null && arena.getState() == GameState.PLAYING) {
				// 拦截默认的物品进入背包动作
				event.setCancelled(true);

				// 检查是不是寻找者，且捡起的是不是便便 (可可豆)
				Item item = event.getItem();
				if (item.getItemStack().getType() == Material.COCOA_BEANS) {
					if (arena.getSeekers().contains(player.getUniqueId())) {
						// 寻找者捡到了便便！
						arena.addMatchScore(player.getUniqueId(), 5);
						player.sendMessage(Component.text("你发现了便便！来源: ", NamedTextColor.GREEN)
								.append(Component.text(item.getName(), NamedTextColor.YELLOW))
								.append(Component.text(" (积分 +5)", NamedTextColor.GRAY)));

						player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
						item.remove();
					}
				}
			}
		}
	}

	/**
	 * 防止原版 AI 生物互相攻击 (如狼吃羊) 以及生物锁定玩家
	 */
	@EventHandler
	public void onEntityTarget(EntityTargetEvent event) {
		// 如果目标是玩家，且玩家在游戏中，取消怪物的仇恨锁定
		if (event.getTarget() instanceof Player player) {
			Arena arena = gameManager.getArenaByPlayer(player);
			if (arena != null && arena.getState() == GameState.PLAYING) {
				event.setCancelled(true);
				return;
			}
		}

		// 如果是 AI 动物之间的互相锁定 (如狼锁定羊)，通过所在世界来判断并取消
		for (Arena arena : gameManager.getActiveMatches()) {
			if (arena.getState() == GameState.PLAYING) {
				// 如果这个发生寻敌事件的实体，处在正在游戏的地图世界中，就取消它的寻敌行为
				if (arena.getHiderSpawn() != null && event.getEntity().getWorld().equals(arena.getHiderSpawn().getWorld())) {
					event.setCancelled(true);
					break;
				}
			}
		}
	}

	/**
	 * 防止羊吃草破坏地形
	 */
	@EventHandler
	public void onEntityChangeBlock(EntityChangeBlockEvent event) {
		if (event.getEntity() instanceof Player player) {
			if (player.getGameMode() == GameMode.CREATIVE) {
				return;
			}
		}
		event.setCancelled(true);
	}

	/**
	 * 封印末影人：防止 AI 末影人随机瞬移或受击瞬移
	 */
	@EventHandler
	public void onEntityTeleport(EntityTeleportEvent event) {
		for (Arena arena : gameManager.getActiveMatches()) {
			if (arena.getState() == GameState.PLAYING && arena.getAiAnimals().contains(event.getEntity())) {
				event.setCancelled(true);
				return;
			}
		}
	}

	/**
	 * 封印亡灵：防止 AI 僵尸和骷髅在白天自燃
	 */
	@EventHandler
	public void onEntityCombust(EntityCombustEvent event) {
		for (Arena arena : gameManager.getActiveMatches()) {
			if (arena.getState() == GameState.PLAYING && arena.getAiAnimals().contains(event.getEntity())) {
				event.setCancelled(true);
				return;
			}
		}
	}

	/**
	 * 封印苦力怕：防止 AI 苦力怕因意外情况触发爆炸引信
	 */
	@EventHandler
	public void onExplosionPrime(ExplosionPrimeEvent event) {
		for (Arena arena : gameManager.getActiveMatches()) {
			if (arena.getState() == GameState.PLAYING && arena.getAiAnimals().contains(event.getEntity())) {
				event.setCancelled(true);
				return;
			}
		}
	}

	/**
	 * 防止鸡下蛋
	 */
	@EventHandler
	public void onTutorialChickenDrop(EntityDropItemEvent event) {
		event.setCancelled(true);
	}

}
