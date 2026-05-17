package me.suxuan.animalhide.manager;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.ArenaMode;
import me.suxuan.animalhide.game.GameManager;
import me.suxuan.animalhide.game.GameState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
				for (Player player : Bukkit.getOnlinePlayers()) {
					Arena arena = gameManager.getArenaByPlayer(player);
					if (arena != null) {
						updateBoard(player, arena); // 游戏内计分板
					} else {
						updateLobbyBoard(player); // 大厅计分板
					}
				}
			}
		}.runTaskTimer(plugin, 0L, 2L);
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

		Team allies = board.getTeam("ah_allies");
		if (arena.getState() == GameState.PLAYING) {
			// 1. 创建盟友队伍 (绿色)
			if (allies == null) {
				allies = board.registerNewTeam("ah_allies");
				allies.color(NamedTextColor.GREEN); // 设置名片和 Tab 颜色为绿
				allies.setOption(org.bukkit.scoreboard.Team.Option.COLLISION_RULE, org.bukkit.scoreboard.Team.OptionStatus.NEVER);
			}

			// 2. 创建敌人队伍 (红色)
			org.bukkit.scoreboard.Team enemies = board.getTeam("ah_enemies");
			if (enemies == null) {
				enemies = board.registerNewTeam("ah_enemies");
				enemies.color(NamedTextColor.RED); // 设置名片和 Tab 颜色为红
				enemies.setOption(org.bukkit.scoreboard.Team.Option.COLLISION_RULE, org.bukkit.scoreboard.Team.OptionStatus.NEVER);
			}

			boolean isPlayerSeeker = arena.getSeekers().contains(player.getUniqueId());

			// 3. 遍历房间内所有人，根据“当前玩家的视角”把他们分到敌友队伍中
			for (UUID targetId : arena.getPlayers()) {
				Player target = Bukkit.getPlayer(targetId);
				if (target == null) continue;

				boolean isTargetSeeker = arena.getSeekers().contains(targetId);

				// 如果阵营相同，就是盟友；否则就是敌人
				if (isPlayerSeeker == isTargetSeeker) {
					enemies.removeEntry(target.getName()); // 确保不在敌人队
					if (!allies.hasEntry(target.getName())) allies.addEntry(target.getName());
				} else {
					allies.removeEntry(target.getName()); // 确保不在盟友队
					if (!enemies.hasEntry(target.getName())) enemies.addEntry(target.getName());
				}
			}
		} else {
			Team enemies = board.getTeam("ah_enemies");
			if (allies != null) for (String entry : allies.getEntries()) allies.removeEntry(entry);
			if (enemies != null) for (String entry : enemies.getEntries()) enemies.removeEntry(entry);
			// 等待阶段尚未分配阵营，统一设置为灰色无碰撞
			Team waitTeam = board.getTeam("ah_wait");
			if (waitTeam == null) {
				waitTeam = board.registerNewTeam("ah_wait");
				waitTeam.color(NamedTextColor.GRAY);
				waitTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
			}
			for (UUID targetId : arena.getPlayers()) {
				Player target = Bukkit.getPlayer(targetId);
				if (target != null && !waitTeam.hasEntry(target.getName())) {
					waitTeam.addEntry(target.getName());
				}
			}
		}

		// 1. 构建显示的文本行
		List<String> lines = new ArrayList<>();

		if (arena.getState() == GameState.PLAYING) {
			boolean isHider = arena.getHiders().contains(player.getUniqueId());
			boolean isSeeker = arena.getSeekers().contains(player.getUniqueId());

			String hiderSuffix = isHider ? " §7(你)" : "";
			String seekerSuffix = isSeeker ? " §7(你)" : "";

			lines.add("§f躲藏者: §a" + arena.getHiders().size() + hiderSuffix);
			lines.add("§f寻找者: §c" + arena.getSeekers().size() + seekerSuffix);

			// 个人成长信息：寻找者看击杀升级进度，躲藏者看弓箭升级进度
			lines.add("§d");
			if (isSeeker) {
				appendSeekerLevelLines(lines, arena, player);
			} else if (isHider) {
				appendHiderBowLines(lines, arena, player);
			}
		} else if (arena.getState() == GameState.STARTING || arena.getState() == GameState.WAITING) {
			lines.add("§a"); // 空行作分隔
			lines.add("§f地图: §a" + arena.getArenaName());
			lines.add("§f状态: " + getStateString(arena.getState()));
			lines.add("§b");
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

		newObj.numberFormat(NumberFormat.blank());

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
	 * 更新全服主城大厅的计分板
	 */
	private void updateLobbyBoard(Player player) {
		Scoreboard board = player.getScoreboard();
		if (board == Bukkit.getScoreboardManager().getMainScoreboard()) {
			board = Bukkit.getScoreboardManager().getNewScoreboard();
			player.setScoreboard(board);
		}

		Objective objective = board.getObjective("ah_lobby");
		if (objective == null) {
			objective = board.registerNewObjective("ah_lobby", "dummy",
					Component.text("躲猫猫小游戏", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true));
			objective.setDisplaySlot(DisplaySlot.SIDEBAR);
			objective.numberFormat(NumberFormat.blank());
		}

		// 构建大厅显示的文本
		List<String> lines = new ArrayList<>();
		lines.add("§1");
		lines.add("§f玩家: §a" + player.getName());
		lines.add("§f全服在线: §e" + Bukkit.getOnlinePlayers().size() + " 人");
		lines.add("§2");
		lines.add("§7正在主城闲逛...");
		lines.add("§3");
		lines.add("§7mcbi.top");

		// 刷新计分板内容 (防止闪烁的替换写法)
		for (String entry : board.getEntries()) {
			if (!lines.contains(entry)) {
				board.resetScores(entry);
			}
		}
		int score = lines.size();
		for (String line : lines) {
			objective.getScore(line).setScore(score--);
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

	/**
	 * 拼一个简易的 ASCII 进度条：current / total 段，filledColor + emptyColor 着色。
	 * 总段数固定 10，便于不同分母统一观感。
	 */
	private String makeProgressBar(int current, int total, String filledColor, String emptyColor) {
		final int segments = 10;
		if (total <= 0) total = 1;
		// 用 Math.round(double)→long 再走 clamp(long,int,int)→int 的重载，避免类型不匹配
		int filled = Math.clamp(Math.round((double) current * segments / total), 0, segments);
		StringBuilder sb = new StringBuilder();
		sb.append(filledColor);
		for (int i = 0; i < filled; i++) sb.append('|');
		sb.append(emptyColor);
		for (int i = filled; i < segments; i++) sb.append('|');
		return sb.toString();
	}

	/**
	 * 寻找者侧：等级 + 总体击杀升级进度（一条长进度条直接走到满级）。
	 * 因为每级只需 +1 杀，单级进度条只有 0/1 没有视觉意义，
	 * 所以这里改成展示「向满级冲刺」的累计进度。
	 */
	private void appendSeekerLevelLines(List<String> lines, Arena arena, Player player) {
		int kills = arena.getMatchKills(player.getUniqueId());
		int level = GameManager.seekerLevelOf(kills);
		final int killsToMax = GameManager.MAX_SEEKER_LEVEL - 1; // 4 杀即满级
		boolean maxed = level >= GameManager.MAX_SEEKER_LEVEL;

		lines.add("§f寻找者等级: §c§lLv." + level + (maxed ? " §6(MAX)" : ""));
		int capped = Math.min(kills, killsToMax);
		lines.add(" " + makeProgressBar(capped, killsToMax, "§a", "§7"));
		if (maxed) {
			lines.add("§f击杀总数: §a" + kills);
		} else {
			lines.add("§f升级进度: §a" + capped + "§7/§e" + killsToMax + " §7击杀");
		}
	}

	/**
	 * 躲藏者侧：弓箭等级（每命中 5 次升 1 级）+ 整体升级进度。
	 * 同样直接展示「冲向满级」的全局进度，避免每 5 命中重置看上去没动静。
	 */
	private void appendHiderBowLines(List<String> lines, Arena arena, Player player) {
		int hits = arena.getArrowHits().getOrDefault(player.getUniqueId(), 0);
		int level = hits / 5; // 5 命中升 Lv.1，10 命中升 Lv.2，15 命中后视为满级
		final int hitsToMax = 15;
		boolean maxed = hits >= hitsToMax;

		lines.add("§f弓箭等级: §b§lLv." + level + (maxed ? " §6(MAX)" : ""));
		int capped = Math.min(hits, hitsToMax);
		lines.add(" " + makeProgressBar(capped, hitsToMax, "§b", "§7"));
		if (maxed) {
			lines.add("§f命中总数: §b" + hits);
		} else {
			lines.add("§f升级进度: §b" + capped + "§7/§e" + hitsToMax + " §7命中");
		}
	}
}