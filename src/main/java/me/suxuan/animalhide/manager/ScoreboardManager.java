package me.suxuan.animalhide.manager;

import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.ArenaMode;
import me.suxuan.animalhide.game.GameManager;
import me.suxuan.animalhide.game.GameState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.util.*;

/**
 * 计分板管理器
 */
public class ScoreboardManager {

	private final AnimalHidePlugin plugin;
	private final GameManager gameManager;

	// 【新增】用于缓存玩家上一次的计分板内容
	private final Map<UUID, List<String>> lastBoardData = new HashMap<>();

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
				for (Arena arena : gameManager.getArenas().values()) {
					for (UUID uuid : arena.getPlayers()) {
						Player player = Bukkit.getPlayer(uuid);
						if (player != null) {
							updateBoard(player, arena);
						}
					}
				}
			}
		}.runTaskTimer(plugin, 0L, 2L); // 【关键修改】改为 2 Tick(0.1秒) 极速轮询，消除视觉延迟
	}

	/**
	 * 更新单个玩家的计分板
	 */
	private void updateBoard(Player player, Arena arena) {
		Scoreboard board = player.getScoreboard();
		if (board == Bukkit.getScoreboardManager().getMainScoreboard()) {
			board = Bukkit.getScoreboardManager().getNewScoreboard();
			player.setScoreboard(board);
		}

		Team team = board.getTeam("ah_no_col");
		if (team == null) {
			team = board.registerNewTeam("ah_no_col");
			team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
		}
		if (!team.hasEntry(player.getName())) {
			team.addEntry(player.getName());
		}

		// 1. 构建显示的文本行
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
		} else if (arena.getState() == GameState.STARTING || arena.getState() == GameState.WAITING) {
			lines.add("§f人数: §a" + arena.getPlayers().size() + "§8/§a" + arena.getMaxPlayers());

			lines.add("§6");
			lines.add("§f模式投票:");
			lines.add(" §7- 生物: §e" + arena.getModeVoteCount(ArenaMode.ANIMAL));
			lines.add(" §7- 怪物: §e" + arena.getModeVoteCount(ArenaMode.MONSTER));

			if (arena.getState() == GameState.STARTING) {
				lines.add("§f倒计时: §e" + arena.getTimeLeft() + "秒");
			}
		}

		lines.add("§e");
		lines.add("§7mcbi.top");

		// 缓存对比拦截：如果文本内容没有变，直接停止操作，不向客户端发包！
		if (lines.equals(lastBoardData.get(player.getUniqueId()))) {
			return;
		}
		// 保存最新的文本内容到缓存中
		lastBoardData.put(player.getUniqueId(), new ArrayList<>(lines));

		// 使用 UUID 保证生成的目标名绝对唯一，防止时间戳碰撞
		String objName = "ah_" + UUID.randomUUID().toString().substring(0, 8);
		Objective newObj = board.registerNewObjective(objName, Criteria.DUMMY, Component.text("🐾 动物躲猫猫 🐾", NamedTextColor.YELLOW));

		// 3. 倒序插入分数
		int score = lines.size();
		for (String line : lines) {
			newObj.getScore(line).setScore(score--);
		}

		// 4. 将新内容推送到侧边栏
		newObj.setDisplaySlot(DisplaySlot.SIDEBAR);

		// 5. 注销旧的 Objective，完成平滑替换
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
		lastBoardData.remove(player.getUniqueId());
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