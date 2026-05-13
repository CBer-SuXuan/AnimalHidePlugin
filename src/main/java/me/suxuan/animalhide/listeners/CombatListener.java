package me.suxuan.animalhide.listeners;

import com.destroystokyo.paper.event.entity.ProjectileCollideEvent;
import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.GameManager;
import me.suxuan.animalhide.game.GameState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
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
		Player attacker = null;

		// 智能获取攻击者：无论是近战还是远程射击
		if (event.getDamager() instanceof Player p) {
			attacker = p;
		} else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
			attacker = p;
		}

		if (attacker == null) {
			for (Arena arena : gameManager.getArenas().values()) {
				if (arena.getState() == GameState.PLAYING && arena.getAiAnimals().contains(event.getDamager())) {
					event.setCancelled(true);
				}
			}
			return;
		}

		Arena arena = gameManager.getArenaByPlayer(attacker);
		if (arena == null || arena.getState() != GameState.PLAYING) return;

		// 寻找者砍错 AI
		if (arena.getAiAnimals().contains(event.getEntity())) {
			if (arena.getSeekers().contains(attacker.getUniqueId())) {
				event.setDamage(0);
				attacker.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, false, false, false));
				attacker.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false, false));
			}
			// 躲藏者不小心射中 AI，直接取消伤害，无惩罚
			if (arena.getHiders().contains(attacker.getUniqueId())) {
				event.setCancelled(true);
			}
			return;
		}

		if (!(event.getEntity() instanceof Player victim)) return;

		if (arena.getSpectators().contains(victim.getUniqueId())) {
			event.setCancelled(true);
			return;
		}

		boolean attackerIsSeeker = arena.getSeekers().contains(attacker.getUniqueId());
		boolean victimIsHider = arena.getHiders().contains(victim.getUniqueId());

		boolean attackerIsHider = arena.getHiders().contains(attacker.getUniqueId());
		boolean victimIsSeeker = arena.getSeekers().contains(victim.getUniqueId());

		if (attackerIsSeeker && victimIsHider) {
			if (event.getDamager() instanceof Projectile) {
				event.setDamage(8);
				attacker.sendActionBar(Component.text("命中躲藏者！", NamedTextColor.GREEN));
			}
			// 寻找者 攻击 躲藏者
			if (victim.getHealth() - event.getFinalDamage() <= 0) {
				event.setCancelled(true);
				handleHiderDeath(arena, victim, attacker);
			} else {
				victim.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, false, false, false));
				victim.sendActionBar(Component.text("你受到了惊吓！快逃！", NamedTextColor.YELLOW));
			}
		} else if (attackerIsHider && victimIsSeeker) {
			// 躲藏者 射中/攻击 寻找者
			// 仅造成极小的伤害(0.1)用来触发原版的物理击退动作，不致死
			event.setDamage(0.1);
			// 让寻找者短暂发光，暴露其位置给别的躲藏者
			victim.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, false, false, false));
			attacker.sendActionBar(Component.text("命中寻找者！", NamedTextColor.GREEN));
		} else {
			// 禁止同阵营互殴
			event.setCancelled(true);
		}
	}

	/**
	 * 让飞行道具 (如弓箭) 完美穿透旁观者
	 */
	@EventHandler
	public void onProjectileCollide(ProjectileCollideEvent event) {
		if (event.getCollidedWith() instanceof Player victim) {
			Arena arena = gameManager.getArenaByPlayer(victim);
			if (arena != null && arena.getSpectators().contains(victim.getUniqueId())) {
				event.setCancelled(true);
			}
		}
	}

	/**
	 * 处理躲藏者被击杀的逻辑
	 */
	private void handleHiderDeath(Arena arena, Player victim, Player killer) {
		gameManager.processHiderFound(arena, victim, killer);
	}
}
