package me.suxuan.animalhide.game;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * 模板坐标系下的轴对齐长方体区域（方块整数坐标，两端均包含）。
 */
public record BlockRegion(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

	public static BlockRegion fromCorners(Location a, Location b) {
		int x1 = (int) Math.floor(Math.min(a.getX(), b.getX()));
		int y1 = (int) Math.floor(Math.min(a.getY(), b.getY()));
		int z1 = (int) Math.floor(Math.min(a.getZ(), b.getZ()));
		int x2 = (int) Math.floor(Math.max(a.getX(), b.getX()));
		int y2 = (int) Math.floor(Math.max(a.getY(), b.getY()));
		int z2 = (int) Math.floor(Math.max(a.getZ(), b.getZ()));
		return new BlockRegion(x1, y1, z1, x2, y2, z2);
	}

	public boolean isEmpty() {
		return minX > maxX || minY > maxY || minZ > maxZ;
	}

	/**
	 * 将区域内所有非空气方块设为空气。
	 */
	public int fillAir(World world) {
		if (world == null || isEmpty()) return 0;
		int changed = 0;
		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					if (world.getBlockAt(x, y, z).getType() != Material.AIR) {
						world.getBlockAt(x, y, z).setType(Material.AIR);
						changed++;
					}
				}
			}
		}
		return changed;
	}
}
