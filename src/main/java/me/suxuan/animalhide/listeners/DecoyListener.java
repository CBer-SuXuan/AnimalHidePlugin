package me.suxuan.animalhide.listeners;

import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.GameManager;
import me.suxuan.animalhide.game.GameState;
import me.suxuan.animalhide.manager.DecoyManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class DecoyListener implements Listener {

	private final GameManager gameManager;
	private final DecoyManager decoyManager;

	public DecoyListener(GameManager gameManager, DecoyManager decoyManager) {
		this.gameManager = gameManager;
		this.decoyManager = decoyManager;
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPlayerMove(PlayerMoveEvent event) {
		Player player = event.getPlayer();
		Arena arena = gameManager.getArenaByPlayer(player);
		if (arena == null || arena.getState() != GameState.PLAYING) return;
		if (!arena.getHiders().contains(player.getUniqueId())) return;

		Location anchor = arena.getDecoyAnchors().get(player.getUniqueId());
		if (anchor == null) return;

		if (event.getFrom().getBlockX() == event.getTo().getBlockX()
				&& event.getFrom().getBlockY() == event.getTo().getBlockY()
				&& event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
			return;
		}

		event.setTo(decoyManager.resolveAnchoredMoveTo(anchor, event.getTo()));
	}

	/**
	 * 须在伤害可能被取消之前处理（例如寻找者击杀会 cancel 伤害事件）。
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
	public void onDamage(EntityDamageEvent event) {
		if (!(event.getEntity() instanceof Player player)) return;

		Arena arena = gameManager.getArenaByPlayer(player);
		if (arena == null || arena.getState() != GameState.PLAYING) return;
		if (!arena.getHiders().contains(player.getUniqueId())) return;
		if (!decoyManager.isAnchored(arena, player.getUniqueId())) return;

		decoyManager.breakOnDamage(player, arena);
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		Arena arena = gameManager.getArenaByPlayer(player);
		if (arena != null) {
			decoyManager.clear(player, arena);
		}
	}
}
