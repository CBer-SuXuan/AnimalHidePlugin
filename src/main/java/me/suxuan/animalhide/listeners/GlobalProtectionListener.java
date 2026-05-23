package me.suxuan.animalhide.listeners;

import io.papermc.paper.event.player.PlayerFlowerPotManipulateEvent;
import io.papermc.paper.event.player.PlayerOpenSignEvent;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

/**
 * 全局防护监听器：在「所有世界」对玩家强制执行的安全规则。
 * <p>
 * 设计原则：
 * 1. 仅否决「方块/实体的交互结果」，不影响玩家手上物品的使用（避免误伤嘲讽道具、菜单物品）。
 * 2. 创造模式玩家 / 拥有 {@code animalhide.bypass.interact} 权限的玩家全部豁免，便于管理员搭建与调试。
 * 3. 一切「房间内特有」的规则放在 {@link GameRuleListener}，这里只做与游戏房间无关的通用保护。
 */
public class GlobalProtectionListener implements Listener {

	// ==================================================================================
	// SECTION 1：可交互方块（门 / 活板门 / 栅栏门 / 按钮 / 拉杆 / 告示牌 / 潜影盒）
	// ==================================================================================

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
		if (shouldBypass(event.getPlayer())) return;

		Block block = event.getClickedBlock();
		if (block == null) return;

		if (isProtectedPot(block.getType())) {
			denyPotInteraction(event);
			return;
		}
		if (isProtectedInteractable(block.getType())) {
			event.setUseInteractedBlock(Event.Result.DENY);
		}
	}

	/**
	 * 禁止往花盆 / 饰纹陶罐放入或取出物品（Paper 专用事件，覆盖普通交互拦截遗漏的情况）。
	 */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onFlowerPotManipulate(PlayerFlowerPotManipulateEvent event) {
		if (shouldBypass(event.getPlayer())) return;
		event.setCancelled(true);
	}

	/**
	 * 防止玩家踩踏耕地把农作物变回泥土。
	 */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onFarmlandTrample(PlayerInteractEvent event) {
		if (event.getAction() != Action.PHYSICAL) return;
		if (shouldBypass(event.getPlayer())) return;

		Block block = event.getClickedBlock();
		if (block != null && block.getType() == Material.FARMLAND) {
			event.setCancelled(true);
		}
	}

	// ==================================================================================
	// SECTION 2：实体装饰（盔甲架 / 画框 / 展示框）
	// ==================================================================================

	/**
	 * 禁止右键盔甲架穿戴 / 取下 / 交换装备。
	 */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
		if (shouldBypass(event.getPlayer())) return;
		event.setCancelled(true);
	}

	/**
	 * 防止玩家左键攻击装饰类实体：
	 * - 盔甲架被打一拳会掉装备，再打一拳会碎；
	 * - 展示框被打一拳会掉里面的物品；
	 * - 画 / 展示框作为 {@link Hanging} 实体也走这里。
	 */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onDecorativeDamage(EntityDamageByEntityEvent event) {
		if (!(event.getDamager() instanceof Player player)) return;
		if (shouldBypass(player)) return;

		if (event.getEntity() instanceof ArmorStand
				|| event.getEntity() instanceof Hanging
				|| event.getEntity() instanceof Display) {
			event.setCancelled(true);
		}
	}

	/**
	 * 防止玩家拳碎画框 / 展示框（也兼顾被玩家骑乘的矿车撞掉等边界场景）。
	 */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onHangingBreak(HangingBreakByEntityEvent event) {
		if (!(event.getRemover() instanceof Player player)) return;
		if (shouldBypass(player)) return;

		event.setCancelled(true);
	}

	/**
	 * 禁止对生物使用栓绳（在交互阶段取消，避免仅取消 Leash 事件时仍消耗栓绳）。
	 * 局内躲藏者的栓绳道具仅用于对空气/方块右键的定点伪装，见 {@link InteractionListener}。
	 */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onLeadInteractEntity(PlayerInteractEntityEvent event) {
		if (shouldBypass(event.getPlayer())) return;
		if (!(event.getRightClicked() instanceof LivingEntity)) return;

		ItemStack item = event.getHand() == EquipmentSlot.HAND
				? event.getPlayer().getInventory().getItemInMainHand()
				: event.getPlayer().getInventory().getItemInOffHand();
		if (item.getType() == Material.LEAD) {
			event.setCancelled(true);
		}
	}

	/**
	 * 拦截高版本 Display 实体（物品展示/方块展示/文本展示）的右键交互。
	 * 这些实体不属于 Hanging / ItemFrame，需要单独处理。
	 */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onDisplayInteract(PlayerInteractEntityEvent event) {
		if (event.getHand() != EquipmentSlot.HAND) return;
		if (!(event.getRightClicked() instanceof Display)) return;
		if (shouldBypass(event.getPlayer())) return;

		event.setCancelled(true);
	}

	/**
	 * 防止玩家对羊进行染色、对蘑菇牛剪蘑菇等“右键改造生物外观”的操作。
	 * 这里按手持物兜底拦截，避免误伤普通空手交互。
	 */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onEntityCosmeticInteract(PlayerInteractEntityEvent event) {
		if (shouldBypass(event.getPlayer())) return;
		if (!(event.getRightClicked() instanceof LivingEntity)) return;

		ItemStack item = event.getHand() == EquipmentSlot.HAND
				? event.getPlayer().getInventory().getItemInMainHand()
				: event.getPlayer().getInventory().getItemInOffHand();
		Material type = item.getType();
		if (isDyeItem(type) || type == Material.SHEARS) {
			event.setCancelled(true);
		}
	}

	/**
	 * 兜底：若仍触发了拴绳事件则取消（不应再消耗物品）。
	 */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onLeash(PlayerLeashEntityEvent event) {
		if (shouldBypass(event.getPlayer())) return;
		event.setCancelled(true);
	}

	/**
	 * 防止玩家右键展示框旋转 / 放入物品。
	 */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onItemFrameInteract(PlayerInteractEntityEvent event) {
		if (event.getHand() != EquipmentSlot.HAND) return;
		if (!(event.getRightClicked() instanceof ItemFrame)) return;
		if (shouldBypass(event.getPlayer())) return;

		event.setCancelled(true);
	}

	// ==================================================================================
	// SECTION 3：物品使用（水桶 / 火 / 末影珍珠 / 紫颂果 / 鱼竿 / 载具放置）
	// ==================================================================================

	/**
	 * 防止玩家倒水 / 倒岩浆，避免破坏场景或干扰玩法。
	 */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onBucketEmpty(PlayerBucketEmptyEvent event) {
		if (shouldBypass(event.getPlayer())) return;
		event.setCancelled(true);
	}

	/**
	 * 防止玩家用桶舀走场景内的水 / 岩浆。
	 */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onBucketFill(PlayerBucketFillEvent event) {
		if (shouldBypass(event.getPlayer())) return;
		event.setCancelled(true);
	}

	/**
	 * 拦截玩家造成的所有点火行为（打火石、火焰弹、烈焰人射火等无所谓，反正只挡玩家）。
	 */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onBlockIgnite(BlockIgniteEvent event) {
		Player player = event.getPlayer();
		if (player == null) return;
		if (shouldBypass(player)) return;

		event.setCancelled(true);
	}

	/**
	 * 拦截一些「会立即改变玩家位置 / 状态」的可被滥用的物品。
	 * - 末影珍珠：直接传送，可能穿墙
	 * - 紫颂果：随机闪烁，可能脱出房间
	 * - 三叉戟激流：在水中冲撞，可能撞穿伪装判定
	 */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onDisallowedItemUse(PlayerInteractEvent event) {
		if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
		if (event.getItem() == null) return;
		if (shouldBypass(event.getPlayer())) return;

		Material type = event.getItem().getType();
		if (type == Material.ENDER_PEARL
				|| type == Material.ENDER_EYE
				|| type == Material.CHORUS_FRUIT
				|| type == Material.TRIDENT
				|| type == Material.FIRE_CHARGE
				|| isVehicleItem(type)
				|| isBoatItem(type)) {
			event.setCancelled(true);
		}
	}

	/**
	 * 紫颂果以「食用」方式触发传送，物品使用拦截覆盖不到，需要单独拦消耗事件。
	 */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onItemConsume(PlayerItemConsumeEvent event) {
		if (shouldBypass(event.getPlayer())) return;
		if (event.getItem().getType() == Material.CHORUS_FRUIT) {
			event.setCancelled(true);
		}
	}

	/**
	 * 防止玩家用鱼竿钩拉其他实体（特别是其他玩家），打乱伪装与位置。
	 */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onFish(PlayerFishEvent event) {
		if (shouldBypass(event.getPlayer())) return;
		event.setCancelled(true);
	}

	// ==================================================================================
	// SECTION 4：副手（禁止 F 键交换与任何方式放入副手）
	// ==================================================================================

	/**
	 * 禁止按 F 在主手与副手之间交换物品。
	 */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
		if (shouldBypass(event.getPlayer())) return;
		event.setCancelled(true);
	}

	/**
	 * 禁止在背包界面点击副手格、以及背包内的 F 键（SWAP_OFFHAND）。
	 */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onInventoryClickOffhand(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) return;
		if (shouldBypass(player)) return;

		if (event.getClick() == ClickType.SWAP_OFFHAND || isOffhandSlotClick(event)) {
			event.setCancelled(true);
		}
	}

	/**
	 * 禁止拖拽物品到副手格。
	 */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onInventoryDragOffhand(InventoryDragEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) return;
		if (shouldBypass(player)) return;

		InventoryView view = event.getView();
		if (view.getBottomInventory().getHolder() != player) return;

		for (int rawSlot : event.getRawSlots()) {
			if (view.convertSlot(rawSlot) == OFFHAND_SLOT) {
				event.setCancelled(true);
				return;
			}
		}
	}

	// ==================================================================================
	// SECTION 5：界面 / 玩家状态（睡觉、讲台取书、告示牌编辑、载具进入）
	// ==================================================================================

	/**
	 * 禁止玩家在大厅或房间内躺床睡觉（会跳过夜晚、影响投票节奏）。
	 */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onBedEnter(PlayerBedEnterEvent event) {
		if (shouldBypass(event.getPlayer())) return;
		event.setCancelled(true);
	}

	/**
	 * 禁止从讲台里取走展示的书（保护布景）。
	 */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onTakeLecternBook(PlayerTakeLecternBookEvent event) {
		if (shouldBypass(event.getPlayer())) return;
		event.setCancelled(true);
	}

	/**
	 * 禁止打开告示牌的编辑面板（1.20+ 双面告示牌右键也走这里）。
	 */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onSignOpen(PlayerOpenSignEvent event) {
		if (shouldBypass(event.getPlayer())) return;
		event.setCancelled(true);
	}

	/**
	 * 禁止玩家上船 / 上矿车，避免被困或卡位。
	 */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onVehicleEnter(VehicleEnterEvent event) {
		if (!(event.getEntered() instanceof Player player)) return;
		if (shouldBypass(player)) return;
		event.setCancelled(true);
	}

	// ==================================================================================
	// 工具方法
	// ==================================================================================

	/**
	 * 统一豁免规则：创造模式 + 持有绕过权限。
	 */
	private boolean shouldBypass(Player player) {
		return player.getGameMode() == GameMode.CREATIVE
				|| player.hasPermission("animalhide.bypass.interact");
	}

	/**
	 * 是否属于会被玩家右键改变状态/打开的可交互方块。
	 */
	private static final int OFFHAND_SLOT = 40;

	private boolean isOffhandSlotClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) return false;
		if (event.getSlot() != OFFHAND_SLOT) return false;
		Inventory clicked = event.getClickedInventory();
		if (clicked == null) return false;
		return clicked.getHolder() == player || clicked.getType() == InventoryType.PLAYER;
	}

	private boolean isProtectedPot(Material type) {
		return type == Material.DECORATED_POT || Tag.FLOWER_POTS.isTagged(type);
	}

	private void denyPotInteraction(PlayerInteractEvent event) {
		event.setCancelled(true);
		event.setUseInteractedBlock(Event.Result.DENY);
		event.setUseItemInHand(Event.Result.DENY);
	}

	private boolean isProtectedInteractable(Material type) {
		return Tag.DOORS.isTagged(type)
				|| Tag.TRAPDOORS.isTagged(type)
				|| Tag.FENCE_GATES.isTagged(type)
				|| Tag.BUTTONS.isTagged(type)
				|| Tag.SIGNS.isTagged(type)
				|| Tag.SHULKER_BOXES.isTagged(type)
				|| Tag.CANDLES.isTagged(type)
				|| Tag.CANDLE_CAKES.isTagged(type)
				|| type == Material.LEVER
				|| type == Material.CAKE
				|| type == Material.RESPAWN_ANCHOR
				|| type == Material.COMPARATOR
				|| type == Material.REPEATER
				|| type == Material.DAYLIGHT_DETECTOR
				|| type == Material.NOTE_BLOCK
				|| type == Material.JUKEBOX
				|| Tag.ITEMS_WOODEN_SHELVES.isTagged(type)
				|| Tag.WOODEN_SHELVES.isTagged(type);
	}

	private boolean isVehicleItem(Material type) {
		return Tag.ITEMS_BOATS.isTagged(type)
				|| Tag.ITEMS_CHEST_BOATS.isTagged(type)
				|| type == Material.MINECART
				|| type == Material.CHEST_MINECART
				|| type == Material.HOPPER_MINECART
				|| type == Material.FURNACE_MINECART
				|| type == Material.TNT_MINECART
				|| type == Material.COMMAND_BLOCK_MINECART;
	}

	private boolean isDyeItem(Material type) {
		return type.name().endsWith("_DYE");
	}

	private boolean isBoatItem(Material type) {
		String name = type.name();
		return name.endsWith("_BOAT") || name.endsWith("_CHEST_BOAT") || name.endsWith("_RAFT") || name.endsWith("_CHEST_RAFT");
	}
}
