package me.suxuan.animalhide.manager;

import me.libraryaddict.disguise.DisguiseAPI;
import me.suxuan.animalhide.game.Arena;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * 躲藏者「定点伪装」：锁定当前位置与伪装朝向，玩家仅可转动视角，可使用嘲讽但不可射箭。
 */
public class DecoyManager {

	private final DisguiseManager disguiseManager;

	public DecoyManager(DisguiseManager disguiseManager) {
		this.disguiseManager = disguiseManager;
	}

	public boolean isAnchored(Arena arena, UUID playerId) {
		return arena.getDecoyAnchors().containsKey(playerId);
	}

	public Location getAnchorOrPlayerLocation(Player player, Arena arena) {
		Location anchor = arena.getDecoyAnchors().get(player.getUniqueId());
		return anchor != null ? anchor : player.getLocation();
	}

	public boolean toggle(Player player, Arena arena) {
		if (isAnchored(arena, player.getUniqueId())) {
			deactivate(player, arena);
			return false;
		}
		if (!DisguiseAPI.isDisguised(player)) {
			player.sendMessage(Component.text("✘ 你需要先变身成动物才能使用定点伪装！", NamedTextColor.RED));
			return false;
		}
		activate(player, arena);
		return true;
	}

	public void activate(Player player, Arena arena) {
		if (player.isFlying() || player.isGliding()) {
			player.sendMessage(Component.text("✘ 空中无法使用定点伪装！", NamedTextColor.RED));
			return;
		}
		if (player.isInWater() || player.isInLava()) {
			player.sendMessage(Component.text("✘ 不能在水中或熔岩里使用定点伪装！", NamedTextColor.RED));
			return;
		}
		if (!player.isOnGround()) {
			player.sendMessage(Component.text("✘ 必须站在地面上才能使用定点伪装！", NamedTextColor.RED));
			return;
		}

		UUID uuid = player.getUniqueId();
		Location anchor = player.getLocation().clone();
		arena.getDecoyAnchors().put(uuid, anchor);

		saveMovement(player, arena);

		player.setWalkSpeed(0f);
		zeroMovementAttributes(player);
		disguiseManager.applyAnchoredDisguisePose(player, anchor.getYaw());

		player.sendMessage(Component.text("✔ 定点伪装已开启：你固定在原地，可转动视角并使用嘲讽，无法移动或射箭。", NamedTextColor.GREEN));
		player.sendActionBar(Component.text("定点伪装中 — 再次右键道具可解除", NamedTextColor.AQUA));
	}

	public void deactivate(Player player, Arena arena) {
		deactivate(player, arena, Component.text("✔ 已解除定点伪装，可以移动了。", NamedTextColor.YELLOW));
	}

	public void deactivate(Player player, Arena arena, Component message) {
		if (!isAnchored(arena, player.getUniqueId())) return;

		UUID uuid = player.getUniqueId();
		arena.getDecoyAnchors().remove(uuid);
		restoreMovement(player, arena);
		disguiseManager.applyDefaultDisguisePose(player);

		player.sendMessage(message);
	}

	/** 受到伤害时强制解除定点伪装 */
	public void breakOnDamage(Player player, Arena arena) {
		deactivate(player, arena, Component.text("⚠ 受到攻击，定点伪装已解除！", NamedTextColor.RED));
	}

	public void clear(Player player, Arena arena) {
		if (!isAnchored(arena, player.getUniqueId())) return;
		arena.getDecoyAnchors().remove(player.getUniqueId());
		arena.getDecoySavedWalkSpeed().remove(player.getUniqueId());
		arena.getDecoySavedMoveSpeed().remove(player.getUniqueId());
		restoreMovementAttributes(player);
		if (DisguiseAPI.isDisguised(player)) {
			disguiseManager.applyDefaultDisguisePose(player);
			disguiseManager.refreshMovementForDisguise(player);
		}
	}

	private void saveMovement(Player player, Arena arena) {
		UUID uuid = player.getUniqueId();
		arena.getDecoySavedWalkSpeed().put(uuid, player.getWalkSpeed());
		AttributeInstance moveAttr = player.getAttribute(Attribute.MOVEMENT_SPEED);
		if (moveAttr != null) {
			arena.getDecoySavedMoveSpeed().put(uuid, moveAttr.getBaseValue());
		}
	}

	private void restoreMovement(Player player, Arena arena) {
		UUID uuid = player.getUniqueId();
		Float savedWalk = arena.getDecoySavedWalkSpeed().remove(uuid);
		Double savedMove = arena.getDecoySavedMoveSpeed().remove(uuid);

		restoreMovementAttributes(player);

		if (DisguiseAPI.isDisguised(player)) {
			disguiseManager.refreshMovementForDisguise(player);
		} else {
			if (savedMove != null) {
				AttributeInstance moveAttr = player.getAttribute(Attribute.MOVEMENT_SPEED);
				if (moveAttr != null) moveAttr.setBaseValue(savedMove);
			}
			if (savedWalk != null) {
				player.setWalkSpeed(savedWalk);
			} else {
				disguiseManager.resetMovement(player);
			}
		}
	}

	private void zeroMovementAttributes(Player player) {
		player.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0);
		player.getAttribute(Attribute.SNEAKING_SPEED).setBaseValue(0);
		player.getAttribute(Attribute.JUMP_STRENGTH).setBaseValue(0);
	}

	private void restoreMovementAttributes(Player player) {
		player.getAttribute(Attribute.SNEAKING_SPEED).setBaseValue(0.3);
		player.getAttribute(Attribute.JUMP_STRENGTH).setBaseValue(0.42);
	}

	/**
	 * 计算定点伪装下允许到达的位置：锚点坐标不变，仅同步玩家视角朝向。
	 * 必须在 {@link org.bukkit.event.player.PlayerMoveEvent} 里通过 {@code setTo} 使用，不要 teleport。
	 */
	public Location resolveAnchoredMoveTo(Location anchor, Location lookSource) {
		Location fixed = anchor.clone();
		fixed.setYaw(lookSource.getYaw());
		fixed.setPitch(lookSource.getPitch());
		return fixed;
	}
}
