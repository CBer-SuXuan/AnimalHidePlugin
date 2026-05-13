package me.suxuan.animalhide.game;

import lombok.Getter;
import lombok.Setter;
import me.suxuan.animalhide.AnimalHidePlugin;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * 代表一个独立进行的游戏对局
 */

@Getter
@Setter
public class Arena {

	private final GameManager gameManager;
	private final String arenaName;
	private GameState state;
	private BossBar timeBar;
	private int timeLeft = 0;
	private ArenaMode arenaMode = ArenaMode.ANIMAL;  // 默认生物模式
	private final Map<UUID, ArenaMode> modeVotes = new HashMap<>();  // 记录玩家模式投票
	private final Map<UUID, PlayerRole> rolePreferences = new HashMap<>();  // 记录玩家身份偏好

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
	private final Set<UUID> spectators = new HashSet<>();

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
		giveLobbyItems(player);

		gameManager.updatePlayerVisibility(player);

		// 检查是否达到最低人数以触发倒计时
		checkStartCondition();
	}

	/**
	 * 以旁观者身份加入正在进行的游戏
	 */
	public void addSpectator(Player player) {
		players.add(player.getUniqueId());
		spectators.add(player.getUniqueId());

		gameManager.resetPlayerDataWithoutLobby(player, this);

		player.setGameMode(org.bukkit.GameMode.ADVENTURE);

		player.setAllowFlight(true);
		player.setFlying(true);

		player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false, false));

		if (seekerSpawn != null) {
			player.teleportAsync(seekerSpawn);
		}

		ItemStack leaveItem = new ItemStack(Material.RED_BED);
		ItemMeta leaveMeta = leaveItem.getItemMeta();
		leaveMeta.displayName(Component.text("▶ 离开游戏 ◀", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
		leaveItem.setItemMeta(leaveMeta);
		player.getInventory().setItem(8, leaveItem);

		gameManager.updatePlayerVisibility(player);

		player.sendMessage(Component.text("你已作为旁观者加入！你可以飞行，但无法穿墙。", NamedTextColor.AQUA));
	}

	/**
	 * 发放等待大厅的交互物品
	 */
	private void giveLobbyItems(Player player) {
		ItemStack modeItem = new ItemStack(Material.RECOVERY_COMPASS);
		ItemMeta modeMeta = modeItem.getItemMeta();
		modeMeta.displayName(Component.text("▶ 选择游戏模式 ◀", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
		modeItem.setItemMeta(modeMeta);
		player.getInventory().setItem(0, modeItem);

		ItemStack roleItem = new ItemStack(Material.DIAMOND_HELMET);
		ItemMeta roleMeta = roleItem.getItemMeta();
		roleMeta.displayName(Component.text("▶ 选择期望身份 ◀", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
		roleItem.setItemMeta(roleMeta);
		player.getInventory().setItem(4, roleItem);

		ItemStack leaveItem = new ItemStack(Material.RED_BED);
		ItemMeta leaveMeta = leaveItem.getItemMeta();
		leaveMeta.displayName(Component.text("▶ 离开房间 ◀", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
		leaveItem.setItemMeta(leaveMeta);
		player.getInventory().setItem(8, leaveItem);
	}

	/**
	 * 玩家离开房间的逻辑处理
	 */
	public void removePlayer(Player player) {
		UUID uuid = player.getUniqueId();
		players.remove(uuid);
		boolean wasHider = hiders.remove(uuid);
		boolean wasSeeker = seekers.remove(uuid);
		spectators.remove(uuid);

		gameManager.resetPlayerData(player, this);
		AnimalHidePlugin.getInstance().getScoreboardManager().removeBoard(player);

		gameManager.updatePlayerVisibility(player);

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
	 * 获取指定模式的票数
	 */
	public int getModeVoteCount(ArenaMode mode) {
		return (int) modeVotes.values().stream().filter(m -> m == mode).count();
	}

	/**
	 * 获取指定身份偏好的人数
	 */
	public int getRolePreferenceCount(PlayerRole role) {
		return (int) rolePreferences.values().stream().filter(r -> r == role).count();
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
		this.spectators.clear();
		this.rolePreferences.clear();
		this.modeVotes.clear();
		this.arenaMode = ArenaMode.ANIMAL;
	}

}