package me.suxuan.animalhide.listeners;

import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Particle;
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

		Location mainLobby = AnimalHidePlugin.getInstance().getConfigManager().getLocation(
				AnimalHidePlugin.getInstance().getConfigManager().getMainConfig().getConfigurationSection("main-lobby"));

		for (Arena arena : gameManager.getArenas().values()) {
			Location hiderSpawn = arena.getHiderSpawn();

			if (hiderSpawn != null && player.getWorld().equals(hiderSpawn.getWorld())) {

				if (gameManager.getArenaByPlayer(player) == null) {

					player.getInventory().clear();
					player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
					AnimalHidePlugin.getInstance().getDisguiseManager().undisguisePlayer(player);
					if (mainLobby != null) {
						player.teleportAsync(mainLobby);
					}
					return;

				}
				break;
			}
		}

		// 修改全服进服广播
		event.joinMessage(
				Component.text("⚡ ", NamedTextColor.YELLOW)
						.append(Component.text(player.getName(), NamedTextColor.AQUA))
						.append(Component.text(" 闪亮登场！加入躲猫猫大厅！", NamedTextColor.GRAY))
		);

		// 播放进服升级音效
		player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

		// 发送屏幕中央的大标题
		player.showTitle(Title.title(
				Component.text("躲猫猫小游戏", NamedTextColor.GOLD, TextDecoration.BOLD),
				Component.text("欢迎来到服务器，快去寻找伪装吧！", NamedTextColor.WHITE)
		));

		// 在玩家周围生成不死图腾爆发粒子特效
		player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 100, 0.5, 1, 0.5, 0.1);

		if (mainLobby != null) {
			player.teleportAsync(mainLobby);
		}

		AnimalHidePlugin.getInstance().getServer().getScheduler().runTaskLater(
				AnimalHidePlugin.getInstance(),
				() -> gameManager.updatePlayerVisibility(player),
				1L
		);
	}
}
