package me.suxuan.animalhide.listeners;

import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Sound;
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
		event.quitMessage(null);
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
		event.joinMessage(null);
		Player player = event.getPlayer();

		Location mainLobby = AnimalHidePlugin.getInstance().getConfigManager().getLocation(
				AnimalHidePlugin.getInstance().getConfigManager().getMainConfig().getConfigurationSection("main-lobby"));

		// 仍在房间名单内（断线未触发 Quit 等）：视为弃局，不送回对局
		Arena activeArena = gameManager.getArenaByPlayer(player);
		if (activeArena != null) {
			activeArena.removePlayer(player, false);
			player.sendMessage(Component.text("你之前已离开房间，不会恢复到旧房间。", NamedTextColor.YELLOW));
		}

		for (Arena arena : gameManager.getActiveMatches()) {
			Location hiderSpawn = arena.getHiderSpawn();

			if (hiderSpawn != null && player.getWorld().equals(hiderSpawn.getWorld())) {

				player.getInventory().clear();
				player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
				AnimalHidePlugin.getInstance().getDisguiseManager().undisguisePlayer(player);
				if (mainLobby != null) {
					player.teleportAsync(mainLobby);
				}
				return;
			}
		}

		// 播放进服升级音效
		player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

		// 发送屏幕中央的大标题
		player.showTitle(Title.title(
				Component.text("躲猫猫小游戏", NamedTextColor.GOLD, TextDecoration.BOLD),
				Component.text("欢迎来到躲猫猫，快去寻找伪装吧！", NamedTextColor.WHITE)
		));

		if (mainLobby != null) {
			player.teleportAsync(mainLobby);
		}

		AnimalHidePlugin.getInstance().getServer().getScheduler().runTaskLater(AnimalHidePlugin.getInstance(), () ->
				gameManager.autoJoinQueue(player), 2L);

	}
}
