package me.suxuan.animalhide.listeners;

import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.GameManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ConnectionListener implements Listener {

	private final GameManager gameManager;

	public ConnectionListener(GameManager gameManager) {
		this.gameManager = gameManager;
	}

	/**
	 * 处理中途断开连接的玩家
	 */
	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		Arena arena = gameManager.getArenaByPlayer(player);
		if (arena != null) {
			arena.removePlayer(player);
		}
	}

	/**
	 * 服务器崩溃抢救：检查刚进服的玩家是否遗留在了游戏地图中
	 */
	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();

		for (Arena arena : gameManager.getArenas().values()) {
			Location hiderSpawn = arena.getHiderSpawn();

			if (hiderSpawn != null && player.getWorld().equals(hiderSpawn.getWorld())) {

				if (gameManager.getArenaByPlayer(player) == null) {

					player.getInventory().clear();
					player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
					AnimalHidePlugin.getInstance().getDisguiseManager().undisguisePlayer(player);

					Location mainLobby = AnimalHidePlugin.getInstance().getConfigManager().getLocation(
							AnimalHidePlugin.getInstance().getConfigManager().getMainConfig().getConfigurationSection("main-lobby")
					);
					if (mainLobby != null) {
						player.teleportAsync(mainLobby);
					}

				}
				break;
			}
		}
	}
}
