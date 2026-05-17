package me.suxuan.animalhide.manager;

import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.GameState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

public class ExplosiveSheepManager {

	private final AnimalHidePlugin plugin;

	public ExplosiveSheepManager(AnimalHidePlugin plugin) {
		this.plugin = plugin;
	}

	public void spawnSheep(Player seeker, Arena arena) {
		Location spawnLoc = resolveGroundSpawn(seeker);

		Sheep bombSheep = (Sheep) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.SHEEP);
		bombSheep.setAI(false);
		bombSheep.setCustomName("§c§l即将爆炸...");
		bombSheep.setCustomNameVisible(true);
		bombSheep.setInvulnerable(true);
		bombSheep.setGravity(false);
		bombSheep.setRotation(spawnLoc.getYaw(), 0f);
		bombSheep.setVelocity(new Vector(0, 0, 0));

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

	/**
	 * 在寻找者面前找一块结实的方块顶面作为爆炸羊的落点。
	 * 思路：从寻找者头部位置出发，垂直向下扫描，直到找到一个上方两格空、下方有固体方块的位置。
	 * 这样无论寻找者是在跳跃、卡在边缘、还是站在台阶上，绵羊都不会浮空或卡进墙体。
	 */
	private Location resolveGroundSpawn(Player seeker) {
		Location origin = seeker.getLocation();
		World world = origin.getWorld();
		int blockX = origin.getBlockX();
		int blockZ = origin.getBlockZ();

		// 从玩家头顶往下扫，最多扫 8 格
		int startY = origin.getBlockY() + 1;
		int minY = Math.max(world.getMinHeight(), origin.getBlockY() - 8);
		for (int y = startY; y >= minY; y--) {
			Block ground = world.getBlockAt(blockX, y - 1, blockZ);
			Block feet = world.getBlockAt(blockX, y, blockZ);
			Block head = world.getBlockAt(blockX, y + 1, blockZ);
			if (ground.getType().isSolid() && feet.isPassable() && head.isPassable()) {
				Location result = new Location(world, blockX + 0.5, y, blockZ + 0.5);
				// 复用玩家朝向，pitch 强制为 0 让绵羊水平站立面朝前方
				result.setYaw(origin.getYaw());
				result.setPitch(0f);
				return result;
			}
		}

		// 兜底：找不到就用玩家自身坐标但 pitch=0
		Location fallback = origin.clone();
		fallback.setPitch(0f);
		return fallback;
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