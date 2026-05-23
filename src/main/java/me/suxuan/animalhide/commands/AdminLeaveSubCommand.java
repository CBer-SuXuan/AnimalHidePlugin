package me.suxuan.animalhide.commands;

import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class AdminLeaveSubCommand implements SubCommand {

	private final GameManager gameManager;

	public AdminLeaveSubCommand(GameManager gameManager) {
		this.gameManager = gameManager;
	}

	@Override
	public String getName() {
		return "adminleave";
	}

	@Override
	public String getUsage() {
		return "/hide adminleave [玩家名]";
	}

	@Override
	public String getPermission() {
		return "animalhide.admin.leave";
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
			if (!(sender instanceof Player player)) {
				sender.sendMessage(Component.text("控制台后台执行必须指定玩家名！", NamedTextColor.RED));
				return true;
			}
			target = player;
		}

		Arena arena = gameManager.getArenaByPlayer(target);
		if (arena == null) {
			sender.sendMessage(Component.text("目标玩家当前不在任何房间中。", NamedTextColor.RED));
			return true;
		}

		arena.removePlayer(target);
		sender.sendMessage(Component.text("已将 " + target.getName() + " 移出当前房间。", NamedTextColor.GREEN));
		return true;
	}

	@Override
	public List<String> tabComplete(CommandSender sender, String[] args) {
		return null;
	}
}
