package me.suxuan.animalhide.commands;

import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class GameCommand implements CommandExecutor {

	private final GameManager gameManager;

	public GameCommand(GameManager gameManager) {
		this.gameManager = gameManager;
	}

	@Override
	public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
		if (args.length == 0) {
			sender.sendMessage(Component.text("用法: /hide join <地图名> [玩家] 或 /hide leave [玩家]", NamedTextColor.RED));
			return true;
		}

		// ==========================================
		// 加入游戏指令 (/hide join <地图> [玩家])
		// ==========================================
		if (args[0].equalsIgnoreCase("join")) {
			if (args.length < 2) {
				sender.sendMessage(Component.text("用法: /hide join <地图名> [玩家名]", NamedTextColor.RED));
				return true;
			}

			String arenaName = args[1];
			Arena arena = gameManager.getArena(arenaName);

			if (arena == null) {
				sender.sendMessage(Component.text("找不到该地图！", NamedTextColor.RED));
				return true;
			}

			Player target = null;

			// 如果参数有 3 个，说明指令指定了玩家 (例如控制台执行: /hide join example %player_name%)
			if (args.length == 3) {
				target = Bukkit.getPlayer(args[2]);
				if (target == null || !target.isOnline()) {
					sender.sendMessage(Component.text("找不到该玩家或玩家不在线！", NamedTextColor.RED));
					return true;
				}
			} else {
				// 如果只有 2 个参数，必须是玩家自己执行
				if (!(sender instanceof Player)) {
					sender.sendMessage(Component.text("控制台后台执行必须指定玩家名！用法: /hide join <地图名> <玩家名>", NamedTextColor.RED));
					return true;
				}
				target = (Player) sender;
			}

			// 执行加入逻辑
			if (gameManager.getArenaByPlayer(target) != null) {
				// 如果是后台代执行的，给后台也发一条提示
				if (sender != target) {
					sender.sendMessage(Component.text(target.getName() + " 已经在游戏中了！", NamedTextColor.RED));
				} else {
					target.sendMessage(Component.text("你已经在游戏中了！", NamedTextColor.RED));
				}
				return true;
			}

			arena.addPlayer(target);

			return true;
		}

		// ==========================================
		// 离开游戏指令 (/hide leave [玩家])
		// ==========================================
		if (args[0].equalsIgnoreCase("leave")) {
			Player target = null;

			if (args.length == 2) {
				target = Bukkit.getPlayer(args[1]);
				if (target == null || !target.isOnline()) {
					sender.sendMessage(Component.text("找不到该玩家或玩家不在线！", NamedTextColor.RED));
					return true;
				}
			} else {
				if (!(sender instanceof Player)) {
					sender.sendMessage(Component.text("控制台后台执行必须指定玩家名！用法: /hide leave <玩家名>", NamedTextColor.RED));
					return true;
				}
				target = (Player) sender;
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

		// ==========================================
		// 重载指令 (/hide reload)
		// ==========================================
		if (args[0].equalsIgnoreCase("reload")) {
			if (sender.hasPermission("animalhide.admin")) {
				gameManager.reload();
				sender.sendMessage(Component.text("插件配置重载成功！", NamedTextColor.GREEN));
			} else {
				sender.sendMessage(Component.text("你没有权限执行此操作！", NamedTextColor.RED));
			}
			return true;
		}

		return true;
	}
}