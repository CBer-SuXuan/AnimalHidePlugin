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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class CombatListener implements Listener {

	private final GameManager gameManager;

	public CombatListener(GameManager gameManager) {
		this.gameManager = gameManager;
	}

	/**
	 * 核心战斗与击杀逻辑判定
	 */
	@EventHandler
	public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
		// 确保攻击者和受害者都是玩家
		if (!(event.getDamager() instanceof Player attacker)) {
			for (Arena arena : gameManager.getArenas().values()) {
				if (arena.getState() == GameState.PLAYING && arena.getAiAnimals().contains(event.getDamager())) {
					event.setCancelled(true);
				}
			}
			return;
		}

		Arena arena = gameManager.getArenaByPlayer(attacker);

		if (arena == null || arena.getState() != GameState.PLAYING) {
			return;
		}

		if (arena.getAiAnimals().contains(event.getEntity())) {
			if (arena.getSeekers().contains(attacker.getUniqueId())) {
				event.setDamage(0);

				attacker.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, false, false, false));
				attacker.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false, false));
			}
			return;
		}

		if (!(event.getEntity() instanceof Player victim)) {
			return;
		}

		boolean attackerIsSeeker = arena.getSeekers().contains(attacker.getUniqueId());
		boolean victimIsHider = arena.getHiders().contains(victim.getUniqueId());

		if (attackerIsSeeker && victimIsHider) {
			// 检查这次攻击是否致命
			if (victim.getHealth() - event.getFinalDamage() <= 0) {
				event.setCancelled(true); // 取消原版死亡事件，防止掉落物品和重生屏幕
				handleHiderDeath(arena, victim, attacker);
			} else {
				victim.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, false, false, false));
				victim.sendActionBar(Component.text("你受到了惊吓！快逃！", NamedTextColor.YELLOW));
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

		gameManager.equipSeeker(victim);

		if (arena.getHiders().isEmpty()) {
			gameManager.endGame(arena, PlayerRole.SEEKER);
		}
	}
}
