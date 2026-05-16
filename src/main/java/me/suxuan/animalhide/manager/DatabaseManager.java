package me.suxuan.animalhide.manager;

import me.suxuan.animalhide.AnimalHidePlugin;
import org.bukkit.Bukkit;

import java.io.File;
import java.sql.*;
import java.util.UUID;

public class DatabaseManager {

	private final AnimalHidePlugin plugin;
	private Connection connection;
	private final Object dbLock = new Object();

	public DatabaseManager(AnimalHidePlugin plugin) {
		this.plugin = plugin;
		connect();
		initTable();
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
				"score INT DEFAULT 0, " +
				"wins INT DEFAULT 0, " +
				"kills INT DEFAULT 0" +
				");";
		try (Statement stmt = connection.createStatement()) {
			stmt.execute(sql);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	// --- 异步更新数据 (防止数据库读写卡顿主线程) ---
	public void addStatsAsync(UUID uuid, String name, int scoreAdd, int winAdd, int killAdd) {
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			if (connection == null) {
				return;
			}
			String sql = "INSERT INTO player_stats (uuid, name, score, wins, kills) VALUES (?, ?, ?, ?, ?) " +
					"ON CONFLICT(uuid) DO UPDATE SET name = ?, score = score + ?, wins = wins + ?, kills = kills + ?;";
			synchronized (dbLock) {
				try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
					pstmt.setString(1, uuid.toString());
					pstmt.setString(2, name);
					pstmt.setInt(3, scoreAdd);
					pstmt.setInt(4, winAdd);
					pstmt.setInt(5, killAdd);
					// On Conflict
					pstmt.setString(6, name);
					pstmt.setInt(7, scoreAdd);
					pstmt.setInt(8, winAdd);
					pstmt.setInt(9, killAdd);
					pstmt.executeUpdate();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		});
	}

	// --- 同步读取数据 (用于 PAPI 提供变量) ---
	public int getStat(UUID uuid, String statType) {
		if (connection == null) {
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
				e.printStackTrace();
			}
		}
		return 0;
	}

	public void close() {
		try {
			if (connection != null && !connection.isClosed()) connection.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
}