package me.suxuan.animalhide.config;

import lombok.Getter;
import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.game.SpawnPoint;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {

	private final AnimalHidePlugin plugin;

	@Getter
	private FileConfiguration mainConfig;
	@Getter
	private final Map<String, FileConfiguration> arenaConfigs = new HashMap<>();

	public ConfigManager(AnimalHidePlugin plugin) {
		this.plugin = plugin;
		loadConfigs();
	}

	public void loadConfigs() {
		arenaConfigs.clear();
		plugin.saveDefaultConfig();
		plugin.reloadConfig();
		this.mainConfig = plugin.getConfig();

		File arenaFolder = new File(plugin.getDataFolder(), "arenas");
		if (!arenaFolder.exists()) {
			arenaFolder.mkdirs();
			plugin.saveResource("arenas/example.yml", false);
		}

		File[] files = arenaFolder.listFiles((dir, name) -> name.endsWith(".yml"));
		if (files != null) {
			for (File file : files) {
				String arenaName = file.getName().replace(".yml", "");
				arenaConfigs.put(arenaName, YamlConfiguration.loadConfiguration(file));
				plugin.getComponentLogger().info("已读取地图配置: {}", arenaName);
			}
		}
	}

	/**
	 * 读取带有指定世界的真实坐标 (用于主城 Lobby)
	 */
	public Location getLocation(ConfigurationSection section) {
		if (section == null) return null;
		World world = Bukkit.getWorld(section.getString("world", "world"));
		return new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
				(float) section.getDouble("yaw", 0.0), (float) section.getDouble("pitch", 0.0));
	}

	/**
	 * 读取没有世界的动态模板坐标 (用于游戏房间内部，World将由系统后续动态绑定)
	 */
	public Location getDynamicLocation(ConfigurationSection section) {
		if (section == null) return null;
		return new Location(null, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
				(float) section.getDouble("yaw", 0.0), (float) section.getDouble("pitch", 0.0));
	}

	/**
	 * 读取一个 AI 生成点（动态模板坐标 + 可选的种类白名单 + 可选权重）。
	 * <p>
	 * 配置示例：
	 * <pre>
	 * pigpen:
	 *   x: 10
	 *   y: -60
	 *   z: 10
	 *   types: [PIG]      # 可选；不填则继承全局 allowed-animals
	 *   weight: 3.0       # 可选；默认 1.0
	 * </pre>
	 */
	public SpawnPoint getSpawnPoint(ConfigurationSection section) {
		if (section == null) return null;
		Location loc = getDynamicLocation(section);
		if (loc == null) return null;

		List<String> types = section.isList("types") ? section.getStringList("types") : null;
		double weight = section.getDouble("weight", 1.0);
		return new SpawnPoint(loc, types, weight);
	}

	/**
	 * 获取某个地图的 yml 文件句柄。
	 *
	 * @return 文件对象，若不存在返回 null
	 */
	public File getArenaFile(String arenaName) {
		File file = new File(plugin.getDataFolder(), "arenas/" + arenaName + ".yml");
		return file.exists() ? file : null;
	}

	/**
	 * 把一个 AI 生成点写入指定地图 yml，并刷新内存缓存。
	 *
	 * @param arenaName 地图名（yml 文件名去掉 .yml）
	 * @param pointName yml 里的节点 key
	 * @param point     要写入的点位
	 * @return true 表示写入并保存成功
	 */
	public boolean saveSpawnPoint(String arenaName, String pointName, SpawnPoint point) {
		File file = getArenaFile(arenaName);
		if (file == null) return false;

		FileConfiguration config = YamlConfiguration.loadConfiguration(file);
		String path = "locations.ai-spawns." + pointName;

		Location loc = point.getLocation();
		config.set(path + ".x", loc.getX());
		config.set(path + ".y", loc.getY());
		config.set(path + ".z", loc.getZ());

		if (point.hasTypes()) {
			config.set(path + ".types", point.getTypes());
		} else {
			config.set(path + ".types", null);
		}

		if (point.getWeight() != 1.0) {
			config.set(path + ".weight", point.getWeight());
		} else {
			config.set(path + ".weight", null);
		}

		try {
			config.save(file);
			arenaConfigs.put(arenaName, config);
			return true;
		} catch (IOException e) {
			plugin.getComponentLogger().error("写入竞技场配置失败: {}", arenaName, e);
			return false;
		}
	}

	/**
	 * 从指定地图 yml 中删除一个 AI 生成点。
	 *
	 * @return true 表示存在并删除成功；false 表示文件不存在或该点位不存在
	 */
	public boolean removeSpawnPoint(String arenaName, String pointName) {
		File file = getArenaFile(arenaName);
		if (file == null) return false;

		FileConfiguration config = YamlConfiguration.loadConfiguration(file);
		String path = "locations.ai-spawns." + pointName;
		if (!config.contains(path)) return false;

		config.set(path, null);
		try {
			config.save(file);
			arenaConfigs.put(arenaName, config);
			return true;
		} catch (IOException e) {
			plugin.getComponentLogger().error("写入竞技场配置失败: {}", arenaName, e);
			return false;
		}
	}

	/**
	 * 把单个积分项写入指定地图 yml 的 {@code scoring} 节，并刷新内存缓存。
	 *
	 * @param arenaName 地图名
	 * @param key       配置项 key（应使用 {@code ScoringConfig.KEY_xxx}）
	 * @param value     新的积分值
	 * @return true 表示写入成功
	 */
	public boolean saveScoring(String arenaName, String key, int value) {
		File file = getArenaFile(arenaName);
		if (file == null) return false;

		FileConfiguration config = YamlConfiguration.loadConfiguration(file);
		config.set("scoring." + key, value);
		try {
			config.save(file);
			arenaConfigs.put(arenaName, config);
			return true;
		} catch (IOException e) {
			plugin.getComponentLogger().error("写入竞技场积分配置失败: {}", arenaName, e);
			return false;
		}
	}

	/**
	 * 删除某地图的整个 {@code scoring} 节，下次读取时会全部回退到默认值。
	 *
	 * @return true 表示存在并删除成功；false 表示文件不存在或写入失败
	 */
	public boolean resetScoring(String arenaName) {
		File file = getArenaFile(arenaName);
		if (file == null) return false;

		FileConfiguration config = YamlConfiguration.loadConfiguration(file);
		config.set("scoring", null);
		try {
			config.save(file);
			arenaConfigs.put(arenaName, config);
			return true;
		} catch (IOException e) {
			plugin.getComponentLogger().error("重置竞技场积分配置失败: {}", arenaName, e);
			return false;
		}
	}
}