package me.suxuan.animalhide.commands;

import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.config.ConfigManager;
import me.suxuan.animalhide.game.ArenaTemplate;
import me.suxuan.animalhide.game.GameManager;
import me.suxuan.animalhide.game.SpawnPoint;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 管理员打点子命令：/hide arena &lt;addspawn|removespawn|listspawns|tp|preview&gt; ...
 * <p>
 * 设计原则：
 * - 所有写入操作直接落盘到 arenas/&lt;map&gt;.yml，然后调用 {@link GameManager#reloadTemplatesOnly()}
 *   只刷新模板，不打断进行中的对局。
 * - 坐标以 player.getLocation() 为准，玩家应当处于该地图的 SlimeArena 实例世界中再执行。
 */
public class ArenaSubCommand implements SubCommand {

	private static final List<String> ACTIONS = Arrays.asList("addspawn", "removespawn", "listspawns", "tp", "preview");

	private final GameManager gameManager;
	private final ConfigManager configManager;

	public ArenaSubCommand(GameManager gameManager) {
		this.gameManager = gameManager;
		this.configManager = AnimalHidePlugin.getInstance().getConfigManager();
	}

	@Override
	public String getName() {
		return "arena";
	}

	@Override
	public String getUsage() {
		return "/hide arena <addspawn|removespawn|listspawns|tp|preview> ...";
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
			case "addspawn" -> handleAddSpawn(sender, subArgs);
			case "removespawn" -> handleRemoveSpawn(sender, subArgs);
			case "listspawns" -> handleListSpawns(sender, subArgs);
			case "tp" -> handleTp(sender, subArgs);
			case "preview" -> handlePreview(sender, subArgs);
			default -> sender.sendMessage(Component.text("未知子命令: " + action, NamedTextColor.RED));
		}
		return true;
	}

	// ============================================================
	// addspawn <map> <pointName> <types|*> [weight]
	// ============================================================
	private void handleAddSpawn(CommandSender sender, String[] args) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(Component.text("addspawn 必须由玩家执行（需要读取当前坐标）。", NamedTextColor.RED));
			return;
		}
		if (args.length < 3) {
			sender.sendMessage(Component.text("用法: /hide arena addspawn <地图名> <点位名> <types|*> [weight]", NamedTextColor.RED));
			sender.sendMessage(Component.text("  types 例: PIG,COW   |   * 表示继承全局 allowed-animals", NamedTextColor.GRAY));
			return;
		}

		String mapName = args[0];
		String pointName = args[1];
		String typesArg = args[2];
		double weight = 1.0;
		if (args.length >= 4) {
			try {
				weight = Double.parseDouble(args[3]);
				if (weight <= 0) {
					sender.sendMessage(Component.text("weight 必须 > 0。", NamedTextColor.RED));
					return;
				}
			} catch (NumberFormatException e) {
				sender.sendMessage(Component.text("weight 必须是数字。", NamedTextColor.RED));
				return;
			}
		}

		if (configManager.getArenaFile(mapName) == null) {
			sender.sendMessage(Component.text("找不到地图配置: " + mapName, NamedTextColor.RED));
			return;
		}

		List<String> types = null;
		if (!typesArg.equals("*")) {
			types = new ArrayList<>();
			for (String t : typesArg.split(",")) {
				String upper = t.trim().toUpperCase(Locale.ROOT);
				if (upper.isEmpty()) continue;
				try {
					EntityType.valueOf(upper);
					types.add(upper);
				} catch (IllegalArgumentException e) {
					sender.sendMessage(Component.text("未知的实体类型: " + upper, NamedTextColor.RED));
					return;
				}
			}
			if (types.isEmpty()) {
				sender.sendMessage(Component.text("types 不能为空（用 * 表示继承全局）。", NamedTextColor.RED));
				return;
			}
		}

		Location loc = player.getLocation();
		SpawnPoint point = new SpawnPoint(loc, types, weight);

		boolean ok = configManager.saveSpawnPoint(mapName, pointName, point);
		if (!ok) {
			sender.sendMessage(Component.text("保存失败！查看控制台日志。", NamedTextColor.RED));
			return;
		}

		gameManager.reloadTemplatesOnly();

		sender.sendMessage(Component.text("✔ 已写入点位 ", NamedTextColor.GREEN)
				.append(Component.text(mapName + "." + pointName, NamedTextColor.AQUA))
				.append(Component.text(String.format(" @ (%.1f, %.1f, %.1f)", loc.getX(), loc.getY(), loc.getZ()), NamedTextColor.GRAY)));
		sender.sendMessage(Component.text("  types = " + (types == null ? "* (继承全局)" : String.join(",", types))
				+ "   weight = " + weight, NamedTextColor.GRAY));
		sender.sendMessage(Component.text("  ⚠ 请确认你当前在 " + mapName + " 的 SlimeArena 实例世界中。", NamedTextColor.YELLOW));
	}

	// ============================================================
	// removespawn <map> <pointName>
	// ============================================================
	private void handleRemoveSpawn(CommandSender sender, String[] args) {
		if (args.length < 2) {
			sender.sendMessage(Component.text("用法: /hide arena removespawn <地图名> <点位名>", NamedTextColor.RED));
			return;
		}

		String mapName = args[0];
		String pointName = args[1];

		if (configManager.getArenaFile(mapName) == null) {
			sender.sendMessage(Component.text("找不到地图配置: " + mapName, NamedTextColor.RED));
			return;
		}

		boolean ok = configManager.removeSpawnPoint(mapName, pointName);
		if (!ok) {
			sender.sendMessage(Component.text("找不到点位 " + mapName + "." + pointName + "（或写入失败）。", NamedTextColor.RED));
			return;
		}

		gameManager.reloadTemplatesOnly();
		sender.sendMessage(Component.text("✔ 已删除点位 " + mapName + "." + pointName, NamedTextColor.GREEN));
	}

	// ============================================================
	// listspawns <map>
	// ============================================================
	private void handleListSpawns(CommandSender sender, String[] args) {
		if (args.length < 1) {
			sender.sendMessage(Component.text("用法: /hide arena listspawns <地图名>", NamedTextColor.RED));
			return;
		}
		String mapName = args[0];

		FileConfiguration config = configManager.getArenaConfigs().get(mapName);
		if (config == null) {
			sender.sendMessage(Component.text("找不到地图配置: " + mapName, NamedTextColor.RED));
			return;
		}

		ConfigurationSection sec = config.getConfigurationSection("locations.ai-spawns");
		if (sec == null || sec.getKeys(false).isEmpty()) {
			sender.sendMessage(Component.text(mapName + " 暂无任何 ai-spawns 点位。", NamedTextColor.YELLOW));
			return;
		}

		sender.sendMessage(Component.text("=== " + mapName + " 的 AI 生成点 ===", NamedTextColor.GOLD));
		for (String key : sec.getKeys(false)) {
			ConfigurationSection ps = sec.getConfigurationSection(key);
			if (ps == null) continue;
			double x = ps.getDouble("x");
			double y = ps.getDouble("y");
			double z = ps.getDouble("z");
			double weight = ps.getDouble("weight", 1.0);
			List<String> types = ps.isList("types") ? ps.getStringList("types") : null;

			Component line = Component.text(" • " + key, NamedTextColor.AQUA)
					.append(Component.text(String.format(" (%.1f, %.1f, %.1f)", x, y, z), NamedTextColor.GRAY))
					.append(Component.text("  weight=" + weight, NamedTextColor.YELLOW))
					.append(Component.text("  types=" + (types == null || types.isEmpty() ? "*" : String.join(",", types)), NamedTextColor.GREEN));
			sender.sendMessage(line);
		}
	}

	// ============================================================
	// tp <map> <pointName>  — 在当前世界传送到该相对坐标
	// ============================================================
	private void handleTp(CommandSender sender, String[] args) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(Component.text("tp 必须由玩家执行。", NamedTextColor.RED));
			return;
		}
		if (args.length < 2) {
			sender.sendMessage(Component.text("用法: /hide arena tp <地图名> <点位名>", NamedTextColor.RED));
			sender.sendMessage(Component.text("  请先确保你处于该地图的 SlimeArena 世界中。", NamedTextColor.GRAY));
			return;
		}

		String mapName = args[0];
		String pointName = args[1];

		FileConfiguration config = configManager.getArenaConfigs().get(mapName);
		if (config == null) {
			sender.sendMessage(Component.text("找不到地图配置: " + mapName, NamedTextColor.RED));
			return;
		}

		ConfigurationSection ps = config.getConfigurationSection("locations.ai-spawns." + pointName);
		if (ps == null) {
			sender.sendMessage(Component.text("找不到点位 " + mapName + "." + pointName, NamedTextColor.RED));
			return;
		}

		double x = ps.getDouble("x");
		double y = ps.getDouble("y");
		double z = ps.getDouble("z");
		Location target = new Location(player.getWorld(), x + 0.5, y, z + 0.5);
		player.teleportAsync(target);
		sender.sendMessage(Component.text("✔ 已传送到 " + mapName + "." + pointName, NamedTextColor.GREEN));
	}

	// ============================================================
	// preview <map>  — 在玩家当前世界用粒子柱标记所有点位
	// ============================================================
	private void handlePreview(CommandSender sender, String[] args) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(Component.text("preview 必须由玩家执行。", NamedTextColor.RED));
			return;
		}
		if (args.length < 1) {
			sender.sendMessage(Component.text("用法: /hide arena preview <地图名>", NamedTextColor.RED));
			return;
		}
		String mapName = args[0];
		ArenaTemplate template = gameManager.getTemplates().get(mapName);
		if (template == null) {
			sender.sendMessage(Component.text("找不到已加载的地图模板: " + mapName, NamedTextColor.RED));
			return;
		}

		List<SpawnPoint> points = template.getConfigAiSpawns();
		if (points == null || points.isEmpty()) {
			sender.sendMessage(Component.text(mapName + " 暂无任何 ai-spawns 点位。", NamedTextColor.YELLOW));
			return;
		}

		World world = player.getWorld();
		Particle.DustOptions dust = new Particle.DustOptions(Color.LIME, 1.5f);

		sender.sendMessage(Component.text("✔ 已在你当前世界对 " + points.size() + " 个点位画粒子柱（持续 10 秒）。", NamedTextColor.GREEN));
		sender.sendMessage(Component.text("  ⚠ 你必须处于该地图的 SlimeArena 世界中，柱子位置才会对得上。", NamedTextColor.YELLOW));

		new BukkitRunnable() {
			int ticks = 0;

			@Override
			public void run() {
				if (ticks >= 200) {
					cancel();
					return;
				}
				ticks += 4;
				for (SpawnPoint sp : points) {
					Location base = sp.getLocation();
					for (double y = 0; y < 3; y += 0.3) {
						world.spawnParticle(Particle.DUST, base.getX() + 0.5, base.getY() + y, base.getZ() + 0.5,
								1, 0, 0, 0, 0, dust);
					}
				}
			}
		}.runTaskTimer(AnimalHidePlugin.getInstance(), 0L, 4L);
	}

	// ============================================================
	// Helpers
	// ============================================================
	private void sendActionHelp(CommandSender sender) {
		sender.sendMessage(Component.text("=== /hide arena 用法 ===", NamedTextColor.GOLD));
		sender.sendMessage(Component.text("/hide arena addspawn <地图> <点位名> <types|*> [weight]", NamedTextColor.AQUA));
		sender.sendMessage(Component.text("/hide arena removespawn <地图> <点位名>", NamedTextColor.AQUA));
		sender.sendMessage(Component.text("/hide arena listspawns <地图>", NamedTextColor.AQUA));
		sender.sendMessage(Component.text("/hide arena tp <地图> <点位名>", NamedTextColor.AQUA));
		sender.sendMessage(Component.text("/hide arena preview <地图>", NamedTextColor.AQUA));
	}

	@Override
	public List<String> tabComplete(CommandSender sender, String[] args) {
		if (args.length == 1) {
			return StringUtil.copyPartialMatches(args[0], ACTIONS, new ArrayList<>());
		}

		String action = args[0].toLowerCase(Locale.ROOT);

		// arg[1] 永远是地图名
		if (args.length == 2) {
			return StringUtil.copyPartialMatches(args[1], gameManager.getTemplates().keySet(), new ArrayList<>());
		}

		// arg[2] 视命令而定
		if (args.length == 3) {
			switch (action) {
				case "addspawn" -> {
					return Collections.singletonList("<点位名>");
				}
				case "removespawn", "tp" -> {
					return StringUtil.copyPartialMatches(args[2], spawnPointKeysOf(args[1]), new ArrayList<>());
				}
				default -> {
					return Collections.emptyList();
				}
			}
		}

		// addspawn 的 arg[3] = types
		if (args.length == 4 && action.equals("addspawn")) {
			List<String> suggest = new ArrayList<>();
			suggest.add("*");
			for (EntityType t : EntityType.values()) {
				if (t.isAlive() && t != EntityType.PLAYER) suggest.add(t.name());
			}
			return StringUtil.copyPartialMatches(args[3], suggest, new ArrayList<>());
		}

		// addspawn 的 arg[4] = weight
		if (args.length == 5 && action.equals("addspawn")) {
			return Arrays.asList("1.0", "2.0", "3.0", "5.0");
		}

		return Collections.emptyList();
	}

	private List<String> spawnPointKeysOf(String mapName) {
		FileConfiguration config = configManager.getArenaConfigs().get(mapName);
		if (config == null) return Collections.emptyList();
		ConfigurationSection sec = config.getConfigurationSection("locations.ai-spawns");
		if (sec == null) return Collections.emptyList();
		return new ArrayList<>(sec.getKeys(false));
	}
}
