package me.suxuan.animalhide.game.runtime;

import me.libraryaddict.disguise.DisguiseAPI;
import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.config.ConfigManager;
import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.GameState;
import me.suxuan.animalhide.manager.DisguiseManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class PlayerStateService {

	private final AnimalHidePlugin plugin;
	private final ConfigManager configManager;
	private final DisguiseManager disguiseManager;
	private final Location mainLobby;

	public PlayerStateService(AnimalHidePlugin plugin, ConfigManager configManager, DisguiseManager disguiseManager, Location mainLobby) {
		this.plugin = plugin;
		this.configManager = configManager;
		this.disguiseManager = disguiseManager;
		this.mainLobby = mainLobby;
	}

	public void resetPlayerData(Player player, Arena arena) {
		if (player == null) return;
		resetPlayerDataWithoutLobby(player, arena);
		if (mainLobby != null) {
			player.teleportAsync(mainLobby);
		}
	}

	public void resetPlayerDataWithoutLobby(Player player, Arena arena) {
		if (player == null) return;

		if (arena != null && arena.getTimeBar() != null) {
			player.hideBossBar(arena.getTimeBar());
		}

		if (arena != null) {
			plugin.getDecoyManager().clear(player, arena);
		}
		if (DisguiseAPI.isDisguised(player)) {
			DisguiseAPI.undisguiseToAll(player);
		}
		disguiseManager.resetMovement(player);

		player.setAllowFlight(false);
		player.setFlying(false);
		player.resetPlayerTime();
		player.getInventory().clear();
		player.setGameMode(arena == null ? GameMode.SURVIVAL : GameMode.ADVENTURE);
		player.setHealth(20.0);
		player.setFoodLevel(20);
		player.setFireTicks(0);
		player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
		player.setCollidable(true);
	}

	public void emergencyCleanup(List<Arena> activeMatches, Consumer<Arena> destroyArenaMatch) {
		Location lobby = configManager.getLocation(configManager.getMainConfig().getConfigurationSection("main-lobby"));

		for (Arena arena : new ArrayList<>(activeMatches)) {
			if (arena.getState() == GameState.WAITING) continue;

			for (UUID uuid : arena.getPlayers()) {
				Player player = Bukkit.getPlayer(uuid);
				if (player == null) continue;

				disguiseManager.undisguisePlayer(player);
				player.getInventory().clear();
				player.setHealth(20.0);
				player.setFoodLevel(20);
				player.setFireTicks(0);
				player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));

				if (lobby != null) {
					player.teleport(lobby);
				}
			}
			destroyArenaMatch.accept(arena);
		}
	}
}
