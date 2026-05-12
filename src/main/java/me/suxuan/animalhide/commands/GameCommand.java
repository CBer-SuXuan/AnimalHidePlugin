package me.suxuan.animalhide.commands;

import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 游戏主指令执行器与补全器
 * 处理 /hide join <地图> 和 /hide leave 等命令
 */
public class GameCommand implements CommandExecutor, TabCompleter {

	private final GameManager gameManager;

	public GameCommand(GameManager gameManager) {
		this.gameManager = gameManager;
	}

	@Override
	public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

		if (!(sender instanceof Player player)) {
			sender.sendMessage(Component.text("该指令只能由玩家在游戏内执行！", NamedTextColor.RED));
			return true;
		}

		if (!player.hasPermission("animalhide.use")) {
			player.sendMessage(Component.text("✘ 你没有权限使用该指令！", NamedTextColor.RED));
			return true;
		}

		if (args.length == 0) {
			sendHelpMessage(player);
			return true;
		}

		// 4. 解析子命令
		String subCommand = args[0].toLowerCase();

		switch (subCommand) {
			case "join":
				handleJoin(player, args);
				break;
			case "leave":
				handleLeave(player);
				break;
			case "list":
				handleList(player);
				break;
			case "reload":
				handleReload(player);
				break;
			default:
				player.sendMessage(Component.text("✘ 未知的子指令，请输入 /hide 查看帮助。", NamedTextColor.RED));
				break;
		}

		return true;
	}

	/**
	 * 处理加入房间逻辑
	 */
	private void handleJoin(Player player, String[] args) {
		// 检查玩家是否已经在游戏中
		if (gameManager.getArenaByPlayer(player) != null) {
			player.sendMessage(Component.text("✘ 你当前已经在游戏中了，请先使用 /hide leave 退出！", NamedTextColor.RED));
			return;
		}

		if (args.length < 2) {
			player.sendMessage(Component.text("✘ 用法错误！请输入: /hide join <地图名称>", NamedTextColor.RED));
			return;
		}

		String arenaName = args[1];
		Arena arena = gameManager.getArena(arenaName);

		if (arena == null) {
			player.sendMessage(Component.text("✘ 找不到名为 '" + arenaName + "' 的地图！", NamedTextColor.RED));
			return;
		}

		// 调用我们在 Arena 中写好的加入逻辑
		arena.addPlayer(player);
	}

	/**
	 * 处理离开房间逻辑
	 */
	private void handleLeave(Player player) {
		Arena arena = gameManager.getArenaByPlayer(player);

		if (arena == null) {
			player.sendMessage(Component.text("✘ 你当前不在任何游戏中！", NamedTextColor.RED));
			return;
		}

		arena.removePlayer(player);
		player.sendMessage(Component.text("✔ 你已成功退出游戏。", NamedTextColor.GREEN));
	}

	/**
	 * 处理查看可用房间列表逻辑
	 */
	private void handleList(Player player) {
		player.sendMessage(Component.text("=== 可用的游戏房间 ===", NamedTextColor.AQUA));
		for (Arena arena : gameManager.getArenas().values()) {
			Component info = Component.text("- " + arena.getArenaName(), NamedTextColor.YELLOW)
					.append(Component.text(" [" + arena.getState().name() + "] ", NamedTextColor.GRAY))
					.append(Component.text("(" + arena.getPlayers().size() + "/" + arena.getMaxPlayers() + ")", NamedTextColor.GREEN));
			player.sendMessage(info);
		}
	}

	/**
	 * 处理插件重载逻辑
	 */
	private void handleReload(Player player) {
		// 独立的管理员权限检查
		if (!player.hasPermission("animalhide.admin")) {
			player.sendMessage(Component.text("✘ 你没有权限执行插件重载！", NamedTextColor.RED));
			return;
		}

		player.sendMessage(Component.text("正在重载 AnimalHide 配置文件...", NamedTextColor.YELLOW));

		try {
			gameManager.reload();
			player.sendMessage(Component.text("✔ 插件重载成功！", NamedTextColor.GREEN));
		} catch (Exception e) {
			player.sendMessage(Component.text("✘ 重载失败，请查看后台报错！", NamedTextColor.DARK_RED));
			e.printStackTrace();
		}
	}

	/**
	 * 发送帮助菜单
	 */
	private void sendHelpMessage(Player player) {
		player.sendMessage(Component.text("--- 动物躲猫猫 (AnimalHide) 指令帮助 ---", NamedTextColor.GOLD));
		player.sendMessage(Component.text("/hide join <地图名> ", NamedTextColor.YELLOW).append(Component.text("- 加入一场游戏", NamedTextColor.WHITE)));
		player.sendMessage(Component.text("/hide leave ", NamedTextColor.YELLOW).append(Component.text("- 退出当前游戏", NamedTextColor.WHITE)));
		player.sendMessage(Component.text("/hide list ", NamedTextColor.YELLOW).append(Component.text("- 查看所有地图与状态", NamedTextColor.WHITE)));
		player.sendMessage(Component.text("/hide reload ", NamedTextColor.YELLOW).append(Component.text("- 重新加载插件配置文件", NamedTextColor.WHITE)));
	}

	/**
	 * Tab 键自动补全逻辑
	 */
	@Override
	public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
		List<String> completions = new ArrayList<>();

		if (args.length == 1) {
			completions.add("join");
			completions.add("leave");
			completions.add("list");
			completions.add("reload");
		} else if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
			// 当输入 /hide join 时，补全所有已加载的地图名称
			completions.addAll(gameManager.getArenas().keySet());
		}

		// 根据玩家当前输入的字符进行前缀过滤（原版特性：只显示匹配的字母）
		List<String> filtered = new ArrayList<>();
		String currentInput = args[args.length - 1].toLowerCase();
		for (String s : completions) {
			if (s.toLowerCase().startsWith(currentInput)) {
				filtered.add(s);
			}
		}
		return filtered;
	}
}