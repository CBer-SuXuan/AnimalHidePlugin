package me.suxuan.animalhide.manager;

import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.game.PlayerRole;
import org.bukkit.Bukkit;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

public class DatabaseManager {

	private final AnimalHidePlugin plugin;
	private Connection connection;
	private final Object dbLock = new Object();

	public DatabaseManager(AnimalHidePlugin plugin) {
		this.plugin = plugin;
		connect();
		initTable();
		migrateLegacyColumns();
	}

	private void connect() {
		try {
			File dataFolder = plugin.getDataFolder();
			if (!dataFolder.exists()) dataFolder.mkdirs();
			File dbFile = new File(dataFolder, "database.db");
			String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
			connection = DriverManager.getConnection(url);
			plugin.getComponentLogger().info("已成功连接到 SQLite 数据库！");
		} catch (SQLException e) {
			plugin.getComponentLogger().error("数据库连接失败: " + e.getMessage());
		}
	}

	private void initTable() {
		if (connection == null) {
			plugin.getComponentLogger().error("数据库初始化失败：连接未建立。");
			return;
		}
		String sql = "CREATE TABLE IF NOT EXISTS player_stats (" +
				"uuid VARCHAR(36) PRIMARY KEY, " +
				"name VARCHAR(16), " +
				"games_played INT DEFAULT 0, " +
				"high_score INT DEFAULT 0, " +
				"total_score INT DEFAULT 0, " +
				"total_kills INT DEFAULT 0, " +
				"total_wins INT DEFAULT 0, " +
				"seeker_wins INT DEFAULT 0, " +
				"hider_wins INT DEFAULT 0, " +
				"quit_count INT DEFAULT 0" +
				");";
		try (Statement stmt = connection.createStatement()) {
			stmt.execute(sql);
		} catch (SQLException e) {
			plugin.getComponentLogger().error("初始化玩家统计表失败: " + e.getMessage());
		}
	}

	private void migrateLegacyColumns() {
		ensureColumnExists("games_played", "INT DEFAULT 0");
		ensureColumnExists("high_score", "INT DEFAULT 0");
		ensureColumnExists("total_score", "INT DEFAULT 0");
		ensureColumnExists("total_kills", "INT DEFAULT 0");
		ensureColumnExists("total_wins", "INT DEFAULT 0");
		ensureColumnExists("seeker_wins", "INT DEFAULT 0");
		ensureColumnExists("hider_wins", "INT DEFAULT 0");
		ensureColumnExists("quit_count", "INT DEFAULT 0");
		migrateLegacyColumnData("score", "total_score");
		migrateLegacyColumnData("wins", "total_wins");
		migrateLegacyColumnData("kills", "total_kills");
	}

	private void ensureColumnExists(String columnName, String definition) {
		if (connection == null || hasColumn(columnName)) {
			return;
		}
		String sql = "ALTER TABLE player_stats ADD COLUMN " + columnName + " " + definition;
		synchronized (dbLock) {
			try (Statement stmt = connection.createStatement()) {
				stmt.execute(sql);
			} catch (SQLException e) {
				plugin.getComponentLogger().warn("补充玩家统计列失败 {}: {}", columnName, e.getMessage());
			}
		}
	}

	private boolean hasColumn(String columnName) {
		if (connection == null) {
			return false;
		}
		synchronized (dbLock) {
			try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("PRAGMA table_info(player_stats)")) {
				while (rs.next()) {
					if (columnName.equalsIgnoreCase(rs.getString("name"))) {
						return true;
					}
				}
			} catch (SQLException e) {
				plugin.getComponentLogger().warn("读取 player_stats 表结构失败: {}", e.getMessage());
			}
		}
		return false;
	}

	private void migrateLegacyColumnData(String legacyColumn, String newColumn) {
		if (connection == null || !hasColumn(legacyColumn) || !hasColumn(newColumn)) {
			return;
		}
		String sql = "UPDATE player_stats SET " + newColumn + " = CASE WHEN " + newColumn + " = 0 THEN COALESCE(" + legacyColumn + ", 0) ELSE " + newColumn + " END";
		synchronized (dbLock) {
			try (Statement stmt = connection.createStatement()) {
				stmt.executeUpdate(sql);
			} catch (SQLException e) {
				plugin.getComponentLogger().warn("迁移旧列 {} -> {} 失败: {}", legacyColumn, newColumn, e.getMessage());
			}
		}
	}

	public void recordMatchStatsAsync(UUID uuid, String name, int scoreEarned, int killsEarned, boolean won, PlayerRole finalRole, boolean quitMidGame) {
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> recordMatchStats(uuid, name, scoreEarned, killsEarned, won, finalRole, quitMidGame));
	}

	private void recordMatchStats(UUID uuid, String name, int scoreEarned, int killsEarned, boolean won, PlayerRole finalRole, boolean quitMidGame) {
		if (connection == null) {
			return;
		}
		String sql = "INSERT INTO player_stats (uuid, name, games_played, high_score, total_score, total_kills, total_wins, seeker_wins, hider_wins, quit_count) " +
				"VALUES (?, ?, 1, ?, ?, ?, ?, ?, ?, ?) " +
				"ON CONFLICT(uuid) DO UPDATE SET " +
				"name = excluded.name, " +
				"games_played = player_stats.games_played + 1, " +
				"high_score = MAX(player_stats.high_score, excluded.high_score), " +
				"total_score = player_stats.total_score + excluded.total_score, " +
				"total_kills = player_stats.total_kills + excluded.total_kills, " +
				"total_wins = player_stats.total_wins + excluded.total_wins, " +
				"seeker_wins = player_stats.seeker_wins + excluded.seeker_wins, " +
				"hider_wins = player_stats.hider_wins + excluded.hider_wins, " +
				"quit_count = player_stats.quit_count + excluded.quit_count;";

		int totalWins = won ? 1 : 0;
		int seekerWins = won && finalRole == PlayerRole.SEEKER ? 1 : 0;
		int hiderWins = won && finalRole == PlayerRole.HIDER ? 1 : 0;
		int quitCount = quitMidGame ? 1 : 0;

		synchronized (dbLock) {
			try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
				pstmt.setString(1, uuid.toString());
				pstmt.setString(2, name);
				pstmt.setInt(3, scoreEarned);
				pstmt.setInt(4, scoreEarned);
				pstmt.setInt(5, killsEarned);
				pstmt.setInt(6, totalWins);
				pstmt.setInt(7, seekerWins);
				pstmt.setInt(8, hiderWins);
				pstmt.setInt(9, quitCount);
				pstmt.executeUpdate();
			} catch (SQLException e) {
				plugin.getComponentLogger().error("写入玩家统计失败 {}: {}", name, e.getMessage());
			}
		}
	}

	public int getStat(UUID uuid, String statType) {
		if (connection == null || !isAllowedStat(statType)) {
			return 0;
		}
		String sql = "SELECT " + statType + " FROM player_stats WHERE uuid = ?";
		synchronized (dbLock) {
			try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
				pstmt.setString(1, uuid.toString());
				ResultSet rs = pstmt.executeQuery();
				if (rs.next()) {
					return rs.getInt(statType);
				}
			} catch (SQLException e) {
				plugin.getComponentLogger().error("读取玩家统计失败 {}: {}", statType, e.getMessage());
			}
		}
		return 0;
	}

	private boolean isAllowedStat(String statType) {
		return switch (statType) {
			case "games_played", "high_score", "total_score", "total_kills", "total_wins", "seeker_wins", "hider_wins", "quit_count" -> true;
			default -> false;
		};
	}

	public void close() {
		try {
			if (connection != null && !connection.isClosed()) connection.close();
		} catch (SQLException e) {
			plugin.getComponentLogger().error("关闭 SQLite 连接失败: " + e.getMessage());
		}
	}
}
