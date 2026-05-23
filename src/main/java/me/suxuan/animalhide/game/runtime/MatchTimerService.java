package me.suxuan.animalhide.game.runtime;

import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.config.ConfigManager;
import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.GameManager;
import me.suxuan.animalhide.game.GameState;
import me.suxuan.animalhide.game.ScoringConfig;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Consumer;

public class MatchTimerService {

	private final AnimalHidePlugin plugin;
	private final ConfigManager configManager;
	private final Consumer<Arena> hiderWinCallback;

	public MatchTimerService(AnimalHidePlugin plugin, ConfigManager configManager, Consumer<Arena> hiderWinCallback) {
		this.plugin = plugin;
		this.configManager = configManager;
		this.hiderWinCallback = hiderWinCallback;
	}

	public void startHidePhaseTask(Arena arena, int hideTimeSeconds) {
		new BukkitRunnable() {
			int timeLeft = hideTimeSeconds;

			@Override
			public void run() {
				if (arena.getState() != GameState.PLAYING) {
					cancel();
					return;
				}

				if (timeLeft > 0) {
					arena.setTimeLeft(timeLeft);
					sendHidePhaseActionBars(arena, timeLeft);
					timeLeft--;
				} else {
					releaseSeekers(arena);
					cancel();

					int gameDuration = configManager.getArenaConfigs()
							.get(arena.getArenaName())
							.getInt("settings.game-duration", 300);
					startGameTimer(arena, gameDuration);
				}
			}
		}.runTaskTimer(plugin, 0L, 20L);
	}

	private void sendHidePhaseActionBars(Arena arena, int timeLeft) {
		Component seekerText = Component.text("距离释放还有: " + timeLeft + " 秒", NamedTextColor.RED);
		for (UUID id : arena.getSeekers()) {
			Player p = Bukkit.getPlayer(id);
			if (p != null) p.sendActionBar(seekerText);
		}

		Component hiderText = Component.text("寻找者将在 " + timeLeft + " 秒后出动！", NamedTextColor.YELLOW);
		for (UUID id : arena.getHiders()) {
			Player p = Bukkit.getPlayer(id);
			if (p != null) p.sendActionBar(hiderText);
		}
	}

	private void releaseSeekers(Arena arena) {
		arena.openPhaseDoors();
		arena.broadcast(Component.text("⚔ 寻找者已出动！", NamedTextColor.DARK_RED));

		for (UUID seekerId : arena.getSeekers()) {
			Player seeker = Bukkit.getPlayer(seekerId);
			if (seeker == null) continue;

			plugin.getGameManager().getRoleSetupService().applySeekerReleasedAttributes(seeker);

			seeker.showTitle(Title.title(
					Component.text("开始寻找！", NamedTextColor.RED),
					Component.text("找出所有动物！", NamedTextColor.YELLOW),
					Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(200))
			));
		}
	}

	public void startGameTimer(Arena arena, int durationSeconds) {
		BossBar timeBar = BossBar.bossBar(
				Component.text("⏳ 游戏剩余时间: " + durationSeconds + " 秒", NamedTextColor.WHITE),
				1.0f,
				BossBar.Color.RED,
				BossBar.Overlay.PROGRESS
		);

		arena.setTimeBar(timeBar);
		for (UUID uuid : arena.getPlayers()) {
			Player p = Bukkit.getPlayer(uuid);
			if (p != null) p.showBossBar(timeBar);
		}

		ScoringConfig scoring = arena.getTemplate().getScoring();
		int survivalReward = scoring.getHiderSurvivalReward();
		int survivalInterval = scoring.getHiderSurvivalInterval();

		new BukkitRunnable() {
			int timeLeft = durationSeconds;

			@Override
			public void run() {
				if (arena.getState() != GameState.PLAYING) {
					cancel();
					return;
				}

				if (timeLeft > 0) {
					arena.setTimeLeft(timeLeft);
					updateBossBar(timeBar, timeLeft, durationSeconds);
					int elapsed = durationSeconds - timeLeft;
					applySurvivalReward(arena, survivalReward, survivalInterval, elapsed);
					replenishHiderArrows(arena, elapsed);
					timeLeft--;
				} else {
					hiderWinCallback.accept(arena);
					cancel();
				}
			}
		}.runTaskTimer(plugin, 0L, 20L);
	}

	private void updateBossBar(BossBar timeBar, int timeLeft, int durationSeconds) {
		float progress = (float) timeLeft / durationSeconds;
		timeBar.progress(progress);
		if (timeLeft <= 30) {
			timeBar.name(Component.text("⏳ 游戏剩余时间: " + timeLeft + " 秒", NamedTextColor.RED));
		} else {
			timeBar.name(Component.text("⏳ 游戏剩余时间: " + timeLeft + " 秒", NamedTextColor.WHITE));
		}
	}

	private void applySurvivalReward(Arena arena, int survivalReward, int survivalInterval, int elapsed) {
		if (survivalReward <= 0 || elapsed <= 0 || elapsed % survivalInterval != 0) return;
		for (UUID hiderId : arena.getHiders()) {
			arena.addMatchScore(hiderId, survivalReward);
			Player hider = Bukkit.getPlayer(hiderId);
			if (hider != null) {
				hider.sendActionBar(Component.text("✔ 潜行存活奖励: 积分 +" + survivalReward, NamedTextColor.GREEN));
			}
		}
	}

	private void replenishHiderArrows(Arena arena, int elapsed) {
		if (elapsed <= 0 || elapsed % 5 != 0) return;
		for (UUID hiderId : arena.getHiders()) {
			Player hider = Bukkit.getPlayer(hiderId);
			if (hider == null) continue;

			ItemStack arrowItem = hider.getInventory().getItem(8);
			if (arrowItem == null || arrowItem.getType() == Material.AIR) {
				hider.getInventory().setItem(8, new ItemStack(Material.ARROW, 1));
			} else if (arrowItem.getType() == Material.ARROW && arrowItem.getAmount() < 5) {
				arrowItem.setAmount(arrowItem.getAmount() + 1);
			}
		}
	}
}
