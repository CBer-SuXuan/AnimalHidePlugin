package me.suxuan.animalhide.config;

import lombok.Getter;
import me.suxuan.animalhide.AnimalHidePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
	// 存储所有加载好的地图配置，Key 是文件名
	@Getter
	private final Map<String, FileConfiguration> arenaConfigs = new HashMap<>();

	public ConfigManager(AnimalHidePlugin plugin) {
		this.plugin = plugin;
		loadConfigs();
	}

	public void loadConfigs() {
		arenaConfigs.clear();
		
		// 加载主配置
		plugin.saveDefaultConfig();
		plugin.reloadConfig();
		this.mainConfig = plugin.getConfig();

		// 加载 arenas 文件夹下的所有地图
		File arenaFolder = new File(plugin.getDataFolder(), "arenas");
		if (!arenaFolder.exists()) {
			arenaFolder.mkdirs();
			// 如果文件夹为空，加载示例配置
			plugin.saveResource("arenas/example.yml", false);
		}

		File[] files = arenaFolder.listFiles((dir, name) -> name.endsWith(".yml"));
		if (files != null) {
			for (File file : files) {
				String arenaName = file.getName().replace(".yml", "");
				arenaConfigs.put(arenaName, YamlConfiguration.loadConfiguration(file));
				plugin.getComponentLogger().info("已加载地图配置: {}", arenaName);
			}
		}
	}

	// 读取 Location 对象
	public Location getLocation(ConfigurationSection section) {
		if (section == null) return null;
		return new Location(
				Bukkit.getWorld(section.getString("world", "world")),
				section.getDouble("x"),
				section.getDouble("y"),
				section.getDouble("z"),
				(float) section.getDouble("yaw", 0.0),
				(float) section.getDouble("pitch", 0.0)
		);
	}

}