package me.suxuan.animalhide.commands;

import me.suxuan.animalhide.game.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.List;

public class JoinSubCommand implements SubCommand {

	private final GameManager gameManager;

	public JoinSubCommand(GameManager gameManager) {
		this.gameManager = gameManager;
	}

	@Override
	public String getName() {
		return "join";
	}

	@Override
	public String getUsage() {
		return "/hide join <地图名> [玩家名]";
	}

	@Override
	public String getPermission() {
		return null;
	}

	@Override
	public boolean execute(CommandSender sender, String[] args) {
		if (args.length < 1) {
			sender.sendMessage(Component.text("用法: " + getUsage(), NamedTextColor.RED));
			return true;
		}

		String mapName = args[0];
		Player target;

		// 第二个参数是玩家名（控制台或代执行场景）
		if (args.length >= 2) {
			target = Bukkit.getPlayer(args[1]);
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

		if (gameManager.getArenaByPlayer(target) != null) {
			if (sender != target) {
				sender.sendMessage(Component.text(target.getName() + " 已经在游戏中了！", NamedTextColor.RED));
			} else {
				target.sendMessage(Component.text("你已经在游戏中了！", NamedTextColor.RED));
			}
			return true;
		}

		gameManager.joinMatchmaking(target, mapName);
		return true;
	}

	@Override
	public List<String> tabComplete(CommandSender sender, String[] args) {
		if (args.length == 1) {
			return StringUtil.copyPartialMatches(args[0], gameManager.getTemplates().keySet(), new ArrayList<>());
		}
		return null; // 第 2 个参数返回 null → Bukkit 自动补全在线玩家
	}
}
