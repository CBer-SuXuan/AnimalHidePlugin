package me.suxuan.animalhide.commands;

import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.manager.TutorialManager;
import me.suxuan.animalhide.tutorial.DemoStation;
import me.suxuan.animalhide.tutorial.TutorialDemo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 管理员命令：在大厅部署/清理"教程演示桩"。
 *
 * <pre>
 * /hide tutorial spawn &lt;id&gt;         在你脚下生成一个指定的演示桩
 * /hide tutorial spawnall            在你脚下沿正前方一字排开生成全部
 * /hide tutorial list                列出所有可用 ID
 * /hide tutorial removenear [r]      清除半径 r (默认 5) 内的演示桩
 * /hide tutorial removeall           清空全部演示桩
 * </pre>
 */
public class TutorialSubCommand implements SubCommand {

	private static final List<String> ACTIONS = Arrays.asList(
			"spawn", "spawnall", "list", "removenear", "removeall"
	);

	@Override
	public String getName() {
		return "tutorial";
	}

	@Override
	public String getUsage() {
		return "/hide tutorial <spawn|spawnall|list|removenear|removeall> [...]";
	}

	@Override
	public String getPermission() {
		return "animalhide.admin";
	}

	@Override
	public boolean execute(CommandSender sender, String[] args) {
		if (args.length < 1) {
			sendHelp(sender);
			return true;
		}

		TutorialManager tm = AnimalHidePlugin.getInstance().getTutorialManager();
		String action = args[0].toLowerCase(Locale.ROOT);

		return switch (action) {
			case "spawn" -> handleSpawn(sender, tm, args);
			case "spawnall" -> handleSpawnAll(sender, tm);
			case "list" -> handleList(sender, tm);
			case "removenear" -> handleRemoveNear(sender, tm, args);
			case "removeall" -> handleRemoveAll(sender, tm);
			default -> {
				sender.sendMessage(Component.text("未知操作: " + action, NamedTextColor.RED));
				sendHelp(sender);
				yield true;
			}
		};
	}

	// =====================================================================
	// 操作处理
	// =====================================================================

	private boolean handleSpawn(CommandSender sender, TutorialManager tm, String[] args) {
		if (!(sender instanceof Player p)) {
			sender.sendMessage(Component.text("必须在游戏内执行此操作。", NamedTextColor.RED));
			return true;
		}
		if (args.length < 2) {
			p.sendMessage(Component.text("用法: /hide tutorial spawn <id>", NamedTextColor.RED));
			p.sendMessage(Component.text("使用 /hide tutorial list 查看可用 ID。", NamedTextColor.GRAY));
			return true;
		}
		TutorialDemo demo = TutorialDemo.fromId(args[1]);
		if (demo == null) {
			p.sendMessage(Component.text("未知的演示桩 ID: " + args[1], NamedTextColor.RED));
			return true;
		}
		String id = tm.spawn(p.getLocation(), demo);
		if (id == null) {
			p.sendMessage(Component.text("演示桩生成失败，请检查日志。", NamedTextColor.RED));
		} else {
			p.sendMessage(Component.text("✔ 已生成演示桩 ", NamedTextColor.GREEN)
					.append(Component.text(demo.getId(), NamedTextColor.AQUA))
					.append(Component.text(" (内部 ID: " + id + ")", NamedTextColor.DARK_GRAY)));
		}
		return true;
	}

	private boolean handleSpawnAll(CommandSender sender, TutorialManager tm) {
		if (!(sender instanceof Player p)) {
			sender.sendMessage(Component.text("必须在游戏内执行此操作。", NamedTextColor.RED));
			return true;
		}
		Location base = p.getLocation();
		Vector forward = base.getDirection().setY(0);
		if (forward.lengthSquared() < 1e-6) {
			forward = new Vector(0, 0, 1);
		}
		forward.normalize();
		// 与玩家朝向垂直的"右向"，用作并排间隔方向
		Vector right = new Vector(-forward.getZ(), 0, forward.getX());

		TutorialDemo[] all = TutorialDemo.values();
		int spacing = 3;
		int placed = 0;

		// 居中铺开：i 从 -n/2 ... n/2
		int n = all.length;
		for (int i = 0; i < n; i++) {
			double offset = (i - (n - 1) / 2.0) * spacing;
			Location loc = base.clone().add(right.clone().multiply(offset));
			// 让 NPC 面朝玩家来的方向
			loc.setYaw(yawFromVector(forward.clone().multiply(-1)));
			loc.setPitch(0);
			if (tm.spawn(loc, all[i]) != null) placed++;
		}
		p.sendMessage(Component.text("✔ 已在你周围一字排开生成 " + placed + " 个教程演示桩。", NamedTextColor.GREEN));
		return true;
	}

	private boolean handleList(CommandSender sender, TutorialManager tm) {
		sender.sendMessage(Component.text("=== 教程演示桩列表 ===", NamedTextColor.GOLD));
		for (TutorialDemo demo : TutorialDemo.values()) {
			Component line = Component.text(" • ", NamedTextColor.DARK_GRAY)
					.append(Component.text(demo.getId(), NamedTextColor.AQUA))
					.append(Component.text("  ", NamedTextColor.DARK_GRAY))
					.append(LegacyComponentSerializer.legacySection().deserialize(demo.getTitleLine()))
					.append(Component.text("  ", NamedTextColor.DARK_GRAY))
					.append(LegacyComponentSerializer.legacySection().deserialize(demo.getSubTitleLine()))
					.decoration(TextDecoration.ITALIC, false);
			sender.sendMessage(line);
		}
		Collection<DemoStation> active = tm.getStations();
		sender.sendMessage(Component.text("当前已部署: " + active.size() + " 个。", NamedTextColor.GRAY));
		return true;
	}

	private boolean handleRemoveNear(CommandSender sender, TutorialManager tm, String[] args) {
		if (!(sender instanceof Player p)) {
			sender.sendMessage(Component.text("必须在游戏内执行此操作。", NamedTextColor.RED));
			return true;
		}
		double radius = 5.0;
		if (args.length >= 2) {
			try {
				radius = Math.max(0.5, Double.parseDouble(args[1]));
			} catch (NumberFormatException e) {
				p.sendMessage(Component.text("无效半径: " + args[1] + "，已使用默认值 5。", NamedTextColor.RED));
			}
		}
		int removed = tm.removeNear(p.getLocation(), radius);
		p.sendMessage(Component.text("✔ 已清除附近 " + removed + " 个演示桩。", NamedTextColor.YELLOW));
		return true;
	}

	private boolean handleRemoveAll(CommandSender sender, TutorialManager tm) {
		int before = tm.getStations().size();
		tm.removeAll();
		sender.sendMessage(Component.text("✔ 已清空全部 " + before + " 个教程演示桩。", NamedTextColor.YELLOW));
		return true;
	}

	private void sendHelp(CommandSender sender) {
		sender.sendMessage(Component.text("=== /hide tutorial 用法 ===", NamedTextColor.GOLD));
		sender.sendMessage(Component.text(" • /hide tutorial spawn <id>      ", NamedTextColor.AQUA)
				.append(Component.text("- 在你脚下放一个演示桩", NamedTextColor.GRAY)));
		sender.sendMessage(Component.text(" • /hide tutorial spawnall        ", NamedTextColor.AQUA)
				.append(Component.text("- 在你周围一字排开生成全部", NamedTextColor.GRAY)));
		sender.sendMessage(Component.text(" • /hide tutorial list            ", NamedTextColor.AQUA)
				.append(Component.text("- 列出全部可用 ID", NamedTextColor.GRAY)));
		sender.sendMessage(Component.text(" • /hide tutorial removenear [r]  ", NamedTextColor.AQUA)
				.append(Component.text("- 清除半径 r 内的桩 (默认 5)", NamedTextColor.GRAY)));
		sender.sendMessage(Component.text(" • /hide tutorial removeall       ", NamedTextColor.AQUA)
				.append(Component.text("- 清空全部", NamedTextColor.GRAY)));
	}

	@Override
	public List<String> tabComplete(CommandSender sender, String[] args) {
		if (args.length == 1) {
			List<String> out = new ArrayList<>();
			StringUtil.copyPartialMatches(args[0], ACTIONS, out);
			Collections.sort(out);
			return out;
		}
		if (args.length == 2 && "spawn".equalsIgnoreCase(args[0])) {
			List<String> ids = new ArrayList<>();
			for (TutorialDemo demo : TutorialDemo.values()) ids.add(demo.getId());
			List<String> out = new ArrayList<>();
			StringUtil.copyPartialMatches(args[1], ids, out);
			Collections.sort(out);
			return out;
		}
		return Collections.emptyList();
	}

	// =====================================================================
	// 工具
	// =====================================================================

	private float yawFromVector(Vector dir) {
		double rad = Math.atan2(-dir.getX(), dir.getZ());
		return (float) Math.toDegrees(rad);
	}
}
