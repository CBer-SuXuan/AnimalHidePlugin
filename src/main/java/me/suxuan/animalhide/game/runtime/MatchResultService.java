package me.suxuan.animalhide.game.runtime;

import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.GameState;
import me.suxuan.animalhide.game.PlayerRole;
import me.suxuan.animalhide.game.ScoringConfig;
import me.suxuan.animalhide.manager.DatabaseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class MatchResultService {

	private final AnimalHidePlugin plugin;
	private final PlayerStateService playerStateService;

	public MatchResultService(AnimalHidePlugin plugin, PlayerStateService playerStateService) {
		this.plugin = plugin;
		this.playerStateService = playerStateService;
	}

	public void endGame(Arena arena, PlayerRole winner, Consumer<Arena> destroyArenaMatch) {
		arena.openPhaseDoors();
		arena.setState(GameState.ENDING);

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
		} else {
			arena.broadcast(Component.text("      本局为管理员结束，不结算积分与胜场", NamedTextColor.GRAY));
			arena.broadcast(Component.text("=========================", NamedTextColor.YELLOW));
		}

		resetPlayersAfterResult(arena, adminShutdown);
		destroyArenaMatch.accept(arena);
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
}
