package me.suxuan.animalhide.commands;

import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.config.ConfigManager;
import me.suxuan.animalhide.game.ArenaTemplate;
import me.suxuan.animalhide.game.GameManager;
import me.suxuan.animalhide.game.ScoringConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 管理员积分配置子命令：{@code /hide score <list|set|reset> ...}。
 * <p>
 * - list  &lt;map&gt;                    列出该地图当前所有积分项
 * - set   &lt;map&gt; &lt;key&gt; &lt;value&gt;   修改某项并落盘到 arenas/&lt;map&gt;.yml
 * - reset &lt;map&gt;                    清空 yml 的 scoring 节，所有项回退默认值
 * <p>
 * 修改后自动 {@link GameManager#reloadTemplatesOnly()} 刷新模板，
 * 不会打断任何正在进行的对局（进行中的房间继续持有旧 {@link ArenaTemplate}）。
 */
public class ScoreSubCommand implements SubCommand {

	private static final List<String> ACTIONS = Arrays.asList("list", "set", "reset");

	private final GameManager gameManager;
	private final ConfigManager configManager;

	public ScoreSubCommand(GameManager gameManager) {
		this.gameManager = gameManager;
		this.configManager = AnimalHidePlugin.getInstance().getConfigManager();
	}

	@Override
	public String getName() {
		return "score";
	}

	@Override
	public String getUsage() {
		return "/hide score <list|set|reset> <地图> [key] [value]";
	}

	@Override
	public String getPermission() {
		return "animalhide.admin";
	}

	@Override
	public boolean execute(CommandSender sender, String[] args) {
		if (args.length == 0) {
			sendActionHelp(sender);
			return true;
		}

		String action = args[0].toLowerCase(Locale.ROOT);
		String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

		switch (action) {
			case "list" -> handleList(sender, subArgs);
			case "set" -> handleSet(sender, subArgs);
			case "reset" -> handleReset(sender, subArgs);
			default -> {
				sender.sendMessage(Component.text("未知子命令: " + action, NamedTextColor.RED));
				sendActionHelp(sender);
			}
		}
		return true;
	}

	// ============================================================
	// list <map>
	// ============================================================
	private void handleList(CommandSender sender, String[] args) {
		if (args.length < 1) {
			sender.sendMessage(Component.text("用法: /hide score list <地图>", NamedTextColor.RED));
			return;
		}
		String mapName = args[0];
		ArenaTemplate template = gameManager.getTemplates().get(mapName);
		if (template == null) {
			sender.sendMessage(Component.text("找不到地图: " + mapName, NamedTextColor.RED));
			return;
		}

		ScoringConfig scoring = template.getScoring();
		sender.sendMessage(Component.text("=== " + mapName + " 的积分配置 ===", NamedTextColor.GOLD));
		for (String key : ScoringConfig.ALL_KEYS) {
			int value = scoring.getByKey(key);
			int def = ScoringConfig.DEFAULTS.get(key);
			String label = ScoringConfig.LABELS.getOrDefault(key, key);

			Component line = Component.text(" • " + key, NamedTextColor.AQUA)
					.append(Component.text(" [" + label + "]", NamedTextColor.GRAY))
					.append(Component.text(" = ", NamedTextColor.WHITE))
					.append(Component.text(String.valueOf(value),
							value == def ? NamedTextColor.GREEN : NamedTextColor.YELLOW))
					.append(Component.text("  (默认 " + def + ")", NamedTextColor.DARK_GRAY));
			sender.sendMessage(line);
		}
		sender.sendMessage(Component.text("提示：黄色数字代表已被覆盖默认值。", NamedTextColor.GRAY));
	}

	// ============================================================
	// set <map> <key> <value>
	// ============================================================
	private void handleSet(CommandSender sender, String[] args) {
		if (args.length < 3) {
			sender.sendMessage(Component.text("用法: /hide score set <地图> <key> <value>", NamedTextColor.RED));
			sender.sendMessage(Component.text("  可用 key 见 /hide score list <地图>", NamedTextColor.GRAY));
			return;
		}
		String mapName = args[0];
		String key = args[1].toLowerCase(Locale.ROOT);
		String valueStr = args[2];

		if (configManager.getArenaFile(mapName) == null) {
			sender.sendMessage(Component.text("找不到地图配置: " + mapName, NamedTextColor.RED));
			return;
		}
		if (!ScoringConfig.isValidKey(key)) {
			sender.sendMessage(Component.text("未知积分项: " + key, NamedTextColor.RED));
			sender.sendMessage(Component.text("可用: " + String.join(", ", ScoringConfig.ALL_KEYS), NamedTextColor.GRAY));
			return;
		}

		int value;
		try {
			value = Integer.parseInt(valueStr);
		} catch (NumberFormatException e) {
			sender.sendMessage(Component.text("value 必须是整数。", NamedTextColor.RED));
			return;
		}
		if (key.equals(ScoringConfig.KEY_HIDER_SURVIVAL_INTERVAL) && value < 1) {
			sender.sendMessage(Component.text(ScoringConfig.KEY_HIDER_SURVIVAL_INTERVAL + " 必须 >= 1（秒）。", NamedTextColor.RED));
			return;
		}
		if (value < 0) {
			sender.sendMessage(Component.text("积分值不能为负数。", NamedTextColor.RED));
			return;
		}

		boolean ok = configManager.saveScoring(mapName, key, value);
		if (!ok) {
			sender.sendMessage(Component.text("写入失败，查看控制台日志。", NamedTextColor.RED));
			return;
		}

		gameManager.reloadTemplatesOnly();
		sender.sendMessage(Component.text("✔ 已更新 ", NamedTextColor.GREEN)
				.append(Component.text(mapName + "." + key, NamedTextColor.AQUA))
				.append(Component.text(" = ", NamedTextColor.WHITE))
				.append(Component.text(String.valueOf(value), NamedTextColor.YELLOW)));
		sender.sendMessage(Component.text("  ⚠ 已对局中的房间不受影响，下一局生效。", NamedTextColor.GRAY));
	}

	// ============================================================
	// reset <map>
	// ============================================================
	private void handleReset(CommandSender sender, String[] args) {
		if (args.length < 1) {
			sender.sendMessage(Component.text("用法: /hide score reset <地图>", NamedTextColor.RED));
			return;
		}
		String mapName = args[0];
		if (configManager.getArenaFile(mapName) == null) {
			sender.sendMessage(Component.text("找不到地图配置: " + mapName, NamedTextColor.RED));
			return;
		}

		boolean ok = configManager.resetScoring(mapName);
		if (!ok) {
			sender.sendMessage(Component.text("重置失败，查看控制台日志。", NamedTextColor.RED));
			return;
		}

		gameManager.reloadTemplatesOnly();
		sender.sendMessage(Component.text("✔ 已重置 " + mapName + " 的所有积分项为默认值。", NamedTextColor.GREEN));
	}

	// ============================================================
	// Helpers
	// ============================================================
	private void sendActionHelp(CommandSender sender) {
		sender.sendMessage(Component.text("=== /hide score 用法 ===", NamedTextColor.GOLD));
		sender.sendMessage(Component.text("/hide score list  <地图>", NamedTextColor.AQUA));
		sender.sendMessage(Component.text("/hide score set   <地图> <key> <value>", NamedTextColor.AQUA));
		sender.sendMessage(Component.text("/hide score reset <地图>", NamedTextColor.AQUA));
	}

	@Override
	public List<String> tabComplete(CommandSender sender, String[] args) {
		if (args.length == 1) {
			return StringUtil.copyPartialMatches(args[0], ACTIONS, new ArrayList<>());
		}

		String action = args[0].toLowerCase(Locale.ROOT);

		if (args.length == 2) {
			return StringUtil.copyPartialMatches(args[1], gameManager.getTemplates().keySet(), new ArrayList<>());
		}

		if (args.length == 3 && action.equals("set")) {
			return StringUtil.copyPartialMatches(args[2], ScoringConfig.ALL_KEYS, new ArrayList<>());
		}

		if (args.length == 4 && action.equals("set")) {
			String key = args[2].toLowerCase(Locale.ROOT);
			Integer def = ScoringConfig.DEFAULTS.get(key);
			if (def != null) {
				return Collections.singletonList(String.valueOf(def));
			}
		}

		return Collections.emptyList();
	}
}
