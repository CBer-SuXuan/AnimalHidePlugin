package me.suxuan.animalhide.config;

import lombok.Getter;
import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.game.BlockRegion;
import me.suxuan.animalhide.game.SpawnPoint;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

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
			plugin.saveResource("arenas/queue.yml", false);
		} else {
			File queueFile = new File(arenaFolder, "queue.yml");
			if (!queueFile.exists()) {
				plugin.saveResource("arenas/queue.yml", false);
			}
		}

		File[] files = arenaFolder.listFiles((dir, name) -> name.endsWith(".yml"));
		if (files != null) {
			for (File file : files) {
				String arenaName = file.getName().replace(".yml", "");
				arenaConfigs.put(arenaName, YamlConfiguration.loadConfiguration(file));
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
	 * 解析寻找阶段需清空的隔离墙区域（模板坐标，{@code locations.phase-wall} 的 min/max 对角）。
	 */
	public BlockRegion getPhaseWallRegion(FileConfiguration config) {
		ConfigurationSection section = config.getConfigurationSection("locations.phase-wall");
		if (section == null) return null;
		Location min = getDynamicLocation(section.getConfigurationSection("min"));
		Location max = getDynamicLocation(section.getConfigurationSection("max"));
		if (min == null || max == null) return null;
		return BlockRegion.fromCorners(min, max);
	}

	public SpawnPoint getSpawnPoint(ConfigurationSection section) {
		if (section == null) return null;
		Location loc = getDynamicLocation(section);
		if (loc == null) return null;

		List<String> types = section.isList("types") ? section.getStringList("types") : null;
		double weight = section.getDouble("weight", 1.0);
		return new SpawnPoint(loc, types, weight);
	}

	public int getMatchSettlementDurationSeconds() {
		return Math.max(3, Math.min(60, mainConfig.getInt("match.settlement-duration-seconds", 8)));
	}

	public boolean isHiderDisguiseInvisibilityEnabled() {
		return mainConfig.getBoolean("features.hider-disguise-invisibility", true);
	}

	public boolean isQueueEnabled() {
		return mainConfig.getBoolean("queue.enabled", true);
	}

	public String getQueueTemplateName() {
		return mainConfig.getString("queue.template-name", "queue");
	}

	public boolean isNormalLeaveAllowed() {
		return mainConfig.getBoolean("queue.allow-normal-leave", false);
	}

	public double getSeekerPreReleaseMoveSpeed() {
		return Math.max(0.01, mainConfig.getDouble("seeker.pre-release-move-speed", 0.10));
	}

	public double getSeekerReleasedMoveSpeed() {
		return Math.max(0.01, mainConfig.getDouble("seeker.released-move-speed", 0.11));
	}

	public int getPoopTauntCooldownSeconds(String arenaName) {
		return getArenaTauntCooldownSeconds(arenaName, "poop", "safe", 5);
	}

	public int getStinkyTauntCooldownSeconds(String arenaName) {
		return getArenaTauntCooldownSeconds(arenaName, "stinky", "stinky", 15);
	}

	public int getScreamTauntCooldownSeconds(String arenaName) {
		return getArenaTauntCooldownSeconds(arenaName, "scream", "scream", 30);
	}

	public int getPartyTauntCooldownSeconds(String arenaName) {
		return getArenaTauntCooldownSeconds(arenaName, "party", "party", 50);
	}

	private int getArenaTauntCooldownSeconds(String arenaName, String newKey, String legacyKey, int defaultValue) {
		FileConfiguration arenaConfig = arenaName == null ? null : arenaConfigs.get(arenaName);
		if (arenaConfig != null) {
			String newPath = "taunt-cooldowns." + newKey;
			if (arenaConfig.contains(newPath)) {
				return Math.max(1, arenaConfig.getInt(newPath, defaultValue));
			}
			String legacyArenaPath = "taunt-cooldowns." + legacyKey;
			if (arenaConfig.contains(legacyArenaPath)) {
				return Math.max(1, arenaConfig.getInt(legacyArenaPath, defaultValue));
			}
		}
		return Math.max(1, mainConfig.getInt("taunt-cooldowns." + legacyKey, defaultValue));
	}

	public double getPoopTauntHealAmount(String arenaName) {
		return getArenaTauntHealAmount(arenaName, "poop", 0.0);
	}

	public double getStinkyTauntHealAmount(String arenaName) {
		return getArenaTauntHealAmount(arenaName, "stinky", 0.0);
	}

	public double getScreamTauntHealAmount(String arenaName) {
		return getArenaTauntHealAmount(arenaName, "scream", 0.0);
	}

	public double getPartyTauntHealAmount(String arenaName) {
		return getArenaTauntHealAmount(arenaName, "party", 0.0);
	}

	private double getArenaTauntHealAmount(String arenaName, String key, double defaultValue) {
		FileConfiguration arenaConfig = arenaName == null ? null : arenaConfigs.get(arenaName);
		if (arenaConfig != null) {
			String path = "taunt-heal." + key;
			if (arenaConfig.contains(path)) {
				return Math.max(0.0, arenaConfig.getDouble(path, defaultValue));
			}
		}
		return defaultValue;
	}

	public boolean isQueueTutorialEnabled() {
		return mainConfig.getBoolean("queue.tutorial.enabled", true);
	}

	public String getTutorialDemoText(String demoId, String key, String fallback) {
		return mainConfig.getString("tutorial-content.demos." + demoId + "." + key, fallback);
	}

	public List<String> getQueueTutorialHintTemplateLines(String key) {
		return mainConfig.getStringList("tutorial-content.hints." + key + ".lines");
	}

	public String getQueueTutorialHintTitle(String key) {
		return mainConfig.getString("tutorial-content.hints." + key + ".title", "");
	}

	public Display.Billboard getQueueTutorialHintBillboard() {
		String raw = mainConfig.getString("tutorial-content.hints-common.billboard", "CENTER");
		try {
			return Display.Billboard.valueOf(raw.toUpperCase());
		} catch (IllegalArgumentException ex) {
			return Display.Billboard.CENTER;
		}
	}

	public TextDisplay.TextAlignment getQueueTutorialHintAlignment() {
		String raw = mainConfig.getString("tutorial-content.hints-common.alignment", "CENTER");
		try {
			return TextDisplay.TextAlignment.valueOf(raw.toUpperCase());
		} catch (IllegalArgumentException ex) {
			return TextDisplay.TextAlignment.CENTER;
		}
	}

	public int getQueueTutorialHintLineWidth() {
		return Math.max(80, mainConfig.getInt("tutorial-content.hints-common.line-width", 240));
	}

	public float getQueueTutorialHintViewRange() {
		return (float) Math.max(0.1, mainConfig.getDouble("tutorial-content.hints-common.view-range", 0.75));
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