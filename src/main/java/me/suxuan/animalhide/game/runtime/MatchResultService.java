package me.suxuan.animalhide.game.runtime;

import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.config.ConfigManager;
import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.GameState;
import me.suxuan.animalhide.game.PlayerRole;
import me.suxuan.animalhide.game.ScoringConfig;
import me.suxuan.animalhide.manager.DatabaseManager;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

@SuppressWarnings("unchecked")
public class MatchResultService {

	private final AnimalHidePlugin plugin;
	private final ConfigManager configManager;
	private final PlayerStateService playerStateService;

	public MatchResultService(AnimalHidePlugin plugin, ConfigManager configManager, PlayerStateService playerStateService) {
		this.plugin = plugin;
		this.configManager = configManager;
		this.playerStateService = playerStateService;
	}

	public void endGame(Arena arena, PlayerRole winner, Consumer<Arena> afterSettlement) {
		if (arena.isMatchSettlement()) {
			return;
		}

		boolean finalRevealTriggered = arena.isFinalRevealActive();
		arena.openPhaseDoors();
		arena.setFinalRevealActive(false);
		arena.setState(GameState.ENDING);
		arena.markMatchEnd();

		boolean adminShutdown = winner == PlayerRole.SPECTATOR;
		Set<UUID> winners = Collections.emptySet();
		if (!adminShutdown) {
			winners = (winner == PlayerRole.SEEKER) ? arena.getSeekers() : arena.getHiders();
			applyWinScores(arena, winner);
		}

		broadcastResultHeader(arena, winner, adminShutdown);

		if (!adminShutdown) {
			broadcastTopScores(arena);
			persistStats(arena, winners);
			persistMatchAnalytics(arena, winner, winners, finalRevealTriggered, false);
			executeSettlementRewardCommands(arena, winner);
			beginSettlementPhase(arena, winner, winners, adminShutdown, afterSettlement);
		} else {
			persistMatchAnalytics(arena, winner, winners, finalRevealTriggered, true);
			arena.broadcast(Component.text("      本局为管理员结束，不结算积分与胜场", NamedTextColor.GRAY));
			arena.broadcast(Component.text("=========================", NamedTextColor.YELLOW));
			finishSettlement(arena, adminShutdown, afterSettlement);
		}
	}

	private void beginSettlementPhase(Arena arena, PlayerRole winner, Set<UUID> winners, boolean adminShutdown, Consumer<Arena> afterSettlement) {
		int seconds = configManager.getMatchSettlementDurationSeconds();
		arena.setMatchSettlement(true);
		arena.setSettlementSecondsLeft(seconds);

		arena.broadcast(Component.text(""));
		arena.broadcast(Component.text("      庆祝结算中，" + seconds + " 秒后返回大厅...", NamedTextColor.AQUA));
		arena.broadcast(Component.text("=========================", NamedTextColor.YELLOW));

		updateSettlementBossBar(arena, seconds);

		arena.setSettlementTask(new BukkitRunnable() {
			int remaining = seconds;

			@Override
			public void run() {
				if (!arena.isMatchSettlement()) {
					cancel();
					return;
				}

				if (remaining <= 0) {
					finishSettlement(arena, adminShutdown, afterSettlement);
					cancel();
					return;
				}

				arena.setSettlementSecondsLeft(remaining);
				updateSettlementBossBar(arena, remaining);
				spawnWinnerFireworks(arena, winners, winner);
				remaining--;
			}
		}.runTaskTimer(plugin, 0L, 20L));
	}

	private void updateSettlementBossBar(Arena arena, int secondsLeft) {
		BossBar bar = arena.getTimeBar();
		if (bar == null) {
			return;
		}
		int duration = configManager.getMatchSettlementDurationSeconds();
		float progress = duration <= 0 ? 0f : (float) secondsLeft / duration;
		bar.progress(Math.max(0f, Math.min(1f, progress)));
		bar.color(BossBar.Color.GREEN);
		bar.name(Component.text("庆祝结算中 · " + secondsLeft + " 秒后离开", NamedTextColor.GREEN));
	}

	private void spawnWinnerFireworks(Arena arena, Set<UUID> winners, PlayerRole winnerRole) {
		Color primary = winnerRole == PlayerRole.SEEKER ? Color.RED : Color.LIME;
		Color secondary = winnerRole == PlayerRole.SEEKER ? Color.ORANGE : Color.AQUA;
		ThreadLocalRandom random = ThreadLocalRandom.current();

		for (UUID uuid : winners) {
			Player player = Bukkit.getPlayer(uuid);
			if (player == null || !player.isOnline()) {
				continue;
			}
			if (!arena.getPlayers().contains(uuid)) {
				continue;
			}

			Location loc = player.getLocation().add(0, 1.2, 0);
			if (loc.getWorld() == null) {
				continue;
			}

			Firework firework = (Firework) loc.getWorld().spawnEntity(loc, EntityType.FIREWORK_ROCKET);
			FireworkMeta meta = firework.getFireworkMeta();
			FireworkEffect.Type type = FireworkEffect.Type.values()[random.nextInt(FireworkEffect.Type.values().length)];
			meta.addEffect(FireworkEffect.builder()
					.withColor(primary, secondary)
					.withFade(Color.WHITE)
					.with(type)
					.flicker(random.nextBoolean())
					.trail(random.nextBoolean())
					.build());
			meta.setPower(0);
			firework.setFireworkMeta(meta);
			firework.detonate();
		}
	}

	private void finishSettlement(Arena arena, boolean adminShutdown, Consumer<Arena> afterSettlement) {
		arena.clearMatchSettlement();
		resetPlayersAfterResult(arena, adminShutdown);
		afterSettlement.accept(arena);
	}

	private void applyWinScores(Arena arena, PlayerRole winner) {
		ScoringConfig scoring = arena.getTemplate().getScoring();
		if (winner == PlayerRole.SEEKER) {
			for (UUID u : arena.getSeekers()) {
				if (arena.getOriginalSeekers().contains(u)) {
					arena.addMatchScore(u, scoring.getSeekerWinOriginal());
				} else {
					arena.addMatchScore(u, scoring.getSeekerWinInfected());
				}
			}
		} else {
			for (UUID u : arena.getHiders()) {
				arena.addMatchScore(u, scoring.getHiderWin());
			}
		}
	}

	private void broadcastResultHeader(Arena arena, PlayerRole winner, boolean adminShutdown) {
		arena.broadcast(Component.text("=========================", NamedTextColor.YELLOW));
		if (adminShutdown) {
			arena.broadcast(Component.text("      游戏已由管理员结束", NamedTextColor.GRAY));
		} else {
			String winnerMsg = winner == PlayerRole.SEEKER ? "§c寻找者" : "§a躲藏者";
			arena.broadcast(Component.text("      游戏结束！ " + winnerMsg + " 获得了胜利！", NamedTextColor.GOLD));
		}
		arena.broadcast(Component.text(""));
	}

	private void broadcastTopScores(Arena arena) {
		List<Map.Entry<UUID, Integer>> sortedScores = new ArrayList<>(arena.getMatchScores().entrySet());
		sortedScores.sort((a, b) -> b.getValue().compareTo(a.getValue()));

		arena.broadcast(Component.text("      【本局积分排行】", NamedTextColor.AQUA));
		for (int i = 0; i < Math.min(3, sortedScores.size()); i++) {
			UUID u = sortedScores.get(i).getKey();
			int score = sortedScores.get(i).getValue();
			Player p = Bukkit.getPlayer(u);
			String name = p != null ? p.getName() : "离线玩家";

			String rankColor = i == 0 ? "§6① " : (i == 1 ? "§e② " : "§7③ ");
			arena.broadcast(Component.text("      " + rankColor + name + " §f- §a" + score + " 分"));
		}
		arena.broadcast(Component.text("=========================", NamedTextColor.YELLOW));
	}

	private void executeSettlementRewardCommands(Arena arena, PlayerRole winner) {
		if (!configManager.isMatchRewardsEnabled()) {
			return;
		}

		List<UUID> rankedPlayers = getRewardEligiblePlayers(arena);
		for (int i = 0; i < rankedPlayers.size(); i++) {
			UUID uuid = rankedPlayers.get(i);
			Player player = Bukkit.getPlayer(uuid);
			if (player == null || !player.isOnline()) continue;

			int rank = i + 1;
			String rewardKey = rank <= 3 ? "top-" + rank : "participation";
			runRewardCommands(arena, player, winner, rank <= 3 ? rank : 0, rewardKey);
		}
	}

	private List<UUID> getRewardEligiblePlayers(Arena arena) {
		List<UUID> players = new ArrayList<>(arena.getPlayers());
		players.removeIf(uuid -> arena.getQuitPlayers().contains(uuid) || Bukkit.getPlayer(uuid) == null || !Bukkit.getPlayer(uuid).isOnline());
		players.sort((a, b) -> Integer.compare(
				arena.getMatchScores().getOrDefault(b, 0),
				arena.getMatchScores().getOrDefault(a, 0)
		));
		return players;
	}

	private void runRewardCommands(Arena arena, Player player, PlayerRole winner, int rank, String rewardKey) {
		List<String> commands = configManager.getMatchRewardCommands(rewardKey);
		if (commands.isEmpty()) {
			return;
		}

		for (String rawCommand : commands) {
			if (rawCommand == null || rawCommand.isBlank()) continue;
			String command = applyRewardPlaceholders(arena, player, winner, rank, rawCommand);
			Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.startsWith("/") ? command.substring(1) : command);
		}
	}

	private String applyRewardPlaceholders(Arena arena, Player player, PlayerRole winner, int rank, String command) {
		UUID uuid = player.getUniqueId();
		return command
				.replace("{player}", player.getName())
				.replace("{uuid}", uuid.toString())
				.replace("{rank}", String.valueOf(rank))
				.replace("{score}", String.valueOf(arena.getMatchScores().getOrDefault(uuid, 0)))
				.replace("{kills}", String.valueOf(arena.getMatchKills(uuid)))
				.replace("{map}", arena.getArenaName())
				.replace("{winner}", winner.name());
	}

	private void persistStats(Arena arena, Set<UUID> winners) {
		DatabaseManager db = plugin.getDatabaseManager();
		for (UUID uuid : arena.getPlayers()) {
			Player p = Bukkit.getPlayer(uuid);
			if (p == null) continue;
			int scoreEarned = arena.getMatchScores().getOrDefault(uuid, 0);
			int killsEarned = arena.getMatchKills(uuid);
			int winEarned = winners.contains(uuid) ? 1 : 0;

			db.addStatsAsync(uuid, p.getName(), scoreEarned, winEarned, killsEarned);
			p.sendMessage(Component.text("已结算入库：+" + scoreEarned + " 总积分", NamedTextColor.GRAY));
		}
	}

	private void resetPlayersAfterResult(Arena arena, boolean adminShutdown) {
		for (UUID uuid : arena.getPlayers()) {
			Player player = Bukkit.getPlayer(uuid);
			if (player == null) continue;
			playerStateService.resetPlayerData(player, arena);
			plugin.getScoreboardManager().removeBoard(player);
			if (adminShutdown) {
				player.sendMessage(Component.text("本局已由管理员结束，未写入积分与胜场。", NamedTextColor.GRAY));
			}
		}
	}

	private void persistMatchAnalytics(Arena arena, PlayerRole winner, Set<UUID> winners, boolean finalRevealTriggered, boolean adminShutdown) {
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("instanceName", arena.getInstanceName());
		root.put("mapName", arena.getArenaName());
		root.put("templateName", arena.getTemplate().getTemplateName());
		root.put("arenaMode", arena.getArenaMode().name());
		root.put("startedAt", arena.getMatchStartedAt());
		root.put("endedAt", arena.getMatchEndedAt());
		root.put("durationSeconds", Math.max(0L, (arena.getMatchEndedAt() - arena.getMatchStartedAt()) / 1000L));
		root.put("playerCount", arena.getInitialPlayerCount());
		root.put("initialHiderCount", arena.getInitialHiderCount());
		root.put("initialSeekerCount", arena.getInitialSeekerCount());
		root.put("winner", winner.name());
		root.put("survivingHiders", arena.getHiders().size());
		root.put("remainingSeekers", arena.getSeekers().size());
		root.put("originalSeekerCount", arena.getOriginalSeekers().size());
		root.put("infectedSeekerCount", Math.max(0, arena.getSeekers().size() - arena.getOriginalSeekers().size()));
		root.put("quitPlayerCount", arena.getQuitPlayers().size());
		root.put("finalRevealTriggered", finalRevealTriggered);
		root.put("endedByAdmin", adminShutdown);

		Map<String, Object> skillCounters = new LinkedHashMap<>();
		skillCounters.put("poopTauntUses", arena.getSkillUseCount("poop_taunt"));
		skillCounters.put("stinkyTauntUses", arena.getSkillUseCount("stinky_taunt"));
		skillCounters.put("screamTauntUses", arena.getSkillUseCount("scream_taunt"));
		skillCounters.put("partyTauntUses", arena.getSkillUseCount("party_taunt"));
		skillCounters.put("explosiveSheepUses", arena.getSkillUseCount("explosive_sheep"));
		root.put("skillCounters", skillCounters);

		List<Map<String, Object>> players = new ArrayList<>();
		for (UUID uuid : arena.getAllParticipants()) {
			Map<String, Object> playerData = new LinkedHashMap<>();
			playerData.put("uuid", uuid.toString());
			playerData.put("name", arena.getPlayerNameSnapshot().getOrDefault(uuid, "unknown"));
			playerData.put("finalRole", resolveFinalRole(arena, uuid).name());
			playerData.put("wasOriginalSeeker", arena.getOriginalSeekers().contains(uuid));
			playerData.put("won", winners.contains(uuid));
			playerData.put("matchScore", arena.getMatchScores().getOrDefault(uuid, 0));
			playerData.put("kills", arena.getMatchKills(uuid));
			playerData.put("quitMidGame", arena.getQuitPlayers().contains(uuid));
			players.add(playerData);
		}
		root.put("players", players);

		plugin.getMatchAnalyticsManager().appendRecordAsync(toJson(root), buildSummaryPayload(root, skillCounters));
	}

	private Map<String, Object> buildSummaryPayload(Map<String, Object> root, Map<String, Object> skillCounters) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("mapName", root.get("mapName"));
		payload.put("winner", root.get("winner"));
		payload.put("durationSeconds", root.get("durationSeconds"));
		payload.put("quitPlayerCount", root.get("quitPlayerCount"));
		payload.put("survivingHiders", root.get("survivingHiders"));
		payload.put("finalRevealTriggered", root.get("finalRevealTriggered"));
		payload.put("endedByAdmin", root.get("endedByAdmin"));
		payload.put("skillCounters", new LinkedHashMap<>(skillCounters));
		payload.put("players", new ArrayList<>((List<Map<String, Object>>) root.get("players")));
		return payload;
	}

	private PlayerRole resolveFinalRole(Arena arena, UUID uuid) {
		if (arena.getHiders().contains(uuid)) return PlayerRole.HIDER;
		if (arena.getSeekers().contains(uuid)) return PlayerRole.SEEKER;
		if (arena.getSpectators().contains(uuid)) return PlayerRole.SPECTATOR;
		if (arena.getOriginalSeekers().contains(uuid)) return PlayerRole.SEEKER;
		return PlayerRole.HIDER;
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
