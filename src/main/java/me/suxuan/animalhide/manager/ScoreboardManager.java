package me.suxuan.animalhide.manager;

import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.GameManager;
import me.suxuan.animalhide.game.GameState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 计分板管理器
 */
public class ScoreboardManager {

	private final AnimalHidePlugin plugin;
	private final GameManager gameManager;

	public ScoreboardManager(AnimalHidePlugin plugin, GameManager gameManager) {
		this.plugin = plugin;
		this.gameManager = gameManager;
		startUpdateTask();
	}

	/**
	 * 开启全局计分板刷新任务 (每 20 Tick / 1秒)
	 */
	private void startUpdateTask() {
		new BukkitRunnable() {
			@Override
			public void run() {
				for (Arena arena : gameManager.getArenas().values()) {
					for (UUID uuid : arena.getPlayers()) {
						Player player = Bukkit.getPlayer(uuid);
						if (player != null) {
							updateBoard(player, arena);
						}
					}
				}
			}
		}.runTaskTimer(plugin, 0L, 20L);
	}

	/**
	 * 更新单个玩家的计分板
	 */
	private void updateBoard(Player player, Arena arena) {
		Scoreboard board = player.getScoreboard();
		// 如果玩家用的是主计分板，给他们分配一个私人计分板
		if (board == Bukkit.getScoreboardManager().getMainScoreboard()) {
			board = Bukkit.getScoreboardManager().getNewScoreboard();
			player.setScoreboard(board);
		}

		// 生成唯一前缀以应用双缓冲无闪烁刷新
		String objName = "ah_" + (System.currentTimeMillis() % 10000);
		Objective newObj = board.registerNewObjective(objName, Criteria.DUMMY, Component.text("🐾 动物躲猫猫 🐾", NamedTextColor.YELLOW));

		// 构建显示的文本行
		List<String> lines = new ArrayList<>();
		lines.add("§a"); // 空行作分隔
		lines.add("§f地图: §a" + arena.getArenaName());
		lines.add("§f状态: " + getStateString(arena.getState()));
		lines.add("§b");

		if (arena.getState() == GameState.PLAYING) {
			String role = arena.getHiders().contains(player.getUniqueId()) ? "§a躲藏者" :
					(arena.getSeekers().contains(player.getUniqueId()) ? "§c寻找者" : "§7旁观者");
			lines.add("§f你的身份: " + role);
			lines.add("§c");
			lines.add("§f存活躲藏者: §a" + arena.getHiders().size());
			lines.add("§f寻找者数量: §c" + arena.getSeekers().size());
			lines.add("§d");
			lines.add("§f剩余时间: §e" + formatTime(arena.getTimeLeft()));
		} else if (arena.getState() == GameState.STARTING) {
			lines.add("§f人数: §a" + arena.getPlayers().size() + "§8/§a" + arena.getMaxPlayers());
			lines.add("§f倒计时: §e" + arena.getTimeLeft() + "秒");
		} else {
			lines.add("§f人数: §a" + arena.getPlayers().size() + "§8/§a" + arena.getMaxPlayers());
			lines.add("§7等待玩家加入...");
		}

		lines.add("§e");
		lines.add("§7mcbi.top");

		// 倒序插入分数 (让第一行在最上面)
		int score = lines.size();
		for (String line : lines) {
			// 注意：计分板不允许重复文本，如果出现两行空行，需要用不同的颜色代码（比如 §a, §b, §c）区分
			newObj.getScore(line).setScore(score--);
		}

		// 将新内容推送到侧边栏
		newObj.setDisplaySlot(DisplaySlot.SIDEBAR);

		// 注销旧的 Objective，完成平滑替换
		for (Objective obj : board.getObjectives()) {
			if (obj.getName().startsWith("ah_") && !obj.getName().equals(objName)) {
				obj.unregister();
			}
		}
	}

	/**
	 * 清理并移除玩家的计分板
	 */
	public void removeBoard(Player player) {
		player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
	}

	private String getStateString(GameState state) {
		return switch (state) {
			case WAITING -> "§7等待中";
			case STARTING -> "§e即将开始";
			case PLAYING -> "§c游戏中";
			case ENDING -> "§b结算中";
		};
	}

	private String formatTime(int seconds) {
		int m = seconds / 60;
		int s = seconds % 60;
		return String.format("%02d:%02d", m, s);
	}
}