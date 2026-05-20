package me.suxuan.animalhide.manager;

import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.Disguise;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.MobDisguise;
import me.libraryaddict.disguise.disguisetypes.watchers.CatWatcher;
import me.libraryaddict.disguise.disguisetypes.watchers.LivingWatcher;
import me.libraryaddict.disguise.disguisetypes.watchers.SheepWatcher;
import me.libraryaddict.disguise.disguisetypes.watchers.WolfWatcher;
import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.GameState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

/**
 * 变身管理器
 * 负责调用 LibsDisguises API 实现玩家的变身与复原
 */
public class DisguiseManager {

	private static final double DEFAULT_PLAYER_MOVE_SPEED = 0.1;
	private static final float DEFAULT_PLAYER_WALK_SPEED = 0.2f;

	private final AnimalHidePlugin plugin;
	private BukkitTask chickenFlapTask;

	public DisguiseManager(AnimalHidePlugin plugin) {
		this.plugin = plugin;
		startChickenFlapTask();
	}

	public void shutdown() {
		if (chickenFlapTask != null) {
			chickenFlapTask.cancel();
		}
	}

	/**
	 * 移除玩家的变身状态
	 */
	public void undisguisePlayer(Player player) {
		if (DisguiseAPI.isDisguised(player)) {
			DisguiseAPI.undisguiseToAll(player);
			resetMovement(player);
			player.sendMessage(Component.text("你的伪装已解除！", NamedTextColor.RED));
		}
	}

	/**
	 * 按当前伪装类型重新应用移速（解除定点、变身后调用）
	 */
	public void refreshMovementForDisguise(Player player) {
		if (DisguiseAPI.isDisguised(player)) {
			applyDisguiseMovement(player);
		} else {
			resetMovement(player);
		}
	}

	/** 伪装后统一使用玩家原版移速，不随动物种类变化 */
	public void applyDisguiseMovement(Player player) {
		resetMovement(player);
	}

	public void resetMovement(Player player) {
		AttributeInstance moveAttr = player.getAttribute(Attribute.MOVEMENT_SPEED);
		if (moveAttr != null) {
			moveAttr.setBaseValue(DEFAULT_PLAYER_MOVE_SPEED);
		}
		player.setWalkSpeed(DEFAULT_PLAYER_WALK_SPEED);
	}

	/**
	 * 伪装外观默认：水平朝向随玩家，俯仰锁定为 0（原版地面动物）
	 */
	public void applyDefaultDisguisePose(Player player) {
		Disguise disguise = DisguiseAPI.getDisguise(player);
		if (disguise == null) return;
		LivingWatcher watcher = (LivingWatcher) disguise.getWatcher();
		watcher.setYawLock(null);
		watcher.setPitchLock(0f);
		watcher.setAddEntityAnimations(true);
		watcher.rebuildWatchableObjects();
	}

	/**
	 * 定点伪装：锁定朝向并暂停实体动画（含鸡的扇翅）
	 */
	public void applyAnchoredDisguisePose(Player player, float yaw) {
		Disguise disguise = DisguiseAPI.getDisguise(player);
		if (disguise == null) return;
		LivingWatcher watcher = (LivingWatcher) disguise.getWatcher();
		watcher.setYawLock(yaw);
		watcher.setPitchLock(0f);
		watcher.setAddEntityAnimations(false);
		watcher.rebuildWatchableObjects();
	}

	private void giveDisguiseItemUI(Player player, DisguiseType type) {
		Material material = Material.matchMaterial(type.name() + "_SPAWN_EGG");
		if (material == null) {
			material = Material.SPAWNER;
		}

		Component localizedEntityName;
		try {
			EntityType entityType = EntityType.valueOf(type.name());
			localizedEntityName = Component.translatable(entityType.translationKey(), NamedTextColor.GREEN);
		} catch (IllegalArgumentException e) {
			localizedEntityName = Component.text(type.name(), NamedTextColor.GREEN);
		}

		ItemStack uiItem = new ItemStack(material);
		ItemMeta meta = uiItem.getItemMeta();

		meta.displayName(Component.text("▶ 你的当前伪装: ", NamedTextColor.GRAY)
				.append(localizedEntityName)
				.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));

		meta.lore(List.of(
				Component.empty(),
				Component.text("这就是你现在的样子！", NamedTextColor.YELLOW)
						.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
				Component.text("请融入环境，不要被寻找者发现。", NamedTextColor.GRAY)
						.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
		));

		uiItem.setItemMeta(meta);
		player.getInventory().setItem(7, uiItem);
	}

	public void disguisePlayerAsEntity(Player player, Entity targetEntity) {
		DisguiseType type = DisguiseType.getType(targetEntity.getType());
		if (!type.isMob()) {
			plugin.getComponentLogger().warn("尝试将玩家变为非生物类型: {}", type.name());
			return;
		}

		MobDisguise disguise = new MobDisguise(type);

		disguise.setViewSelfDisguise(true);
		disguise.setHideArmorFromSelf(false);
		disguise.setHideHeldItemFromSelf(false);
		disguise.setHearSelfDisguise(false);
		disguise.setVelocitySent(true);

		LivingWatcher watcher = disguise.getWatcher();
		watcher.setGlowing(false);
		watcher.setCustomNameVisible(false);

		switch (targetEntity) {
			case Sheep sheepTarget when watcher instanceof SheepWatcher sheepWatcher ->
					sheepWatcher.setColor(sheepTarget.getColor());

			case Wolf wolfTarget when watcher instanceof WolfWatcher wolfWatcher -> {
				try {
					wolfWatcher.setVariant(wolfTarget.getVariant());
					if (wolfTarget.isTamed()) {
						wolfWatcher.setCollarColor(wolfTarget.getCollarColor());
					}
				} catch (Throwable ignored) {
				}
			}

			case Cat catTarget when watcher instanceof CatWatcher catWatcher -> {
				try {
					catWatcher.setType(catTarget.getCatType());
				} catch (Throwable ignored) {
				}
			}

			case Pig pigTarget when watcher instanceof me.libraryaddict.disguise.disguisetypes.watchers.PigWatcher pigWatcher ->
					pigWatcher.setSaddled(pigTarget.hasSaddle());
			default -> {
			}
		}

		DisguiseAPI.disguiseToAll(player, disguise);
		applyDefaultDisguisePose(player);
		applyDisguiseMovement(player);
	}

	/**
	 * LibsDisguises 对玩家伪装不会跑鸡的 tick，扇翅依赖实体动画包。
	 * 定期 rebuild 并在非定点时保持动画开启，尽量贴近场景 AI 鸡。
	 */
	private void startChickenFlapTask() {
		chickenFlapTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
			for (Arena arena : plugin.getGameManager().getActiveMatches()) {
				if (arena.getState() != GameState.PLAYING) continue;
				for (java.util.UUID hiderId : arena.getHiders()) {
					Player hider = Bukkit.getPlayer(hiderId);
					if (hider == null || !DisguiseAPI.isDisguised(hider)) continue;
					if (arena.getDecoyAnchors().containsKey(hiderId)) continue;

					Disguise disguise = DisguiseAPI.getDisguise(hider);
					if (disguise == null || disguise.getType() != DisguiseType.CHICKEN) continue;

					LivingWatcher watcher = (LivingWatcher) disguise.getWatcher();
					watcher.setAddEntityAnimations(true);
					watcher.rebuildWatchableObjects();
				}
			}
		}, 4L, 4L);
	}
}
