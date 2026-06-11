package me.suxuan.animalhide.manager;

import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.game.PlayerRole;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class MatchStatsFileManager {

	private final AnimalHidePlugin plugin;
	private final File statsFolder;
	private final Object fileLock = new Object();

	public MatchStatsFileManager(AnimalHidePlugin plugin) {
		this.plugin = plugin;
		this.statsFolder = new File(plugin.getDataFolder(), "stats");
		ensureStatsStorage();
		cleanupLegacyFiles();
	}

	public void recordMatchResult(String mapName, PlayerRole winner, List<Map<String, Object>> topPlayers) {
		if (winner == null || winner == PlayerRole.SPECTATOR) {
			return;
		}
			synchronized (fileLock) {
				File statsFile = resolveDailyStatsFile();
				FileConfiguration config = YamlConfiguration.loadConfiguration(statsFile);
				String winnerRoot = "winner-counts";
				String winnerKey = winner.name();
				config.set(winnerRoot + "." + winnerKey, config.getInt(winnerRoot + "." + winnerKey, 0) + 1);

				String matchesRoot = "matches";
				int nextIndex = nextMatchIndex(config, matchesRoot);
				String matchRoot = matchesRoot + ".match-" + nextIndex;
				config.set(matchRoot + ".map", mapName);
				config.set(matchRoot + ".winner", winner.name());

				for (int i = 0; i < Math.min(3, topPlayers.size()); i++) {
					Map<String, Object> topPlayer = topPlayers.get(i);
					String topRoot = matchRoot + ".top-" + (i + 1);
					config.set(topRoot + ".player", topPlayer.getOrDefault("player", "unknown"));
					config.set(topRoot + ".score", topPlayer.getOrDefault("score", 0));
				}

				save(statsFile, config);
			}

	}

	private int nextMatchIndex(FileConfiguration config, String matchesRoot) {
		if (!config.isConfigurationSection(matchesRoot)) {
			return 1;
		}
		return config.getConfigurationSection(matchesRoot).getKeys(false).size() + 1;
	}

	private File resolveDailyStatsFile() {
		return new File(statsFolder, LocalDate.now() + ".yml");
	}

	private void ensureStatsStorage() {
		if (!statsFolder.exists()) {
			statsFolder.mkdirs();
		}
	}

	private void cleanupLegacyFiles() {
		deleteIfExists(new File(plugin.getDataFolder(), "match-analytics-summary.json"));
		File[] legacyFiles = plugin.getDataFolder().listFiles((dir, name) ->
				name.startsWith("match-analytics-") && (name.endsWith(".json") || name.endsWith(".jsonl")));
		if (legacyFiles != null) {
			for (File legacyFile : legacyFiles) {
				deleteIfExists(legacyFile);
			}
		}
	}

	private void deleteIfExists(File file) {
		if (file.exists() && !file.delete()) {
			plugin.getComponentLogger().warn("无法删除旧统计文件: {}", file.getName());
		}
	}

	private void save(File targetFile, FileConfiguration config) {
		try {
			config.save(targetFile);
		} catch (IOException e) {
			plugin.getComponentLogger().error("保存对局统计 YML 失败: {}", e.getMessage());
		}
	}
}
