package me.suxuan.animalhide.manager;

import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.GameManager;
import me.suxuan.animalhide.game.GameState;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 统一管理动物躲猫猫可见 BossBar。
 *
 * <p>右侧计分板被隐藏后，匹配、躲藏准备、正式对局、结算阶段的核心信息都通过这里渲染。
 * 每名玩家同一时间只会看到本插件的一条 BossBar，避免不同阶段的条互相叠加。</p>
 */
public class BossBarManager {

	private final AnimalHidePlugin plugin;
	private final GameManager gameManager;
	private final Map<UUID, BossBar> visibleBars = new HashMap<>();
	private final Map<Arena, Set<UUID>> arenaViewers = new HashMap<>();

	public BossBarManager(AnimalHidePlugin plugin, GameManager gameManager) {
		this.plugin = plugin;
		this.gameManager = gameManager;
	}

	public void showLobbyBar(Player player, Arena arena) {
		if (player == null || arena == null || !player.isOnline()) return;
		BossBar bar = visibleBars.computeIfAbsent(player.getUniqueId(), uuid -> createBar());
		show(player, arena, bar);
		updateLobbyBar(player, arena, bar);
	}

	public void refreshLobbyBar(Arena arena) {
		if (arena == null) return;
		forEachArenaPlayer(arena, player -> {
			BossBar bar = visibleBars.computeIfAbsent(player.getUniqueId(), uuid -> createBar());
			show(player, arena, bar);
			updateLobbyBar(player, arena, bar);
		});
	}

	public void showHidePhaseBar(Arena arena, int secondsLeft, int totalSeconds) {
		if (arena == null) return;
		forEachArenaPlayer(arena, player -> {
			BossBar bar = visibleBars.computeIfAbsent(player.getUniqueId(), uuid -> createBar());
			show(player, arena, bar);
			updateHidePhaseBar(player, arena, bar, secondsLeft, totalSeconds);
		});
	}

	public void showGameBar(Arena arena, int secondsLeft, int totalSeconds) {
		if (arena == null) return;
		forEachArenaPlayer(arena, player -> {
			BossBar bar = visibleBars.computeIfAbsent(player.getUniqueId(), uuid -> createBar());
			show(player, arena, bar);
			updateGameBar(player, arena, bar, secondsLeft, totalSeconds);
		});
	}

	public void showSettlementBar(Arena arena, int secondsLeft, int totalSeconds, String winnerName, BossBar.Color color) {
		if (arena == null) return;
		forEachArenaPlayer(arena, player -> {
			BossBar bar = visibleBars.computeIfAbsent(player.getUniqueId(), uuid -> createBar());
			show(player, arena, bar);
			bar.color(color == null ? BossBar.Color.WHITE : color);
			bar.overlay(BossBar.Overlay.PROGRESS);
			bar.progress(progress(secondsLeft, totalSeconds));
			bar.name(Component.text("🏆 " + winnerName + " | " + secondsLeft + " 秒后返回大厅", NamedTextColor.GOLD));
		});
	}

	public void hide(Player player) {
		if (player == null) return;
		UUID uuid = player.getUniqueId();
		BossBar bar = visibleBars.remove(uuid);
		if (bar != null) {
			player.hideBossBar(bar);
		}
		for (Set<UUID> viewers : arenaViewers.values()) {
			viewers.remove(uuid);
		}
	}

	public void clearArena(Arena arena) {
		Set<UUID> viewers = arenaViewers.remove(arena);
		if (viewers == null) return;
		for (UUID uuid : viewers) {
			Player player = Bukkit.getPlayer(uuid);
			BossBar bar = visibleBars.remove(uuid);
			if (player != null && bar != null) {
				player.hideBossBar(bar);
			}
		}
	}

	private void show(Player player, Arena arena, BossBar bar) {
		player.showBossBar(bar);
		arenaViewers.computeIfAbsent(arena, ignored -> new HashSet<>()).add(player.getUniqueId());
	}

	private BossBar createBar() {
		return BossBar.bossBar(Component.text("动物躲猫猫", NamedTextColor.YELLOW), 1.0f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
	}

	private void updateLobbyBar(Player player, Arena arena, BossBar bar) {
		int players = arena.getPlayers().size();
		int max = arena.getMaxPlayers();
		int min = arena.getMinPlayers();
		boolean queue = arena.getTemplate().isQueueRoom();

		bar.overlay(BossBar.Overlay.PROGRESS);
		if (arena.getState() == GameState.STARTING) {
			int total = getLobbyCountdownTotal(arena);
			int left = Math.max(1, arena.getTimeLeft());
			bar.progress(progress(left, total));
			bar.color(left <= 10 ? BossBar.Color.RED : arena.isLobbyFastCountdown() ? BossBar.Color.PURPLE : BossBar.Color.YELLOW);
			String prefix = queue ? "🎲 随机匹配中" : "🐾 " + arena.getArenaName();
			String action = queue ? " 秒后分配地图" : " 秒后开始";
			String fast = arena.isLobbyFastCountdown() ? "⚡ " : "";
			bar.name(Component.text(fast + prefix + " | " + left + action + " | 人数 " + players + "/" + max, NamedTextColor.YELLOW));
		} else {
			bar.progress(progress(Math.min(players, min), min));
			bar.color(queue ? BossBar.Color.BLUE : BossBar.Color.GREEN);
			String title = queue ? "🎲 随机匹配队列" : "🐾 " + arena.getArenaName();
			bar.name(Component.text(title + " | 等待玩家 " + players + "/" + max + " | 至少 " + min + " 人开始", queue ? NamedTextColor.AQUA : NamedTextColor.GREEN));
		}
	}

	private void updateHidePhaseBar(Player player, Arena arena, BossBar bar, int secondsLeft, int totalSeconds) {
		bar.overlay(BossBar.Overlay.PROGRESS);
		bar.progress(progress(secondsLeft, totalSeconds));
		if (arena.getSeekers().contains(player.getUniqueId())) {
			bar.color(BossBar.Color.RED);
			bar.name(Component.text("🔒 等待释放 | " + secondsLeft + " 秒后开始寻找 | 准备抓人！", NamedTextColor.RED));
		} else if (arena.getHiders().contains(player.getUniqueId())) {
			bar.color(BossBar.Color.GREEN);
			bar.name(Component.text("🐾 躲藏阶段 | 寻找者将在 " + secondsLeft + " 秒后出动 | 快藏好！", NamedTextColor.GREEN));
		} else {
			bar.color(BossBar.Color.BLUE);
			bar.name(Component.text("👁 旁观中 | 寻找者将在 " + secondsLeft + " 秒后出动", NamedTextColor.AQUA));
		}
	}

	private void updateGameBar(Player player, Arena arena, BossBar bar, int secondsLeft, int totalSeconds) {
		bar.progress(progress(secondsLeft, totalSeconds));
		boolean finalReveal = secondsLeft <= 30 || arena.isFinalRevealActive();
		bar.overlay(finalReveal ? BossBar.Overlay.NOTCHED_10 : BossBar.Overlay.PROGRESS);
		bar.color(finalReveal ? BossBar.Color.RED : secondsLeft <= 60 ? BossBar.Color.YELLOW : BossBar.Color.GREEN);

		String time = formatTime(secondsLeft);
		int hiders = arena.getHiders().size();
		int seekers = arena.getSeekers().size();
		UUID uuid = player.getUniqueId();

		if (finalReveal) {
			bar.name(Component.text("⚠ 最后 " + secondsLeft + " 秒！全员暴露 | 躲藏者 " + hiders + " | 寻找者 " + seekers, NamedTextColor.RED));
			return;
		}

		if (arena.getSeekers().contains(uuid)) {
			int kills = arena.getMatchKills(uuid);
			int level = GameManager.seekerLevelOf(kills);
			bar.name(Component.text("🔍 寻找者 | 剩余 " + time + " | 躲藏者 " + hiders + " | 寻找者 " + seekers + " | Lv." + level + " " + Math.min(kills, GameManager.MAX_SEEKER_LEVEL - 1) + "/" + (GameManager.MAX_SEEKER_LEVEL - 1), NamedTextColor.RED));
		} else if (arena.getHiders().contains(uuid)) {
			int hits = arena.getArrowHits().getOrDefault(uuid, 0);
			int level = hits / 5;
			bar.name(Component.text("🐾 躲藏者 | 剩余 " + time + " | 躲藏者 " + hiders + " | 寻找者 " + seekers + " | 弓 Lv." + level + " " + Math.min(hits, 15) + "/15", NamedTextColor.GREEN));
		} else {
			bar.name(Component.text("👁 旁观中 | 剩余 " + time + " | 躲藏者 " + hiders + " | 寻找者 " + seekers, NamedTextColor.AQUA));
		}
	}

	private int getLobbyCountdownTotal(Arena arena) {
		String key = arena.getTemplate().getConfigKey();
		if (arena.isLobbyFastCountdown()) {
			return Math.max(1, plugin.getConfigManager().getArenaConfigs().get(key).getInt("settings.fast-countdown-seconds", 20));
		}
		return Math.max(1, plugin.getConfigManager().getArenaConfigs().get(key).getInt("settings.countdown-seconds", 120));
	}

	private void forEachArenaPlayer(Arena arena, PlayerConsumer consumer) {
		for (UUID uuid : arena.getPlayers()) {
			Player player = Bukkit.getPlayer(uuid);
			if (player != null && player.isOnline()) {
				consumer.accept(player);
			}
		}
	}

	private float progress(int current, int total) {
		if (total <= 0) return 0f;
		return Math.max(0f, Math.min(1f, (float) current / total));
	}

	private String formatTime(int seconds) {
		int m = Math.max(0, seconds) / 60;
		int s = Math.max(0, seconds) % 60;
		return String.format("%02d:%02d", m, s);
	}

	@FunctionalInterface
	private interface PlayerConsumer {
		void accept(Player player);
	}
}
