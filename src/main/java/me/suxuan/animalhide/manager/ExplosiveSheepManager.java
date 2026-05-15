package me.suxuan.animalhide.manager;

import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.GameState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class ExplosiveSheepManager {

	private final AnimalHidePlugin plugin;

	public ExplosiveSheepManager(AnimalHidePlugin plugin) {
		this.plugin = plugin;
	}

	public void spawnSheep(Player seeker, Arena arena) {
		if (seeker.hasCooldown(Material.SHEEP_SPAWN_EGG)) return;

		Location loc = seeker.getLocation();
		Sheep bombSheep = (Sheep) loc.getWorld().spawnEntity(loc, EntityType.SHEEP);
		bombSheep.setAI(false);
		bombSheep.setCustomName("§c§l即将爆炸...");
		bombSheep.setCustomNameVisible(true);
		bombSheep.setInvulnerable(true);

		seeker.setCooldown(Material.SHEEP_SPAWN_EGG, 20 * 20); // 20秒冷却

		new BukkitRunnable() {
			int ticks = 30;
			boolean isRed = false;

			@Override
			public void run() {
				if (arena.getState() != GameState.PLAYING || !bombSheep.isValid()) {
					bombSheep.remove();
					cancel();
					return;
				}

				if (ticks > 0) {
					isRed = !isRed;
					bombSheep.setColor(isRed ? DyeColor.RED : DyeColor.WHITE);

					// 随着倒计时，音调越来越高，节奏极快
					float pitch = 1.0f + ((30 - ticks) * 0.03f);
					bombSheep.getWorld().playSound(bombSheep.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, pitch);

					ticks--;
				} else {
					executeExplosion(bombSheep, seeker, arena);
					bombSheep.remove();
					cancel();
				}
			}
		}.runTaskTimer(plugin, 0L, 5L);
	}

	private void executeExplosion(Sheep bombSheep, Player seeker, Arena arena) {
		Location loc = bombSheep.getLocation();
		loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1);
		loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);

		double radius = 6.0;
		List<Entity> nearby = (List<Entity>) loc.getWorld().getNearbyEntities(loc, radius, radius, radius);

		for (Entity entity : nearby) {
			if (arena.getAiAnimals().contains(entity)) {
				EntityType type = entity.getType();
				Location respawnLoc = entity.getLocation();

				entity.remove();
				arena.getAiAnimals().remove(entity);

				new BukkitRunnable() {
					@Override
					public void run() {
						if (arena.getState() == GameState.PLAYING) {
							Entity newAi = respawnLoc.getWorld().spawnEntity(respawnLoc, type);
							if (newAi instanceof LivingEntity living) living.setAI(false);
							newAi.setSilent(true);
							arena.getAiAnimals().add(newAi);
						}
					}
				}.runTaskLater(plugin, 60L);
			}

			if (entity instanceof Player victim && arena.getHiders().contains(victim.getUniqueId())) {
				victim.damage(10.0, seeker);
				victim.sendActionBar(Component.text("⚠ 你受到了爆炸绵羊的冲击！", NamedTextColor.RED));
				seeker.sendActionBar(Component.text("✔ 爆炸命中了躲藏者！", NamedTextColor.GREEN));
			}
		}
	}
}