package me.suxuan.animalhide.commands;

import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.GameManager;
import me.suxuan.animalhide.manager.TutorialManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class GameCommand implements CommandExecutor, TabCompleter {

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

			String mapName = args[1];

			Player target;

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

			gameManager.joinMatchmaking(target, mapName);
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

		// ==========================================
		// 生成教程npc指令 (/hide tutorial)
		// ==========================================
		if (args[0].equalsIgnoreCase("tutorial")) {
			if (!sender.hasPermission("animalhide.admin") || !(sender instanceof Player p)) {
				sender.sendMessage(Component.text("权限不足或必须在游戏内执行！", NamedTextColor.RED));
				return true;
			}

			TutorialManager tm = AnimalHidePlugin.getInstance().getTutorialManager();

			if (args.length < 2) {
				p.sendMessage(Component.text("用法: /hide tutorial <pig|sheep|cow|chicken|all|remove>", NamedTextColor.RED));
				return true;
			}

			String subAction = args[1].toLowerCase();

			if (subAction.equals("remove")) {
				tm.clearTutorialNPCs();
				p.sendMessage(Component.text("已清空大厅所有的技能演示 NPC！", NamedTextColor.YELLOW));
			} else {
				// 执行生成逻辑
				tm.spawnByType(p.getLocation(), subAction);
				p.sendMessage(Component.text("已在你所在的位置生成了: " + subAction, NamedTextColor.GREEN));
			}
			return true;
		}

		// ==========================================
		// 调试指令 (/hide debug)
		// ==========================================
		if (args[0].equalsIgnoreCase("debug")) {
			if (!sender.hasPermission("animalhide.admin") || !(sender instanceof Player p)) {
				sender.sendMessage(Component.text("权限不足或必须在游戏内执行！", NamedTextColor.RED));
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

		return true;
	}

	// ==========================================
	// 自动补全逻辑 (TabCompleter)
	// ==========================================
	@Override
	public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
		List<String> completions = new ArrayList<>();

		if (args.length == 1) {
			List<String> subCommands = new ArrayList<>(Arrays.asList("join", "leave"));
			if (sender.hasPermission("animalhide.admin")) {
				subCommands.add("reload");
				subCommands.add("tutorial");
				subCommands.add("debug");
			}
			StringUtil.copyPartialMatches(args[0], subCommands, completions);
			Collections.sort(completions);
			return completions;
		}

		if (args.length == 2) {
			String subCmd = args[0].toLowerCase();

			if (subCmd.equals("join")) {
				completions.addAll(gameManager.getTemplates().keySet());
				return completions;
			} else if (subCmd.equals("leave")) {
				return null;
			} else if (subCmd.equals("tutorial") && sender.hasPermission("animalhide.admin")) {
				List<String> tutorialArgs = Arrays.asList("pig", "sheep", "cow", "chicken", "all", "remove");
				StringUtil.copyPartialMatches(args[1], tutorialArgs, completions);
				Collections.sort(completions);
				return completions;
			}
		}

		if (args.length == 3) {
			if (args[0].equalsIgnoreCase("join")) {
				return null;
			}
		}

		return completions;
	}
}