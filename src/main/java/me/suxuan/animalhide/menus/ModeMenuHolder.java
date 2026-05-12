package me.suxuan.animalhide.menus;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * 模式选择菜单的专属 Holder
 */
public class ModeMenuHolder implements InventoryHolder {
	private Inventory inventory;

	public void setInventory(Inventory inventory) {
		this.inventory = inventory;
	}

	@Override
	public @NotNull Inventory getInventory() {
		return inventory;
	}
}