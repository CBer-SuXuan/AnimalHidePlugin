package me.suxuan.animalhide.skill;

import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.game.Arena;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;

public record SkillContext(
		AnimalHidePlugin plugin,
		Player player,
		Arena arena,
		ItemStack item,
		Action action
) {
}
