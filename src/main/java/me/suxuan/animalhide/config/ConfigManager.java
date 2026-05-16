package me.suxuan.animalhide.config;

import lombok.Getter;
import me.suxuan.animalhide.AnimalHidePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
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
}