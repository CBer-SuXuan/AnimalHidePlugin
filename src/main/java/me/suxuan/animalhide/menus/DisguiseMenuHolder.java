package me.suxuan.animalhide.menus;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * 伪装菜单的专属 Holder
 * 用于在 InventoryClickEvent 中安全、精准地识别我们的自定义菜单
 */
public class DisguiseMenuHolder implements InventoryHolder {
	private Inventory inventory;

	public void setInventory(Inventory inventory) {
		this.inventory = inventory;
	}

	@Override
	public @NotNull Inventory getInventory() {
		return inventory;
	}
}