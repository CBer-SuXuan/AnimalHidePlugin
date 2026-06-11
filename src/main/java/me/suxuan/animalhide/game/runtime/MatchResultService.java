package me.suxuan.animalhide.game.runtime;

import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.config.ConfigManager;
import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.GameState;
import me.suxuan.animalhide.game.PlayerRole;
import me.suxuan.animalhide.game.ScoringConfig;
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
			recordMatchSummary(arena, winner);
			executeSettlementRewardCommands(arena, winner);
			beginSettlementPhase(arena, winner, winners, adminShutdown, afterSettlement);
		} else {
			arena.broadcast(Component.text("      本局为管理员结束，不结算积分与胜场", NamedTextColor.GRAY));
			arena.broadcast(Component.text("=========================", NamedTextColor.YELLOW));
			updateSettlementBossBar(arena, 1, winner, adminShutdown);
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

		updateSettlementBossBar(arena, seconds, winner, adminShutdown);

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
				updateSettlementBossBar(arena, remaining, winner, adminShutdown);
				spawnWinnerFireworks(arena, winners, winner);
				remaining--;
			}
		}.runTaskTimer(plugin, 0L, 20L));
	}

	private void updateSettlementBossBar(Arena arena, int secondsLeft, PlayerRole winner, boolean adminShutdown) {
		int duration = configManager.getMatchSettlementDurationSeconds();
		String winnerName = adminShutdown ? "游戏已结束" : winner.getDisplayName() + "胜利！";
		BossBar.Color color = adminShutdown ? BossBar.Color.WHITE : winner == PlayerRole.SEEKER ? BossBar.Color.RED : BossBar.Color.GREEN;
		plugin.getBossBarManager().showSettlementBar(arena, secondsLeft, duration, winnerName, color);
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
		plugin.getBossBarManager().clearArena(arena);
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
		for (UUID uuid : arena.getAllParticipants()) {
			String playerName = arena.getPlayerNameSnapshot().getOrDefault(uuid, "unknown");
			int scoreEarned = arena.getMatchScores().getOrDefault(uuid, 0);
			int killsEarned = arena.getMatchKills(uuid);
			boolean won = winners.contains(uuid);
			boolean quitMidGame = arena.getQuitPlayers().contains(uuid);
			PlayerRole finalRole = resolveFinalRole(arena, uuid);

			plugin.getDatabaseManager().recordMatchStatsAsync(uuid, playerName, scoreEarned, killsEarned, won, finalRole, quitMidGame);
			Player online = Bukkit.getPlayer(uuid);
			if (online != null) {
				online.sendMessage(Component.text("已结算入库：+" + scoreEarned + " 总积分", NamedTextColor.GRAY));
			}
		}
	}

	private void resetPlayersAfterResult(Arena arena, boolean adminShutdown) {
		for (UUID uuid : arena.getPlayers()) {
			Player player = Bukkit.getPlayer(uuid);
			if (player == null) continue;
			playerStateService.resetPlayerData(player, arena);
			plugin.getBossBarManager().hide(player);
			plugin.getScoreboardManager().removeBoard(player);
			if (adminShutdown) {
				player.sendMessage(Component.text("本局已由管理员结束，未写入积分与胜场。", NamedTextColor.GRAY));
			}
		}
	}

	private void recordMatchSummary(Arena arena, PlayerRole winner) {
		plugin.getMatchStatsFileManager().recordMatchResult(arena.getArenaName(), winner, getTopPlayers(arena));
	}

	private List<Map<String, Object>> getTopPlayers(Arena arena) {
		List<Map.Entry<UUID, Integer>> sortedScores = new ArrayList<>(arena.getMatchScores().entrySet());
		sortedScores.sort((a, b) -> b.getValue().compareTo(a.getValue()));

		List<Map<String, Object>> topPlayers = new ArrayList<>();
		for (int i = 0; i < Math.min(3, sortedScores.size()); i++) {
			UUID uuid = sortedScores.get(i).getKey();
			Player player = Bukkit.getPlayer(uuid);
			String name = player != null ? player.getName() : arena.getPlayerNameSnapshot().getOrDefault(uuid, "unknown");
			topPlayers.add(Map.of(
					"player", name,
					"score", sortedScores.get(i).getValue()
			));
		}
		return topPlayers;
	}

	private PlayerRole resolveFinalRole(Arena arena, UUID uuid) {
		if (arena.getHiders().contains(uuid)) return PlayerRole.HIDER;
		if (arena.getSeekers().contains(uuid)) return PlayerRole.SEEKER;
		if (arena.getSpectators().contains(uuid)) return PlayerRole.SPECTATOR;
		if (arena.getOriginalSeekers().contains(uuid)) return PlayerRole.SEEKER;
		return PlayerRole.HIDER;
	}
}
