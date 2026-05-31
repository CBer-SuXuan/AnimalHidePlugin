package me.suxuan.animalhide.manager;

import me.suxuan.animalhide.AnimalHidePlugin;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MatchAnalyticsManager {

	private final AnimalHidePlugin plugin;
	private final File dataFolder;
	private final File summaryFile;
	private final Object fileLock = new Object();

	public MatchAnalyticsManager(AnimalHidePlugin plugin) {
		this.plugin = plugin;
		this.dataFolder = plugin.getDataFolder();
		if (!dataFolder.exists()) {
			dataFolder.mkdirs();
		}
		this.summaryFile = new File(dataFolder, "match-analytics-summary.json");
	}

	public void appendRecordAsync(String jsonLine, Map<String, Object> summaryPayload) {
		if (jsonLine == null || jsonLine.isBlank()) {
			return;
		}
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			synchronized (fileLock) {
				try {
					Files.writeString(
							resolveDailyAnalyticsFile().toPath(),
							jsonLine + System.lineSeparator(),
							StandardCharsets.UTF_8,
							StandardOpenOption.CREATE,
							StandardOpenOption.WRITE,
							StandardOpenOption.APPEND
					);
					updateSummary(summaryPayload);
				} catch (IOException e) {
					plugin.getComponentLogger().error("写入对局统计文件失败: {}", e.getMessage());
				}
			}
		});
	}

	private File resolveDailyAnalyticsFile() {
		String date = LocalDate.now().toString();
		return new File(dataFolder, "match-analytics-" + date + ".jsonl");
	}

	private void updateSummary(Map<String, Object> payload) throws IOException {
		Map<String, Object> summary = createDefaultSummary();
		Map<String, Object> global = getMap(summary, "global");
		Map<String, Object> skills = getMap(summary, "skillTotals");
		Map<String, Object> maps = getMap(summary, "maps");
		Map<String, Object> players = getMap(summary, "players");

		String mapName = stringValue(payload.get("mapName"), "unknown");
		Map<String, Object> mapSummary = (Map<String, Object>) maps.computeIfAbsent(mapName, key -> createDefaultMapSummary());

		boolean adminEnded = booleanValue(payload.get("endedByAdmin"));
		String winner = stringValue(payload.get("winner"), "UNKNOWN");
		long durationSeconds = longValue(payload.get("durationSeconds"));
		int quitPlayerCount = intValue(payload.get("quitPlayerCount"));
		boolean finalRevealTriggered = booleanValue(payload.get("finalRevealTriggered"));
		int survivingHiders = intValue(payload.get("survivingHiders"));

		increment(global, "totalMatches", 1);
		increment(mapSummary, "matches", 1);
		add(global, "totalDurationSeconds", durationSeconds);
		add(mapSummary, "totalDurationSeconds", durationSeconds);
		add(global, "totalQuitPlayers", quitPlayerCount);
		add(mapSummary, "totalQuitPlayers", quitPlayerCount);
		if (finalRevealTriggered) {
			increment(global, "finalRevealMatches", 1);
			increment(mapSummary, "finalRevealMatches", 1);
		}

		if (adminEnded) {
			increment(global, "adminEndedMatches", 1);
		} else {
			increment(global, "completedMatches", 1);
			increment(mapSummary, "completedMatches", 1);
			if ("SEEKER".equalsIgnoreCase(winner)) {
				increment(global, "seekerWins", 1);
				increment(mapSummary, "seekerWins", 1);
			} else if ("HIDER".equalsIgnoreCase(winner)) {
				increment(global, "hiderWins", 1);
				increment(mapSummary, "hiderWins", 1);
				increment(global, "hiderWinMatches", 1);
				increment(mapSummary, "hiderWinMatches", 1);
				add(global, "totalSurvivingHidersWhenHiderWins", survivingHiders);
				add(mapSummary, "totalSurvivingHidersWhenHiderWins", survivingHiders);
			}
		}

		Map<String, Object> skillCounters = getMap(payload, "skillCounters");
		for (Map.Entry<String, Object> entry : skillCounters.entrySet()) {
			add(skills, entry.getKey(), longValue(entry.getValue()));
		}

		List<Map<String, Object>> playerPayloads = getListOfMaps(payload.get("players"));
		for (Map<String, Object> playerPayload : playerPayloads) {
			String uuid = stringValue(playerPayload.get("uuid"), "unknown");
			Map<String, Object> playerSummary = (Map<String, Object>) players.computeIfAbsent(uuid, key -> createDefaultPlayerSummary(uuid));
			String name = stringValue(playerPayload.get("name"), "unknown");
			String finalRole = stringValue(playerPayload.get("finalRole"), "HIDER");
			boolean won = booleanValue(playerPayload.get("won"));
			boolean quitMidGame = booleanValue(playerPayload.get("quitMidGame"));
			long matchScore = longValue(playerPayload.get("matchScore"));
			long kills = longValue(playerPayload.get("kills"));

			playerSummary.put("name", name);
			increment(playerSummary, "matches", 1);
			if (adminEnded) {
				increment(playerSummary, "adminEndedMatches", 1);
			} else {
				increment(playerSummary, "completedMatches", 1);
				if (won) {
					increment(playerSummary, "wins", 1);
				}
			}
			add(playerSummary, "totalScore", matchScore);
			add(playerSummary, "totalKills", kills);
			if (quitMidGame) {
				increment(playerSummary, "quitMidGameCount", 1);
			}

			if ("SEEKER".equalsIgnoreCase(finalRole)) {
				increment(playerSummary, "seekerMatches", 1);
				if (!adminEnded && won) increment(playerSummary, "seekerWins", 1);
			} else if ("HIDER".equalsIgnoreCase(finalRole)) {
				increment(playerSummary, "hiderMatches", 1);
				if (!adminEnded && won) increment(playerSummary, "hiderWins", 1);
			} else if ("SPECTATOR".equalsIgnoreCase(finalRole)) {
				increment(playerSummary, "spectatorMatches", 1);
			}

			recalculatePlayerDerived(playerSummary);
		}

		recalculateDerived(global);
		for (Object value : maps.values()) {
			if (value instanceof Map<?, ?> map) {
				recalculateDerived((Map<String, Object>) map);
			}
		}

		Files.writeString(
				summaryFile.toPath(),
				toJson(summary),
				StandardCharsets.UTF_8,
				StandardOpenOption.CREATE,
				StandardOpenOption.WRITE,
				StandardOpenOption.TRUNCATE_EXISTING
		);
	}

	private Map<String, Object> createDefaultSummary() {
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("global", createDefaultGlobalSummary());
		root.put("skillTotals", createDefaultSkillTotals());
		root.put("maps", new LinkedHashMap<String, Object>());
		root.put("players", new LinkedHashMap<String, Object>());
		return root;
	}

	private Map<String, Object> createDefaultGlobalSummary() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("totalMatches", 0L);
		map.put("completedMatches", 0L);
		map.put("adminEndedMatches", 0L);
		map.put("seekerWins", 0L);
		map.put("hiderWins", 0L);
		map.put("hiderWinMatches", 0L);
		map.put("totalDurationSeconds", 0L);
		map.put("totalQuitPlayers", 0L);
		map.put("totalSurvivingHidersWhenHiderWins", 0L);
		map.put("finalRevealMatches", 0L);
		map.put("seekerWinRate", 0.0);
		map.put("hiderWinRate", 0.0);
		map.put("averageDurationSeconds", 0.0);
		map.put("averageQuitPlayers", 0.0);
		map.put("averageSurvivingHidersWhenHiderWins", 0.0);
		map.put("finalRevealTriggerRate", 0.0);
		return map;
	}

	private Map<String, Object> createDefaultMapSummary() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("matches", 0L);
		map.put("completedMatches", 0L);
		map.put("seekerWins", 0L);
		map.put("hiderWins", 0L);
		map.put("hiderWinMatches", 0L);
		map.put("totalDurationSeconds", 0L);
		map.put("totalQuitPlayers", 0L);
		map.put("totalSurvivingHidersWhenHiderWins", 0L);
		map.put("finalRevealMatches", 0L);
		map.put("seekerWinRate", 0.0);
		map.put("hiderWinRate", 0.0);
		map.put("averageDurationSeconds", 0.0);
		map.put("averageQuitPlayers", 0.0);
		map.put("averageSurvivingHidersWhenHiderWins", 0.0);
		map.put("finalRevealTriggerRate", 0.0);
		return map;
	}

	private Map<String, Object> createDefaultSkillTotals() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("poopTauntUses", 0L);
		map.put("stinkyTauntUses", 0L);
		map.put("screamTauntUses", 0L);
		map.put("partyTauntUses", 0L);
		map.put("explosiveSheepUses", 0L);
		return map;
	}

	private Map<String, Object> createDefaultPlayerSummary(String uuid) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("uuid", uuid);
		map.put("name", "unknown");
		map.put("matches", 0L);
		map.put("completedMatches", 0L);
		map.put("adminEndedMatches", 0L);
		map.put("wins", 0L);
		map.put("totalScore", 0L);
		map.put("totalKills", 0L);
		map.put("quitMidGameCount", 0L);
		map.put("seekerMatches", 0L);
		map.put("seekerWins", 0L);
		map.put("hiderMatches", 0L);
		map.put("hiderWins", 0L);
		map.put("spectatorMatches", 0L);
		map.put("winRate", 0.0);
		map.put("averageScore", 0.0);
		map.put("averageKills", 0.0);
		map.put("seekerWinRate", 0.0);
		map.put("hiderWinRate", 0.0);
		return map;
	}

	private void recalculateDerived(Map<String, Object> summary) {
		long completedMatches = longValue(summary.getOrDefault("completedMatches", 0L));
		long seekerWins = longValue(summary.getOrDefault("seekerWins", 0L));
		long hiderWins = longValue(summary.getOrDefault("hiderWins", 0L));
		long totalDurationSeconds = longValue(summary.getOrDefault("totalDurationSeconds", 0L));
		long totalQuitPlayers = longValue(summary.getOrDefault("totalQuitPlayers", 0L));
		long hiderWinMatches = longValue(summary.getOrDefault("hiderWinMatches", 0L));
		long totalSurvivingHiders = longValue(summary.getOrDefault("totalSurvivingHidersWhenHiderWins", 0L));
		long totalMatches = longValue(summary.getOrDefault("totalMatches", summary.getOrDefault("matches", 0L)));
		long finalRevealMatches = longValue(summary.getOrDefault("finalRevealMatches", 0L));

		summary.put("seekerWinRate", rate(seekerWins, completedMatches));
		summary.put("hiderWinRate", rate(hiderWins, completedMatches));
		summary.put("averageDurationSeconds", average(totalDurationSeconds, totalMatches));
		summary.put("averageQuitPlayers", average(totalQuitPlayers, totalMatches));
		summary.put("averageSurvivingHidersWhenHiderWins", average(totalSurvivingHiders, hiderWinMatches));
		summary.put("finalRevealTriggerRate", rate(finalRevealMatches, totalMatches));
	}

	private void recalculatePlayerDerived(Map<String, Object> playerSummary) {
		long matches = longValue(playerSummary.getOrDefault("matches", 0L));
		long completedMatches = longValue(playerSummary.getOrDefault("completedMatches", 0L));
		long wins = longValue(playerSummary.getOrDefault("wins", 0L));
		long totalScore = longValue(playerSummary.getOrDefault("totalScore", 0L));
		long totalKills = longValue(playerSummary.getOrDefault("totalKills", 0L));
		long seekerMatches = longValue(playerSummary.getOrDefault("seekerMatches", 0L));
		long seekerWins = longValue(playerSummary.getOrDefault("seekerWins", 0L));
		long hiderMatches = longValue(playerSummary.getOrDefault("hiderMatches", 0L));
		long hiderWins = longValue(playerSummary.getOrDefault("hiderWins", 0L));

		playerSummary.put("winRate", rate(wins, completedMatches));
		playerSummary.put("averageScore", average(totalScore, matches));
		playerSummary.put("averageKills", average(totalKills, matches));
		playerSummary.put("seekerWinRate", rate(seekerWins, seekerMatches));
		playerSummary.put("hiderWinRate", rate(hiderWins, hiderMatches));
	}

	private double rate(long numerator, long denominator) {
		if (denominator <= 0L) return 0.0;
		return round((double) numerator / (double) denominator);
	}

	private double average(long total, long count) {
		if (count <= 0L) return 0.0;
		return round((double) total / (double) count);
	}

	private double round(double value) {
		return Math.round(value * 10000.0) / 10000.0;
	}

	private void increment(Map<String, Object> map, String key, long delta) {
		add(map, key, delta);
	}

	private void add(Map<String, Object> map, String key, long delta) {
		map.put(key, longValue(map.getOrDefault(key, 0L)) + delta);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> getMap(Map<String, Object> parent, String key) {
		Object value = parent.get(key);
		if (value instanceof Map<?, ?> map) {
			return (Map<String, Object>) map;
		}
		Map<String, Object> created = new LinkedHashMap<>();
		parent.put(key, created);
		return created;
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> getListOfMaps(Object value) {
		if (value instanceof List<?> list) {
			return (List<Map<String, Object>>) list;
		}
		return java.util.Collections.emptyList();
	}

	private String stringValue(Object value, String fallback) {
		return value == null ? fallback : String.valueOf(value);
	}

	private boolean booleanValue(Object value) {
		return value instanceof Boolean b && b;
	}

	private int intValue(Object value) {
		return (int) longValue(value);
	}

	private long longValue(Object value) {
		if (value instanceof Number number) {
			return number.longValue();
		}
		if (value instanceof String s) {
			try {
				return Long.parseLong(s);
			} catch (NumberFormatException ignored) {
				return 0L;
			}
		}
		return 0L;
	}

	private String toJson(Object value) {
		if (value == null) return "null";
		if (value instanceof String s) return '"' + escapeJson(s) + '"';
		if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
		if (value instanceof Map<?, ?> map) {
			StringBuilder sb = new StringBuilder("{");
			boolean first = true;
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				if (!first) sb.append(',');
				first = false;
				sb.append(toJson(String.valueOf(entry.getKey()))).append(':').append(toJson(entry.getValue()));
			}
			sb.append('}');
			return sb.toString();
		}
		if (value instanceof Iterable<?> iterable) {
			StringBuilder sb = new StringBuilder("[");
			boolean first = true;
			for (Object item : iterable) {
				if (!first) sb.append(',');
				first = false;
				sb.append(toJson(item));
			}
			sb.append(']');
			return sb.toString();
		}
		return toJson(String.valueOf(value));
	}

	private String escapeJson(String value) {
		return value
				.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\n", "\\n")
				.replace("\r", "\\r")
				.replace("\t", "\\t");
	}
}
