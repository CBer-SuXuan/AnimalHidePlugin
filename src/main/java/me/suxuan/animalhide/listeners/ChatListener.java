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
			// 1. 清空默认的所有接收者（全服玩家）
			event.getRecipients().clear();

			// 2. 只把当前房间内的玩家加回接收者列表
			for (UUID uuid : arena.getPlayers()) {
				Player p = Bukkit.getPlayer(uuid);
				if (p != null) {
					event.getRecipients().add(p);
				}
			}

			// 3. 动态配置阵营前缀
			String prefix = "§7[等待中] ";
			if (arena.getHiders().contains(player.getUniqueId())) {
				prefix = "§a[躲藏者] ";
			} else if (arena.getSeekers().contains(player.getUniqueId())) {
				prefix = "§c[寻找者] ";
			}

			// 4. 修改聊天格式
			// %1$s 是玩家名，%2$s 是消息内容
			event.setFormat(prefix + "§f%1$s: §7%2$s");
		}
		// 如果玩家不在房间里（在大厅），你可以选择让他们只能和同样在大厅的人聊天
		else {
			event.getRecipients().removeIf(p -> gameManager.getArenaByPlayer(p) != null);
		}
	}
}