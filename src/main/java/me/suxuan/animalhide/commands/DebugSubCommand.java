package me.suxuan.animalhide.commands;

import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DebugSubCommand implements SubCommand {

	private final GameManager gameManager;

	public DebugSubCommand(GameManager gameManager) {
		this.gameManager = gameManager;
	}

	@Override
	public String getName() {
		return "debug";
	}

	@Override
	public String getUsage() {
		return "/hide debug";
	}

	@Override
	public String getPermission() {
		return "animalhide.admin";
	}

	@Override
	public boolean execute(CommandSender sender, String[] args) {
		if (!(sender instanceof Player p)) {
			sender.sendMessage(Component.text("必须在游戏内执行！", NamedTextColor.RED));
			return true;
		}

		Arena arena = gameManager.getArenaByPlayer(p);
		if (arena == null) {
			p.sendMessage(Component.text("你不在任何游戏中，无法查看数据！", NamedTextColor.RED));
			return true;
		}

		p.sendMessage(Component.text("=== 当前房间实时数据 ===", NamedTextColor.GOLD));
		p.sendMessage(Component.text("场上静态AI动物总数: " + arena.getAiAnimals().size(), NamedTextColor.AQUA));
		p.sendMessage(Component.text("当前存活躲藏者: " + arena.getHiders().size(), NamedTextColor.GREEN));
		p.sendMessage(Component.text("当前寻找者: " + arena.getSeekers().size(), NamedTextColor.RED));
		p.sendMessage(Component.text("当前旁观者: " + arena.getSpectators().size(), NamedTextColor.GRAY));
		return true;
	}
}
