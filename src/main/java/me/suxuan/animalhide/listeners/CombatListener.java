package me.suxuan.animalhide.listeners;

import com.destroystokyo.paper.event.entity.ProjectileCollideEvent;
import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.GameManager;
import me.suxuan.animalhide.game.GameState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
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
		if (arena.getSpectators().contains(attacker.getUniqueId())) {
			event.setCancelled(true);
			return;
		}

		// 寻找者砍错 AI
		if (arena.getAiAnimals().contains(event.getEntity())) {
			if (arena.getSeekers().contains(attacker.getUniqueId())) {
				event.setDamage(0);
				event.setCancelled(true);

				attacker.playSound(attacker.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.5f);
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
			}
		} else if (attackerIsHider && victimIsSeeker) {
			// 躲藏者 射中/攻击 寻找者
			if (event.getDamager() instanceof Projectile) {
				event.setDamage(0.1); // 仅触发击退，不造成致死伤害

				// 让寻找者短暂发光，暴露其位置给别的躲藏者
				victim.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, false, false, false));

				int hits = arena.getArrowHits().getOrDefault(attacker.getUniqueId(), 0) + 1;
				arena.getArrowHits().put(attacker.getUniqueId(), hits);

				if (hits > 15) {
					attacker.sendActionBar(Component.text("命中寻找者！弓箭已达到满级", NamedTextColor.GREEN));
				}

				if (hits % 5 == 0) { // 每命中 5 次升级一次
					ItemStack bow = attacker.getInventory().getItem(1); // 假设弓在第2格
					if (bow != null && bow.getType() == Material.BOW) {
						int currentKb = bow.getEnchantmentLevel(Enchantment.KNOCKBACK);
						bow.addUnsafeEnchantment(Enchantment.KNOCKBACK, currentKb + 1);

						attacker.sendMessage(Component.text("精准射击！你的弓击退等级已升至 " + (currentKb + 1) + "！", NamedTextColor.GOLD));
						attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
					}
				} else {
					attacker.sendActionBar(Component.text("命中寻找者！距离下次弓箭升级还需 " + (5 - (hits % 5)) + " 次", NamedTextColor.GREEN));
					attacker.playSound(attacker.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
				}
			} else {
				// 如果是近战敲击，直接取消伤害
				event.setCancelled(true);
			}
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
