package me.suxuan.animalhide.manager;

import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.game.Arena;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;

import java.util.List;
import java.util.Random;

/**
 * 专用于处理躲猫猫 AI 动物生成的管理器
 * 采用 3D 随机投点与群落式生成算法
 */
public class AISpawnManager {

	private final AnimalHidePlugin plugin;
	private final Random random = new Random();

	public AISpawnManager(AnimalHidePlugin plugin) {
		this.plugin = plugin;
	}

	/**
	 * 在地图区域内以“群落”形式生成静止的 AI 实体
	 *
	 * @param arena           目标房间
	 * @param allowedEntities 允许生成的实体类型列表
	 */
	public void spawnAIEntities(Arena arena, List<String> allowedEntities) {
		Location pos1 = arena.getPos1();
		Location pos2 = arena.getPos2();

		if (pos1 == null || pos2 == null || pos1.getWorld() == null) {
			plugin.getComponentLogger().error("地图 {} 的边界坐标未设置，无法生成 AI！", arena.getArenaName());
			return;
		}

		World world = pos1.getWorld();
		if (allowedEntities.isEmpty()) return;

		int totalAnimals = arena.getAiAnimalCount();
		int spawnedCount = 0;
		int maxAttempts = totalAnimals * 5; // 防止死循环的安全阈值
		int attempts = 0;

		// 采用群落式生成，直到生成数量达到设定上限
		while (spawnedCount < totalAnimals && attempts < maxAttempts) {
			attempts++;

			// 1. 获取一个合法的“群落中心点”
			Location clusterCenter = getRandomSafeLocation(pos1, pos2);
			if (clusterCenter == null) continue; // 如果找不到安全点，重新尝试

			// 2. 随机决定这个群落包含的动物数量 (例如 2 到 6 只)
			int animalsPerCluster = 2 + random.nextInt(5);
			int toSpawnHere = Math.min(animalsPerCluster, totalAnimals - spawnedCount);

			for (int i = 0; i < toSpawnHere; i++) {
				// 3. 在群落中心附近随机找一个位置，半径设为 3.0 格
				Location spawnLoc = getValidLocationNear(clusterCenter, 3.0);
				if (spawnLoc == null) spawnLoc = clusterCenter.clone(); // 找不到就堆叠在中心点

				// 4. 赋予完全随机的朝向 (Yaw: 0~360度)
				spawnLoc.setYaw(random.nextFloat() * 360f);

				// 5. 随机抽取一种动物
				String animalStr = allowedEntities.get(random.nextInt(allowedEntities.size()));

				try {
					EntityType type = EntityType.valueOf(animalStr.toUpperCase());
					Entity entity = world.spawnEntity(spawnLoc, type);

					// 应用 Hypixel 机制：完全静止、无声
					entity.setSilent(true);
					if (entity instanceof LivingEntity living) {
						living.setAI(false);         // 彻底关闭 AI
						living.setCollidable(true);  // 保持碰撞体积
					}

					// 丰富场景细节：随机幼崽与变种
					applyEntityVariants(entity);

					// 加入房间管理器
					arena.getAiAnimals().add(entity);
					spawnedCount++;

				} catch (IllegalArgumentException e) {
					plugin.getComponentLogger().warn("尝试生成未知的 AI 动物类型: {}", animalStr);
				}
			}
		}

		plugin.getComponentLogger().info("地图 {} 成功生成了 {} 只静态 AI 动物。", arena.getArenaName(), spawnedCount);
	}

	/**
	 * 【高级算法】3D 随机投点 + 局部下落引力算法
	 * 完美解决多层建筑的生成，且性能极高
	 */
	private Location getRandomSafeLocation(Location pos1, Location pos2) {
		World world = pos1.getWorld();

		// 获取 3D 立方体的边界
		double minX = Math.min(pos1.getX(), pos2.getX());
		double maxX = Math.max(pos1.getX(), pos2.getX());
		double minY = Math.min(pos1.getY(), pos2.getY());
		double maxY = Math.max(pos1.getY(), pos2.getY());
		double minZ = Math.min(pos1.getZ(), pos2.getZ());
		double maxZ = Math.max(pos1.getZ(), pos2.getZ());

		// 1. 在整个 3D 空间内随机投一个点
		double x = minX + (maxX - minX) * random.nextDouble();
		double y = minY + (maxY - minY) * random.nextDouble();
		double z = minZ + (maxZ - minZ) * random.nextDouble();

		int blockX = (int) Math.floor(x);
		int startY = (int) Math.floor(y);
		int blockZ = (int) Math.floor(z);

		// 2. 模拟重力：从这个随机点往下探测最多 15 格，寻找落脚点
		for (int checkY = startY; checkY >= startY - 15 && checkY >= world.getMinHeight(); checkY--) {
			Block feet = world.getBlockAt(blockX, checkY, blockZ);
			Block head = world.getBlockAt(blockX, checkY + 1, blockZ);
			Block ground = world.getBlockAt(blockX, checkY - 1, blockZ);

			if (isSafeLocation(feet, head, ground)) {
				// 加 0.5 让坐标居中
				return new Location(world, blockX + 0.5, checkY, blockZ + 0.5);
			}
		}

		// 如果往下 15 格都没找到安全地面（说明点在半空中或者墙壁里），返回 null 丢弃
		return null;
	}

	/**
	 * 在群落中心点附近寻找偏移位置 (同样使用短距离下落算法)
	 */
	private Location getValidLocationNear(Location center, double radius) {
		World world = center.getWorld();

		double offsetX = (random.nextDouble() * 2 - 1) * radius;
		double offsetZ = (random.nextDouble() * 2 - 1) * radius;

		int blockX = (int) Math.floor(center.getX() + offsetX);
		int blockZ = (int) Math.floor(center.getZ() + offsetZ);

		// 群落附近的点，只需要在中心点高度的上下 4 格内寻找即可 (支持缓坡和楼梯)
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

	/**
	 * 判断该位置是否适合生物站立
	 */
	private boolean isSafeLocation(Block feet, Block head, Block ground) {
		// 基础判断：脚和头必须是可穿过的空气/草，脚下必须是实体方块
		if (!feet.isPassable() || !head.isPassable() || !ground.getType().isSolid()) {
			return false;
		}

		Material groundType = ground.getType();
		String typeName = groundType.name();

		// 排除危险方块
		if (groundType == Material.MAGMA_BLOCK || groundType == Material.CACTUS || groundType == Material.CAMPFIRE) {
			return false;
		}

		// 排除不适合站立的屋顶/树木方块 (防止悬在树叶上或屋顶台阶上)
		if (typeName.endsWith("_STAIRS") || typeName.endsWith("_SLAB") ||
				typeName.endsWith("_LEAVES") || typeName.endsWith("GLASS")) {
			return false;
		}

		return true;
	}

	/**
	 * 为生成的动物附加随机外观变种
	 */
	private void applyEntityVariants(Entity entity) {
		// 15% 概率生成幼崽
		if (entity instanceof Ageable ageable) {
			if (random.nextDouble() < 0.15) {
				ageable.setBaby();
			} else {
				ageable.setAdult();
			}
		}

		// 特定动物的处理
		if (entity instanceof Sheep sheep) {
			sheep.setColor(DyeColor.WHITE);
		} else if (entity instanceof Wolf wolf) {
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