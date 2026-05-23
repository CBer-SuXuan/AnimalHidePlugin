package me.suxuan.animalhide.listeners;

import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.GameManager;
import me.suxuan.animalhide.game.GameState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
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
		if (!(event.getEntity() instanceof Player player)) return;

		Arena arena = gameManager.getArenaByPlayer(player);
		if (arena == null) return;

		event.setCancelled(true);
		player.setFoodLevel(20);
		if (arena.getState() == GameState.PLAYING) {
			if (arena.getHiders().contains(player.getUniqueId())) {
				player.setSaturation(0f);
			} else if (arena.getSeekers().contains(player.getUniqueId())) {
				player.setSaturation(20f);
			}
		}
	}

	/**
	 * 禁止躲藏者在局内通过饥饿/饱和度机制自然回血；寻找者不受影响。
	 */
	@EventHandler
	public void onEntityRegainHealth(EntityRegainHealthEvent event) {
		if (!(event.getEntity() instanceof Player player)) return;

		Arena arena = gameManager.getArenaByPlayer(player);
		if (arena == null || arena.getState() != GameState.PLAYING) return;
		if (!arena.getHiders().contains(player.getUniqueId())) return;

		EntityRegainHealthEvent.RegainReason reason = event.getRegainReason();
		if (reason == EntityRegainHealthEvent.RegainReason.SATIATED
				|| reason == EntityRegainHealthEvent.RegainReason.REGEN) {
			event.setCancelled(true);
		}
	}

	/**
	 * 场景 AI 死亡不掉落物品与经验
	 */
	@EventHandler
	public void onAiAnimalDeath(EntityDeathEvent event) {
		for (Arena arena : gameManager.getActiveMatches()) {
			if (arena.getState() != GameState.PLAYING) continue;
			if (arena.getAiAnimals().contains(event.getEntity())) {
				event.getDrops().clear();
				event.setDroppedExp(0);
				return;
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

		if (arena.getTemplate().isQueueRoom() && event.getCause() == EntityDamageEvent.DamageCause.VOID) {
			event.setCancelled(true);
			if (arena.getWaitingLobby() != null) {
				player.teleportAsync(arena.getWaitingLobby());
				player.sendActionBar(Component.text("已将你传回队列出生点。", NamedTextColor.YELLOW));
			}
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
		if (!(event.getEntity() instanceof Player player)) return;
		Arena arena = gameManager.getArenaByPlayer(player);
		if (arena == null || arena.getState() != GameState.PLAYING) return;

		Item item = event.getItem();
		if (item.getItemStack().getType() == Material.COCOA_BEANS) {
			event.setCancelled(true);
			if (!arena.getSeekers().contains(player.getUniqueId())) {
				return;
			}

			me.suxuan.animalhide.skill.hider.TauntTraceSupport.PoopMarker marker = me.suxuan.animalhide.AnimalHidePlugin.getInstance()
					.getTauntTraceSupport()
					.takeMarkerByItem(item.getUniqueId());
			if (marker == null) {
				return;
			}

			int reward = arena.getTemplate().getScoring().getSeekerPickupPoop();
			arena.addMatchScore(player.getUniqueId(), reward);
			player.sendMessage(Component.text("你捡到了便便线索！", NamedTextColor.GREEN)
					.append(Component.text(" 积分 +" + reward, NamedTextColor.YELLOW))
					.append(Component.text(" · 来源: " + marker.displayName(), NamedTextColor.GRAY)));
			player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
			item.remove();
			me.suxuan.animalhide.AnimalHidePlugin.getInstance().getTauntTraceSupport().refreshSeekers(arena, null, true);
			return;
		}

		event.setCancelled(true);
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
		if (isArenaWorld(event.getEntity().getWorld())) {
			event.setCancelled(true);
		}
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
		String customName = event.getEntity().getCustomName();
		boolean tutorialNpc = customName != null && customName.contains("嘲讽");
		if (tutorialNpc || isArenaWorld(event.getEntity().getWorld())) {
			event.setCancelled(true);
		}
	}

	private boolean isArenaWorld(World world) {
		for (Arena arena : gameManager.getActiveMatches()) {
			if (arena.getCurrentWorld() != null && arena.getCurrentWorld().equals(world)) {
				return true;
			}
		}
		return false;
	}

}
