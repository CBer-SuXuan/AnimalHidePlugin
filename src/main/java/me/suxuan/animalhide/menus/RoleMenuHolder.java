package me.suxuan.animalhide.menus;

import lombok.Setter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

@Setter
public class RoleMenuHolder implements InventoryHolder {
	private Inventory inventory;

	@Override
	public @NotNull Inventory getInventory() {
		return inventory;
	}
}