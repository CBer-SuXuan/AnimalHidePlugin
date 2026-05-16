package me.suxuan.animalhide.commands;

import me.suxuan.animalhide.game.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /hide 主命令分发器。
 * <p>
 * 仅做：识别 args[0] → 找到对应 {@link SubCommand} → 鉴权 → 转交。
 * 业务逻辑全部放在各 SubCommand 类里。
 */
public class GameCommand implements CommandExecutor, TabCompleter {

	private final Map<String, SubCommand> subCommands = new LinkedHashMap<>();

	public GameCommand(GameManager gameManager) {
		register(new JoinSubCommand(gameManager));
		register(new LeaveSubCommand(gameManager));
		register(new ReloadSubCommand(gameManager));
		register(new TutorialSubCommand());
		register(new DebugSubCommand(gameManager));
		register(new ArenaSubCommand(gameManager));
		register(new ScoreSubCommand(gameManager));
	}

	private void register(SubCommand sub) {
		subCommands.put(sub.getName().toLowerCase(Locale.ROOT), sub);
	}

	@Override
	public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
		if (args.length == 0) {
			sendHelp(sender);
			return true;
		}

		SubCommand sub = subCommands.get(args[0].toLowerCase(Locale.ROOT));
		if (sub == null) {
			sender.sendMessage(Component.text("未知子命令: " + args[0], NamedTextColor.RED));
			sendHelp(sender);
			return true;
		}

		if (sub.getPermission() != null && !sender.hasPermission(sub.getPermission())) {
			sender.sendMessage(Component.text("你没有权限执行此操作！", NamedTextColor.RED));
			return true;
		}

		String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
		return sub.execute(sender, subArgs);
	}

	@Override
	public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
		if (args.length == 1) {
			List<String> visibleNames = new ArrayList<>();
			for (SubCommand sub : subCommands.values()) {
				if (sub.getPermission() == null || sender.hasPermission(sub.getPermission())) {
					visibleNames.add(sub.getName());
				}
			}
			List<String> result = new ArrayList<>();
			StringUtil.copyPartialMatches(args[0], visibleNames, result);
			Collections.sort(result);
			return result;
		}

		SubCommand sub = subCommands.get(args[0].toLowerCase(Locale.ROOT));
		if (sub == null) return Collections.emptyList();
		if (sub.getPermission() != null && !sender.hasPermission(sub.getPermission())) {
			return Collections.emptyList();
		}

		String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
		return sub.tabComplete(sender, subArgs);
	}

	private void sendHelp(CommandSender sender) {
		sender.sendMessage(Component.text("=== /hide 用法 ===", NamedTextColor.GOLD));
		for (SubCommand sub : subCommands.values()) {
			if (sub.getPermission() != null && !sender.hasPermission(sub.getPermission())) continue;
			sender.sendMessage(Component.text(" • " + sub.getUsage(), NamedTextColor.AQUA));
		}
	}
}
