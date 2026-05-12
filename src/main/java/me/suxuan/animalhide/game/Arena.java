package me.suxuan.animalhide.game;

import lombok.Getter;
import lombok.Setter;
import me.suxuan.animalhide.AnimalHidePlugin;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * 竞技场 (房间) 类
 * 代表一个独立进行的游戏对局
 */

@Getter
public class Arena {

	private final GameManager gameManager;
	private final String arenaName;
	@Setter
	private GameState state;
	@Setter
	private BossBar timeBar;
	@Setter
	private int timeLeft = 0;

	// 配置参数
	private final int minPlayers;
	private final int maxPlayers;
	private final Location waitingLobby;
	private final Location hiderSpawn;
	private final Location seekerSpawn;

	// 房间内的玩家列表
	private final Set<UUID> players = new HashSet<>();
	private final Set<UUID> hiders = new HashSet<>();
	private final Set<UUID> seekers = new HashSet<>();

	private final Location pos1;
	private final Location pos2;
	private final int aiAnimalCount;

	private final List<Entity> aiAnimals = new ArrayList<>();

	public Arena(GameManager gameManager, String arenaName, int minPlayers, int maxPlayers, Location waitingLobby,
	             Location hiderSpawn, Location seekerSpawn, Location pos1, Location pos2, int aiAnimalCount) {
		this.gameManager = gameManager;
		this.arenaName = arenaName;
		this.minPlayers = minPlayers;
		this.maxPlayers = maxPlayers;
		this.waitingLobby = waitingLobby;
		this.hiderSpawn = hiderSpawn;
		this.seekerSpawn = seekerSpawn;
		this.state = GameState.WAITING;
		this.pos1 = pos1;
		this.pos2 = pos2;
		this.aiAnimalCount = aiAnimalCount;
	}

	/**
	 * 玩家加入房间的逻辑处理
	 */
	public void addPlayer(Player player) {
		if (state != GameState.WAITING && state != GameState.STARTING) {
			player.sendMessage(Component.text("该房间正在游戏中，无法加入！"));
			return;
		}
		if (players.size() >= maxPlayers) {
			player.sendMessage(Component.text("房间已满！"));
			return;
		}

		players.add(player.getUniqueId());
		// 传送至等待大厅
		if (waitingLobby != null) {
			player.teleportAsync(waitingLobby);
		}

		broadcast(Component.text(player.getName() + " 加入了游戏! (" + players.size() + "/" + maxPlayers + ")"));
		gameManager.resetPlayerDataWithoutLobby(player, this);

		// 检查是否达到最低人数以触发倒计时
		checkStartCondition();
	}

	/**
	 * 玩家离开房间的逻辑处理
	 */
	public void removePlayer(Player player) {
		UUID uuid = player.getUniqueId();
		players.remove(uuid);
		boolean wasHider = hiders.remove(uuid);
		boolean wasSeeker = seekers.remove(uuid);

		gameManager.resetPlayerData(player, this);
		AnimalHidePlugin.getInstance().getScoreboardManager().removeBoard(player);

		broadcast(Component.text(player.getName() + " 退出了游戏!"));

		// 中途退出逻辑判定
		if (state == GameState.PLAYING) {
			if (wasHider && hiders.isEmpty()) {
				// 躲藏者全退了，寻找者直接获胜
				gameManager.endGame(this, PlayerRole.SEEKER);
			} else if (wasSeeker && seekers.isEmpty()) {
				// 寻找者全退了，躲藏者直接获胜
				gameManager.endGame(this, PlayerRole.HIDER);
			}
		} else if (state == GameState.STARTING && players.size() < minPlayers) {
			this.state = GameState.WAITING;
		}
	}

	/**
	 * 房间内广播消息 (使用 Paper 的 Component)
	 */
	public void broadcast(Component message) {
		for (UUID uuid : players) {
			Player p = Bukkit.getPlayer(uuid);
			if (p != null) p.sendMessage(message);
		}
	}

	private void checkStartCondition() {
		gameManager.checkAndStartCountdown(this);
	}

	/**
	 * 重置房间
	 */
	public void reset() {
		for (Entity e : aiAnimals) {
			e.remove();
		}
		aiAnimals.clear();
		if (this.timeBar != null) {
			for (UUID uuid : players) {
				Player p = Bukkit.getPlayer(uuid);
				if (p != null) {
					p.hideBossBar(this.timeBar);
					AnimalHidePlugin.getInstance().getScoreboardManager().removeBoard(p);
				}
			}
			this.timeBar = null;
		}
		this.state = GameState.WAITING;
		this.players.clear();
		this.hiders.clear();
		this.seekers.clear();
	}

}