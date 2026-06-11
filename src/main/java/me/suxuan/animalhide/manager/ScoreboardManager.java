package me.suxuan.animalhide.manager;

import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.GameManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashSet;
import java.util.UUID;

/**
 * 计分板管理器
 */
public class ScoreboardManager {

	private final AnimalHidePlugin plugin;
	private final GameManager gameManager;

	private static final String COLLISION_TEAM = "ah_collide";

	public ScoreboardManager(AnimalHidePlugin plugin, GameManager gameManager) {
		this.plugin = plugin;
		this.gameManager = gameManager;
		startUpdateTask();
	}

	/**
	 * 开启全局计分板刷新任务
	 */
	private void startUpdateTask() {
		new BukkitRunnable() {
			@Override
			public void run() {
				for (Player player : Bukkit.getOnlinePlayers()) {
					player.setCollidable(true);
					Arena arena = gameManager.getArenaByPlayer(player);
					if (arena != null) {
						ensureCollisionTeam(player, arena);
					} else {
						clearPluginSidebar(player);
					}
				}
			}
		}.runTaskTimer(plugin, 0L, 2L);
	}

	/**
	 * 清除本插件创建过的右侧 Sidebar Objective，使玩家视觉上不再看到计分板。
	 */
	private void clearPluginSidebar(Player player) {
		Scoreboard board = player.getScoreboard();
		Objective sidebar = board.getObjective(DisplaySlot.SIDEBAR);
		if (sidebar != null && (sidebar.getName().startsWith("ah_") || sidebar.getName().equals("ah_lobby"))) {
			sidebar.unregister();
		}
	}

	/**
	 * 清理并移除玩家的计分板
	 */
	public void removeBoard(Player player) {
		player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
		player.setCollidable(true);
		clearPluginSidebar(player);
	}

	/**
	 * 本局所有玩家归入同一队伍并开启碰撞，覆盖 TAB 等插件的「无碰撞」队伍设置。
	 */
	private void ensureCollisionTeam(Player player, Arena arena) {
		Scoreboard board = player.getScoreboard();
		if (board == Bukkit.getScoreboardManager().getMainScoreboard()) {
			board = Bukkit.getScoreboardManager().getNewScoreboard();
			player.setScoreboard(board);
		}
		clearPluginSidebar(player);

		Team team = board.getTeam(COLLISION_TEAM);
		if (team == null) {
			team = board.registerNewTeam(COLLISION_TEAM);
			team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.ALWAYS);
		}
		for (String entry : new HashSet<>(team.getEntries())) {
			Player member = Bukkit.getPlayerExact(entry);
			if (member == null || gameManager.getArenaByPlayer(member) != arena) {
				team.removeEntry(entry);
			}
		}
		for (UUID uuid : arena.getPlayers()) {
			Player member = Bukkit.getPlayer(uuid);
			if (member != null && !team.hasEntry(member.getName())) {
				team.addEntry(member.getName());
			}
		}
	}
}