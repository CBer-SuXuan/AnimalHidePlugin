package me.suxuan.animalhide.commands;

import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.manager.TutorialManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TutorialSubCommand implements SubCommand {

	private static final List<String> TYPES = Arrays.asList("pig", "sheep", "cow", "chicken", "all", "remove");

	@Override
	public String getName() {
		return "tutorial";
	}

	@Override
	public String getUsage() {
		return "/hide tutorial <pig|sheep|cow|chicken|all|remove>";
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

		if (args.length < 1) {
			p.sendMessage(Component.text("用法: " + getUsage(), NamedTextColor.RED));
			return true;
		}

		TutorialManager tm = AnimalHidePlugin.getInstance().getTutorialManager();
		String subAction = args[0].toLowerCase();

		if (subAction.equals("remove")) {
			tm.clearTutorialNPCs();
			p.sendMessage(Component.text("已清空大厅所有的技能演示 NPC！", NamedTextColor.YELLOW));
		} else {
			tm.spawnByType(p.getLocation(), subAction);
			p.sendMessage(Component.text("已在你所在的位置生成了: " + subAction, NamedTextColor.GREEN));
		}
		return true;
	}

	@Override
	public List<String> tabComplete(CommandSender sender, String[] args) {
		if (args.length == 1) {
			return StringUtil.copyPartialMatches(args[0], TYPES, new ArrayList<>());
		}
		return List.of();
	}
}
