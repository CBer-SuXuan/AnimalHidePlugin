package me.suxuan.animalhide.manager;

import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.game.Arena;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class AISpawnManager {

	private final AnimalHidePlugin plugin;
	private final Random random = new Random();

	public AISpawnManager(AnimalHidePlugin plugin) {
		this.plugin = plugin;
	}

	public void spawnAIEntities(Arena arena, List<String> allowedEntities) {
		List<Location> centers = arena.getAiSpawns();
		if (centers == null || centers.isEmpty()) {
			plugin.getComponentLogger().error("地图 {} 没有配置 AI 生成中心点(ai-spawns)！无法生成动物。", arena.getArenaName());
			return;
		}

		World world = centers.getFirst().getWorld();
		if (allowedEntities.isEmpty() || world == null) return;

		int totalAnimals = arena.getAiAnimalCount();
		int spawnedCount = 0;
		int maxAttempts = totalAnimals * 5;
		int attempts = 0;

		Set<String> occupiedBlocks = new HashSet<>();

		// 采用群落式生成，围绕预设的中心点散开
		while (spawnedCount < totalAnimals && attempts < maxAttempts) {
			attempts++;

			Location clusterCenter = centers.get(random.nextInt(centers.size()));

			Location spawnLoc = getValidLocationNear(clusterCenter, 5.0);
			if (spawnLoc == null) continue;

			String blockKey = spawnLoc.getBlockX() + "," + spawnLoc.getBlockY() + "," + spawnLoc.getBlockZ();
			if (occupiedBlocks.contains(blockKey)) {
				continue;
			}

			spawnLoc.setYaw(random.nextFloat() * 360f);
			occupiedBlocks.add(blockKey);

			String animalStr = allowedEntities.get(random.nextInt(allowedEntities.size()));
			try {
				EntityType type = EntityType.valueOf(animalStr.toUpperCase());
				Entity entity = world.spawnEntity(spawnLoc, type);

				entity.setSilent(true);
				if (entity instanceof LivingEntity living) {
					living.setAI(false);
					living.setCollidable(true);
				}

				applyEntityVariants(entity);
				arena.getAiAnimals().add(entity);
				spawnedCount++;

			} catch (IllegalArgumentException e) {
				plugin.getComponentLogger().warn("未知动物类型: {}", animalStr);
			}
		}

		plugin.getComponentLogger().info("地图 {} 配置需生成 {} 只，实际成功生成 {} 只动物。", arena.getArenaName(), totalAnimals, spawnedCount);
	}

	private Location getValidLocationNear(Location center, double radius) {
		World world = center.getWorld();

		double offsetX = (random.nextDouble() * 2 - 1) * radius;
		double offsetZ = (random.nextDouble() * 2 - 1) * radius;

		int blockX = (int) Math.floor(center.getX() + offsetX);
		int blockZ = (int) Math.floor(center.getZ() + offsetZ);

		for (int y = center.getBlockY() + 4; y >= center.getBlockY() - 4; y--) {
			Block feet = world.getBlockAt(blockX, y, blockZ);
			Block head = world.getBlockAt(blockX, y + 1, blockZ);
			Block ground = world.getBlockAt(blockX, y - 1, blockZ);

			if (isSafeLocation(feet, head, ground)) {
				return new Location(world, blockX + 0.5, y, blockZ + 0.5);
			}
		}
		return null;
	}

	private boolean isSafeLocation(Block feet, Block head, Block ground) {
		if (!feet.isPassable() || !head.isPassable() || !ground.getType().isSolid()) return false;
		Material groundType = ground.getType();
		String typeName = groundType.name();
		if (groundType == Material.MAGMA_BLOCK || groundType == Material.CACTUS || groundType == Material.CAMPFIRE)
			return false;
		return !typeName.endsWith("_STAIRS") && !typeName.endsWith("_SLAB") && !typeName.endsWith("_LEAVES") && !typeName.endsWith("GLASS");
	}

	private void applyEntityVariants(Entity entity) {
		if (entity instanceof Sheep sheep) sheep.setColor(DyeColor.WHITE);
		else if (entity instanceof Wolf wolf) {
			try {
				wolf.setVariant(Registry.WOLF_VARIANT.get(NamespacedKey.minecraft("pale")));
			} catch (Throwable ignored) {
			}
		} else if (entity instanceof Cat cat) {
			try {
				cat.setCatType(Registry.CAT_VARIANT.get(NamespacedKey.minecraft("tabby")));
			} catch (Throwable ignored) {
			}
		}
	}
}