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
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/**
 * 动态对局实例 (Match)
 */
@Getter
@Setter
public class Arena {

	private final GameManager gameManager;
	private final ArenaTemplate template;

	private String instanceName;
	private World currentWorld;

	private GameState state;
	private BossBar timeBar;
	private int timeLeft = 0;
	private ArenaMode arenaMode = ArenaMode.ANIMAL;

	private final Map<UUID, ArenaMode> modeVotes = new HashMap<>();
	private final Map<UUID, PlayerRole> rolePreferences = new HashMap<>();

	private final Set<UUID> players = new HashSet<>();
	private final Set<UUID> hiders = new HashSet<>();
	private final Set<UUID> seekers = new HashSet<>();
	private final Set<UUID> spectators = new HashSet<>();
	private final Set<UUID> originalSeekers = new HashSet<>();

	private final Map<UUID, Integer> arrowHits = new HashMap<>();
	private final Map<UUID, Integer> fireworkUses = new HashMap<>();
	private final Map<UUID, Long> disguiseLockouts = new HashMap<>();

	private final List<Entity> aiAnimals = new ArrayList<>();
	private final Map<UUID, Integer> matchScores = new HashMap<>();
	private final Map<UUID, Integer> matchKills = new HashMap<>();

	public Arena(GameManager gameManager, ArenaTemplate template, String instanceName) {
		this.gameManager = gameManager;
		this.template = template;
		this.instanceName = instanceName;
		this.state = GameState.ENDING;
	}

	public String getArenaName() {
		return template.getMapName();
	}

	public int getMinPlayers() {
		return template.getMinPlayers();
	}

	public int getMaxPlayers() {
		return template.getMaxPlayers();
	}

	public int getAiAnimalCount() {
		return template.getAiAnimalCount();
	}

	// === 动态坐标拼装 ===
	private Location translate(Location configLoc) {
		if (configLoc == null || currentWorld == null) return null;
		return new Location(currentWorld, configLoc.getX(), configLoc.getY(), configLoc.getZ(), configLoc.getYaw(), configLoc.getPitch());
	}

	public Location getWaitingLobby() {
		return translate(template.getConfigWaitingLobby());
	}

	public Location getHiderSpawn() {
		return translate(template.getConfigHiderSpawn());
	}

	public Location getSeekerSpawn() {
		return translate(template.getConfigSeekerSpawn());
	}

	public List<Location> getAiSpawns() {
		if (template.getConfigAiSpawns() == null) return new ArrayList<>();
		return template.getConfigAiSpawns().stream().map(this::translate).toList();
	}

	public void addPlayer(Player player) {
		// 如果世界还没建好，先把玩家塞进名单，等建好了再传送 (由 GameManager 负责扫尾)
		players.add(player.getUniqueId());

		if (currentWorld != null && state == GameState.WAITING) {
			teleportAndInitPlayer(player);
		} else {
			player.sendMessage(Component.text("正在为您分配小游戏服务器资源，请稍候...", NamedTextColor.YELLOW));
		}
	}

	public void teleportAndInitPlayer(Player player) {
		player.teleportAsync(getWaitingLobby());
		broadcast(Component.text(player.getName() + " 加入了游戏! (" + players.size() + "/" + getMaxPlayers() + ")"));
		gameManager.resetPlayerDataWithoutLobby(player, this);
		giveLobbyItems(player);
		gameManager.updatePlayerVisibility(player);
		gameManager.checkAndStartCountdown(this);
	}

	public void addSpectator(Player player) {
		players.add(player.getUniqueId());
		spectators.add(player.getUniqueId());

		gameManager.resetPlayerDataWithoutLobby(player, this);
		player.setGameMode(org.bukkit.GameMode.ADVENTURE);
		player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false, false));

		Location spawn = getSeekerSpawn();
		if (spawn != null) player.teleportAsync(spawn);

		Bukkit.getScheduler().runTaskLater(AnimalHidePlugin.getInstance(), () -> {
			if (player.isOnline()) {
				player.setAllowFlight(true);
				player.setFlying(true);
			}
		}, 2L);

		ItemStack leaveItem = new ItemStack(Material.RED_BED);
		ItemMeta leaveMeta = leaveItem.getItemMeta();
		leaveMeta.displayName(Component.text("▶ 离开游戏 ◀", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
		leaveItem.setItemMeta(leaveMeta);
		player.getInventory().setItem(8, leaveItem);

		gameManager.updatePlayerVisibility(player);
		player.sendMessage(Component.text("你已作为旁观者加入！", NamedTextColor.AQUA));
	}

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

		// 如果房间已经没人了，直接摧毁房间释放资源
		if (players.isEmpty() && state != GameState.ENDING) {
			gameManager.destroyArenaMatch(this);
			return;
		}

		if (state == GameState.PLAYING) {
			if (wasHider && hiders.isEmpty()) gameManager.endGame(this, PlayerRole.SEEKER);
			else if (wasSeeker && seekers.isEmpty()) gameManager.endGame(this, PlayerRole.HIDER);
		} else if (state == GameState.STARTING && players.size() < getMinPlayers()) {
			this.state = GameState.WAITING;
		}
	}

	public void broadcast(Component message) {
		for (UUID uuid : players) {
			Player p = Bukkit.getPlayer(uuid);
			if (p != null) p.sendMessage(message);
		}
	}

	public int getModeVoteCount(ArenaMode mode) {
		return (int) modeVotes.values().stream().filter(m -> m == mode).count();
	}

	public int getRolePreferenceCount(PlayerRole role) {
		return (int) rolePreferences.values().stream().filter(r -> r == role).count();
	}

	public void addMatchScore(UUID uuid, int score) {
		matchScores.put(uuid, matchScores.getOrDefault(uuid, 0) + score);
	}

	public void addMatchKill(UUID uuid) {
		matchKills.put(uuid, matchKills.getOrDefault(uuid, 0) + 1);
	}

	public int getMatchKills(UUID uuid) {
		return matchKills.getOrDefault(uuid, 0);
	}
}