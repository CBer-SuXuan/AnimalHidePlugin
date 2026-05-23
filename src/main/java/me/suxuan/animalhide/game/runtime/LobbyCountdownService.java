package me.suxuan.animalhide.game.runtime;

import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.config.ConfigManager;
import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.GameState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Consumer;

public class LobbyCountdownService {

	private final AnimalHidePlugin plugin;
	private final ConfigManager configManager;
	private final Consumer<Arena> startGameCallback;

	public LobbyCountdownService(AnimalHidePlugin plugin, ConfigManager configManager, Consumer<Arena> startGameCallback) {
		this.plugin = plugin;
		this.configManager = configManager;
		this.startGameCallback = startGameCallback;
	}

	public void checkAndStartCountdown(Arena arena) {
		if (arena.getState() == GameState.WAITING && arena.getPlayers().size() >= arena.getMinPlayers()) {
			arena.setState(GameState.STARTING);
			beginLobbyCountdown(arena);
		} else if (arena.getState() == GameState.STARTING) {
			refreshLobbyCountdown(arena);
		}
	}

	public void refreshLobbyCountdown(Arena arena) {
		if (arena.getState() != GameState.STARTING) return;

		int size = arena.getPlayers().size();
		if (size < arena.getMinPlayers()) {
			cancelLobbyCountdown(arena);
			arena.setState(GameState.WAITING);
			arena.setTimeLeft(0);
			arena.broadcast(Component.text("人数不足，取消倒计时...", NamedTextColor.RED));
			return;
		}

		boolean fast = shouldUseFastLobbyCountdown(arena);
		if (fast == arena.isLobbyFastCountdown() && arena.getLobbyCountdownTask() != null) {
			return;
		}

		if (fast) {
			int pct = (int) Math.round(getFastStartPercent(arena) * 100);
			arena.broadcast(Component.text("房间人数已达上限的 " + pct + "% ，", NamedTextColor.GOLD)
					.append(Component.text(getFastCountdownSeconds(arena) + " 秒后开始游戏！", NamedTextColor.GREEN)));
		} else if (arena.isLobbyFastCountdown()) {
			arena.broadcast(Component.text("人数下降，重新进入 ", NamedTextColor.YELLOW)
					.append(Component.text(getCountdownSeconds(arena) + " 秒", NamedTextColor.AQUA))
					.append(Component.text(" 等待倒计时...", NamedTextColor.YELLOW)));
		}
		restartLobbyCountdown(arena, fast);
	}

	public void cancelLobbyCountdown(Arena arena) {
		BukkitTask task = arena.getLobbyCountdownTask();
		if (task != null) {
			task.cancel();
			arena.setLobbyCountdownTask(null);
		}
		arena.setLobbyFastCountdown(false);
	}

	private void beginLobbyCountdown(Arena arena) {
		boolean fast = shouldUseFastLobbyCountdown(arena);
		if (fast) {
			int pct = (int) Math.round(getFastStartPercent(arena) * 100);
			arena.broadcast(Component.text("房间人数已达上限的 " + pct + "% ，", NamedTextColor.GOLD)
					.append(Component.text(getFastCountdownSeconds(arena) + " 秒后开始游戏！", NamedTextColor.GREEN)));
		} else {
			arena.broadcast(Component.text("已达到最少人数，游戏将在 ", NamedTextColor.GREEN)
					.append(Component.text(formatLobbyDuration(getCountdownSeconds(arena)), NamedTextColor.AQUA))
					.append(Component.text(" 后开始", NamedTextColor.GREEN)));
		}
		restartLobbyCountdown(arena, fast);
	}

	private void restartLobbyCountdown(Arena arena, boolean fast) {
		cancelLobbyCountdown(arena);
		arena.setLobbyFastCountdown(fast);
		int totalSeconds = fast ? getFastCountdownSeconds(arena) : getCountdownSeconds(arena);

		BukkitTask task = new BukkitRunnable() {
			int countdown = totalSeconds;

			@Override
			public void run() {
				if (arena.getState() != GameState.STARTING) {
					cancel();
					return;
				}
				if (arena.getPlayers().size() < arena.getMinPlayers()) {
					Bukkit.getScheduler().runTask(plugin, () -> refreshLobbyCountdown(arena));
					cancel();
					return;
				}

				boolean wantFast = shouldUseFastLobbyCountdown(arena);
				if (wantFast != arena.isLobbyFastCountdown()) {
					Bukkit.getScheduler().runTask(plugin, () -> refreshLobbyCountdown(arena));
					cancel();
					return;
				}

				if (countdown > 0) {
					arena.setTimeLeft(countdown);
					maybeShowLobbyCountdownTitle(arena, countdown, fast);
					countdown--;
				} else {
					arena.setLobbyCountdownTask(null);
					startGameCallback.accept(arena);
					cancel();
				}
			}
		}.runTaskTimer(plugin, 0L, 20L);
		arena.setLobbyCountdownTask(task);
	}

	private void maybeShowLobbyCountdownTitle(Arena arena, int secondsLeft, boolean fast) {
		boolean showEverySecond = fast || secondsLeft <= 10;
		boolean showMilestone = !fast && secondsLeft > 10 && (secondsLeft % 30 == 0 || secondsLeft == getCountdownSeconds(arena));
		if (!showEverySecond && !showMilestone) return;

		Component main = showEverySecond
				? Component.text(secondsLeft, NamedTextColor.AQUA)
				: Component.text(formatLobbyDuration(secondsLeft), NamedTextColor.AQUA);
		Component sub = fast
				? Component.text("即将开始", NamedTextColor.YELLOW)
				: Component.text("游戏即将开始", NamedTextColor.YELLOW);

		Title title = Title.title(main, sub,
				Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(1), Duration.ofMillis(200)));
		for (UUID uuid : arena.getPlayers()) {
			Player p = Bukkit.getPlayer(uuid);
			if (p != null) p.showTitle(title);
		}
	}

	private boolean shouldUseFastLobbyCountdown(Arena arena) {
		int threshold = getFastStartThreshold(arena);
		return arena.getPlayers().size() >= threshold;
	}

	private int getFastStartThreshold(Arena arena) {
		double percent = getFastStartPercent(arena);
		int threshold = (int) Math.ceil(arena.getMaxPlayers() * percent);
		return Math.max(arena.getMinPlayers(), Math.min(threshold, arena.getMaxPlayers()));
	}

	private int getCountdownSeconds(Arena arena) {
		return Math.max(1, configManager.getArenaConfigs()
				.get(arena.getArenaName())
				.getInt("settings.countdown-seconds", 120));
	}

	private int getFastCountdownSeconds(Arena arena) {
		return Math.max(1, configManager.getArenaConfigs()
				.get(arena.getArenaName())
				.getInt("settings.fast-countdown-seconds", 20));
	}

	private double getFastStartPercent(Arena arena) {
		return Math.clamp(configManager.getArenaConfigs()
				.get(arena.getArenaName())
				.getDouble("settings.fast-start-percent", 0.8), 0.0, 1.0);
	}

	private static String formatLobbyDuration(int seconds) {
		if (seconds >= 60 && seconds % 60 == 0) {
			return (seconds / 60) + " 分钟";
		}
		if (seconds >= 60) {
			return (seconds / 60) + " 分 " + (seconds % 60) + " 秒";
		}
		return seconds + " 秒";
	}
}
