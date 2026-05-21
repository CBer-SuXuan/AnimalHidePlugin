package me.suxuan.animalhide.commands;

import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理员强行开始对局（跳过大厅倒计时，不要求达到最少人数）。
 */
public class ForceStartSubCommand implements SubCommand {

	private final GameManager gameManager;

	public ForceStartSubCommand(GameManager gameManager) {
		this.gameManager = gameManager;
	}

	@Override
	public String getName() {
		return "forcestart";
	}

	@Override
	public String getUsage() {
		return "/hide forcestart [地图名]";
	}

	@Override
	public String getPermission() {
		return "animalhide.admin";
	}

	@Override
	public boolean execute(CommandSender sender, String[] args) {
		Arena arena;

		if (args.length >= 1) {
			arena = gameManager.findLobbyArenaByMapName(args[0]);
			if (arena == null) {
				sender.sendMessage(Component.text("找不到可开始的「" + args[0] + "」房间（需处于等待/倒计时且世界已就绪）。", NamedTextColor.RED));
				return true;
			}
		} else if (sender instanceof Player player) {
			arena = gameManager.getArenaByPlayer(player);
			if (arena == null) {
				sender.sendMessage(Component.text("你不在任何房间中！请指定地图名：", NamedTextColor.RED)
						.append(Component.text(getUsage(), NamedTextColor.GRAY)));
				return true;
			}
		} else {
			sender.sendMessage(Component.text("控制台请指定地图名：", NamedTextColor.RED)
					.append(Component.text(getUsage(), NamedTextColor.GRAY)));
			return true;
		}

		GameManager.ForceStartResult result = gameManager.forceStartGame(arena);
		switch (result) {
			case SUCCESS -> {
				String by = sender instanceof Player p ? p.getName() : "控制台";
				arena.broadcast(Component.text("⚡ 管理员 ", NamedTextColor.GOLD)
						.append(Component.text(by, NamedTextColor.AQUA))
						.append(Component.text(" 已强行开始游戏！", NamedTextColor.YELLOW)));
				sender.sendMessage(Component.text("已强行开始房间「" + arena.getArenaName() + "」。", NamedTextColor.GREEN));
			}
			case WORLD_NOT_READY -> sender.sendMessage(Component.text("房间世界尚未生成完成，请稍后再试。", NamedTextColor.RED));
			case ALREADY_PLAYING -> sender.sendMessage(Component.text("该房间已在游戏中。", NamedTextColor.RED));
			case ENDING -> sender.sendMessage(Component.text("该房间正在结束或生成世界，无法开始。", NamedTextColor.RED));
			case NO_PLAYERS -> sender.sendMessage(Component.text("房间内没有玩家。", NamedTextColor.RED));
		}
		return true;
	}

	@Override
	public List<String> tabComplete(CommandSender sender, String[] args) {
		if (args.length == 1) {
			List<String> names = new ArrayList<>();
			for (Arena arena : gameManager.getActiveMatches()) {
				if (gameManager.canForceStart(arena)) {
					names.add(arena.getArenaName());
				}
			}
			names.addAll(gameManager.getTemplates().keySet());
			return StringUtil.copyPartialMatches(args[0], names, new ArrayList<>());
		}
		return List.of();
	}
}
