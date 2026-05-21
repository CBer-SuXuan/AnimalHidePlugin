package me.suxuan.animalhide.listeners;

import com.destroystokyo.paper.event.entity.ProjectileCollideEvent;
import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.GameManager;
import me.suxuan.animalhide.game.GameState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

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
			for (Arena arena : gameManager.getActiveMatches()) {
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

		// 场景 AI：寻找者误伤取消；躲藏者禁止攻击
		if (arena.getAiAnimals().contains(event.getEntity())) {
			event.setCancelled(true);
			if (arena.getSeekers().contains(attacker.getUniqueId())) {
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

		// 禁止任何形式的同阵营内斗（寻找者贴脸互打、躲藏者互射）
		if ((attackerIsSeeker && victimIsSeeker) || (attackerIsHider && victimIsHider)) {
			event.setCancelled(true);
			return;
		}

		if (attackerIsSeeker && victimIsHider) {
			if (event.getDamager() instanceof Projectile) {
				attacker.sendActionBar(Component.text("命中躲藏者！", NamedTextColor.GREEN));
			}
			// 寻找者 攻击 躲藏者
			if (victim.getHealth() - event.getFinalDamage() <= 0) {
				event.setCancelled(true);
				handleHiderDeath(arena, victim, attacker);
			}
		} else if (attackerIsHider && victimIsSeeker) {
			// 躲藏者 射中/近战 寻找者：低伤害 + 暴露位置，弓命中另计升级进度
			event.setDamage(0.1);
			victim.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, false, false, false));

			if (event.getDamager() instanceof Projectile) {
				int hits = arena.getArrowHits().getOrDefault(attacker.getUniqueId(), 0) + 1;
				arena.getArrowHits().put(attacker.getUniqueId(), hits);

				final int maxHits = 15;
				if (hits <= maxHits && hits % 5 == 0) {
					ItemStack bow = attacker.getInventory().getItem(1);
					if (bow != null && bow.getType() == Material.BOW) {
						int currentKb = bow.getEnchantmentLevel(Enchantment.KNOCKBACK);
						int newKb = Math.min(3, currentKb + 1);
						if (newKb > currentKb) {
							bow.addUnsafeEnchantment(Enchantment.KNOCKBACK, newKb);
							attacker.sendMessage(Component.text("精准射击！你的弓击退等级已升至 " + newKb + "！", NamedTextColor.GOLD));
							attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
						}
					}
				} else if (hits < maxHits) {
					attacker.sendActionBar(Component.text("命中寻找者！距离下次弓箭升级还需 " + (5 - (hits % 5)) + " 次", NamedTextColor.GREEN));
					attacker.playSound(attacker.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
				} else {
					attacker.sendActionBar(Component.text("命中寻找者！弓箭已满级 (击退III)", NamedTextColor.GREEN));
					attacker.playSound(attacker.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
				}
			} else {
				attacker.sendActionBar(Component.text("近战命中寻找者！", NamedTextColor.GREEN));
				attacker.playSound(attacker.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
			}
		}
	}

	/**
	 * LibsDisguises 对地面上的动物伪装常不发送击退速度包，导致躲藏者几乎不会被击退。
	 * 在伤害结算后于下一 tick 手动补一次原版击退（仅寻找者近战命中躲藏者）。
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onSeekerMeleeKnockback(EntityDamageByEntityEvent event) {
		if (!(event.getEntity() instanceof Player victim)) return;
		if (!(event.getDamager() instanceof Player attacker)) return;

		Arena arena = gameManager.getArenaByPlayer(attacker);
		if (arena == null || arena.getState() != GameState.PLAYING) return;
		if (!arena.getSeekers().contains(attacker.getUniqueId())) return;
		if (!arena.getHiders().contains(victim.getUniqueId())) return;

		int kbLevel = attacker.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.KNOCKBACK);
		if (kbLevel <= 0) return;

		AnimalHidePlugin.getInstance().getServer().getScheduler().runTask(AnimalHidePlugin.getInstance(), () -> {
			if (!victim.isOnline() || !attacker.isOnline()) return;
			applyMeleeKnockback(attacker, victim, kbLevel, attacker.isSprinting());
		});
	}

	private void applyMeleeKnockback(Player attacker, Player victim, int knockbackLevel, boolean sprinting) {
		// Paper knockback(x,z) 表示击退「来源」方向，实体向相反方向被推
		Location attackerLoc = attacker.getLocation();
		Location victimLoc = victim.getLocation();
		double sourceX = attackerLoc.getX() - victimLoc.getX();
		double sourceZ = attackerLoc.getZ() - victimLoc.getZ();
		double horizontalDist = Math.hypot(sourceX, sourceZ);

		if (horizontalDist < 1e-6) {
			Vector behindAttacker = attackerLoc.getDirection().multiply(-1);
			sourceX = behindAttacker.getX();
			sourceZ = behindAttacker.getZ();
			horizontalDist = Math.hypot(sourceX, sourceZ);
			if (horizontalDist < 1e-6) return;
		}

		double strength = 0.4 + knockbackLevel * 0.5;
		if (sprinting) {
			strength += 0.5;
		}
		victim.knockback(strength, sourceX / horizontalDist, sourceZ / horizontalDist);
	}

	/**
	 * 修复：躲藏者的击退弓射偏的箭会落地变成可拾取物品，导致捡起来累积。
	 * 直接在射出瞬间把箭的拾取状态改为 DISALLOWED，所有玩家都捡不起来。
	 * 寻找者的弓有 INFINITY 附魔，射出的箭天然就是 CREATIVE_ONLY，无需额外处理。
	 */
	@EventHandler
	public void onHiderShootBow(EntityShootBowEvent event) {
		if (!(event.getEntity() instanceof Player player)) return;

		Arena arena = gameManager.getArenaByPlayer(player);
		if (arena == null || arena.getState() != GameState.PLAYING) return;
		if (!arena.getHiders().contains(player.getUniqueId())) return;

		if (arena.isHidePhase()) {
			event.setCancelled(true);
			player.sendActionBar(Component.text("躲藏阶段无法使用弓箭！", NamedTextColor.RED));
			return;
		}

		if (arena.getDecoyAnchors().containsKey(player.getUniqueId())) {
			event.setCancelled(true);
			player.sendActionBar(Component.text("定点伪装中无法使用弓箭！", NamedTextColor.RED));
			return;
		}

		if (event.getProjectile() instanceof AbstractArrow arrow) {
			arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
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
