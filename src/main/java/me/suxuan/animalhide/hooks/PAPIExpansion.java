package me.suxuan.animalhide.hooks;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.GameManager;
import me.suxuan.animalhide.manager.DatabaseManager;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PAPIExpansion extends PlaceholderExpansion {

	private final GameManager gameManager;

	public PAPIExpansion(GameManager gameManager) {
		this.gameManager = gameManager;
	}

	/**
	 * 你的占位符前缀，例如 %animalhide_...%
	 */
	@Override
	public @NotNull String getIdentifier() {
		return "animalhide";
	}

	@Override
	public @NotNull String getAuthor() {
		return "SuXuan_Dev";
	}

	@Override
	public @NotNull String getVersion() {
		return "1.0.0";
	}

	/**
	 * 因为我们的扩展是内置在插件里的，必须返回 true 以防 PAPI 把它当成下载的独立扩展清理掉
	 */
	@Override
	public boolean persist() {
		return true;
	}

	/**
	 * 核心逻辑：拦截并替换占位符
	 */
	@Override
	public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {

		// ==========================================
		// === 1. 地图状态与人数查询 (无需绑定玩家) ===
		// ==========================================

		// 用法: %animalhide_arena_players_example% -> 返回 example 地图的当前人数
		if (params.startsWith("arena_players_")) {
			String arenaName = params.substring("arena_players_".length());
			Arena arena = gameManager.getArena(arenaName);
			if (arena != null) {
				return String.valueOf(arena.getPlayers().size());
			}
			return "0";
		}

		// 用法: %animalhide_arena_max_example% -> 返回 example 地图的最大人数
		if (params.startsWith("arena_max_")) {
			String arenaName = params.substring("arena_max_".length());
			Arena arena = gameManager.getArena(arenaName);
			if (arena != null) {
				return String.valueOf(arena.getMaxPlayers());
			}
			return "0";
		}

		// 用法: %animalhide_arena_state_example% -> 返回 example 地图的中文状态
		if (params.startsWith("arena_state_")) {
			String arenaName = params.substring("arena_state_".length());
			Arena arena = gameManager.getArena(arenaName);
			if (arena != null) {
				return switch (arena.getState()) {
					case WAITING -> "§a等待中";
					case STARTING -> "§e倒计时";
					case PLAYING -> "§c游戏中";
					case ENDING -> "§7结算中";
				};
			}
			return "§8未加载";
		}

		// ==========================================
		// === 2. 玩家个人状态查询 (必须有玩家实体) ===
		// ==========================================
		if (player != null) {

			DatabaseManager db = AnimalHidePlugin.getInstance().getDatabaseManager();

			switch (params) {
				// %animalhide_stat_score% -> 玩家总积分
				case "stat_score" -> {
					return String.valueOf(db.getStat(player.getUniqueId(), "score"));
				}

				// %animalhide_stat_wins% -> 玩家总胜场
				case "stat_wins" -> {
					return String.valueOf(db.getStat(player.getUniqueId(), "wins"));
				}

				// %animalhide_stat_kills% -> 玩家总击杀
				case "stat_kills" -> {
					return String.valueOf(db.getStat(player.getUniqueId(), "kills"));
				}

				// 用法: %animalhide_player_arena% -> 返回玩家当前所在的地图名
				case "player_arena" -> {
					Arena arena = gameManager.getArenaByPlayer(player);
					return arena != null ? arena.getArenaName() : "大厅";
				}

				// 用法: %animalhide_player_role% -> 返回玩家当前的身份
				case "player_role" -> {
					Arena arena = gameManager.getArenaByPlayer(player);
					if (arena != null) {
						if (arena.getHiders().contains(player.getUniqueId())) return "§a躲藏者";
						if (arena.getSeekers().contains(player.getUniqueId())) return "§c寻找者";
						return "§7等待分配";
					}
					return "§8无";
				}
			}
		}

		// 返回 null 表示未知的占位符，PAPI 会原样输出
		return null;
	}
}