package me.suxuan.animalhide.listeners;

import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.GameManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.UUID;

public class ChatListener implements Listener {

	private final GameManager gameManager;

	public ChatListener(GameManager gameManager) {
		this.gameManager = gameManager;
	}

	@EventHandler
	public void onPlayerChat(AsyncPlayerChatEvent event) {
		Player player = event.getPlayer();
		Arena arena = gameManager.getArenaByPlayer(player);

		// 如果玩家在房间内
		if (arena != null) {

			if (arena.getSpectators().contains(player.getUniqueId())) {
				event.setCancelled(true);
				player.sendMessage(net.kyori.adventure.text.Component.text("✘ 旁观模式下无法在局内发言！", net.kyori.adventure.text.format.NamedTextColor.RED));
				return;
			}

			event.getRecipients().clear();

			for (UUID uuid : arena.getPlayers()) {
				Player p = Bukkit.getPlayer(uuid);
				if (p != null) {
					event.getRecipients().add(p);
				}
			}

			String prefix = "§7[等待中] ";
			if (arena.getHiders().contains(player.getUniqueId())) {
				prefix = "§a[躲藏者] ";
			} else if (arena.getSeekers().contains(player.getUniqueId())) {
				prefix = "§c[寻找者] ";
			}

			event.setFormat(prefix + "§f%1$s: §7%2$s");
		} else {
			event.getRecipients().removeIf(p -> gameManager.getArenaByPlayer(p) != null);
		}
	}
}