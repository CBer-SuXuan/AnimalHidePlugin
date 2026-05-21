package me.suxuan.animalhide.commands;

import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class LeaveSubCommand implements SubCommand {

	private final GameManager gameManager;

	public LeaveSubCommand(GameManager gameManager) {
		this.gameManager = gameManager;
	}

	@Override
	public String getName() {
		return "leave";
	}

	@Override
	public String getUsage() {
		return "/hide leave [玩家名]";
	}

	@Override
	public String getPermission() {
		return null;
	}

	@Override
	public boolean execute(CommandSender sender, String[] args) {
		Player target;

		if (args.length >= 1) {
			target = Bukkit.getPlayer(args[0]);
			if (target == null || !target.isOnline()) {
				sender.sendMessage(Component.text("找不到该玩家或玩家不在线！", NamedTextColor.RED));
				return true;
			}
		} else {
			if (!(sender instanceof Player p)) {
				sender.sendMessage(Component.text("控制台后台执行必须指定玩家名！用法: " + getUsage(), NamedTextColor.RED));
				return true;
			}
			target = p;
		}

		Arena arena = gameManager.getArenaByPlayer(target);
		if (arena == null) {
			if (sender != target) {
				sender.sendMessage(Component.text(target.getName() + " 不在任何游戏中！", NamedTextColor.RED));
			} else {
				target.sendMessage(Component.text("你不在任何游戏中！", NamedTextColor.RED));
			}
			return true;
		}

		arena.removePlayer(target);

		if (sender != target) {
			sender.sendMessage(Component.text("已成功将玩家 " + target.getName() + " 移出游戏。", NamedTextColor.GREEN));
		}
		return true;
	}

	@Override
	public List<String> tabComplete(CommandSender sender, String[] args) {
		return null; // 让 Bukkit 自动补全在线玩家
	}
}
