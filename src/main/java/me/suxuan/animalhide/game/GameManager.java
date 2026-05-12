package me.suxuan.animalhide.game;

import lombok.Getter;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.config.ConfigManager;
import me.suxuan.animalhide.manager.DisguiseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.*;

/**
 * 游戏核心管理器
 * 负责解析配置生成竞技场，控制游戏进程、倒计时与阵营分配
 */
public class GameManager {

	private final AnimalHidePlugin plugin;
	private final ConfigManager configManager;
	private final DisguiseManager disguiseManager;

	@Getter
	private final Map<String, Arena> arenas = new HashMap<>();

	public GameManager(AnimalHidePlugin plugin, ConfigManager configManager, DisguiseManager disguiseManager) {
		this.plugin = plugin;
		this.configManager = configManager;
		this.disguiseManager = disguiseManager;
		loadArenas();
	}

	/**
	 * 将配置文件中的地图数据转化为 Arena 对象存入内存
	 */
	private void loadArenas() {
		for (Map.Entry<String, FileConfiguration> entry : configManager.getArenaConfigs().entrySet()) {
			String name = entry.getKey();
			FileConfiguration config = entry.getValue();

			int minPlayers = config.getInt("settings.min-players", 2);
			int maxPlayers = config.getInt("settings.max-players", 12);
			Location waiting = configManager.getLocation(config.getConfigurationSection("locations.waiting-lobby"));
			Location hiderSpawn = configManager.getLocation(config.getConfigurationSection("locations.hider-spawn"));
			Location seekerSpawn = configManager.getLocation(config.getConfigurationSection("locations.seeker-spawn"));

			Arena arena = new Arena(this, name, minPlayers, maxPlayers, waiting, hiderSpawn, seekerSpawn);
			arenas.put(name, arena);
		}
		plugin.getComponentLogger().info("已成功初始化 {} 个游戏房间。", arenas.size());
	}

	/**
	 * 检查房间是否满足启动条件，如果满足则开始倒计时
	 */
	public void checkAndStartCountdown(Arena arena) {
		if (arena.getState() == GameState.WAITING && arena.getPlayers().size() >= arena.getMinPlayers()) {
			arena.setState(GameState.STARTING);
			startCountdownTask(arena);
		}
	}

	/**
	 * 倒计时
	 */
	private void startCountdownTask(Arena arena) {
		new BukkitRunnable() {
			int countdown = 10; // 默认 10 秒倒计时

			@Override
			public void run() {
				// 如果倒计时期间玩家退出导致人数不足，则取消倒计时
				if (arena.getPlayers().size() < arena.getMinPlayers()) {
					arena.setState(GameState.WAITING);
					arena.broadcast(Component.text("人数不足，取消倒计时...", NamedTextColor.RED));
					cancel();
					return;
				}

				if (countdown > 0) {
					// 使用 Paper 的 Title API 发送炫酷的屏幕居中倒计时
					Title title = Title.title(
							Component.text(countdown, NamedTextColor.AQUA),
							Component.text("游戏即将开始", NamedTextColor.YELLOW),
							Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(1), Duration.ofMillis(200))
					);

					for (UUID uuid : arena.getPlayers()) {
						Player p = Bukkit.getPlayer(uuid);
						if (p != null) p.showTitle(title);
					}
					countdown--;
				} else {
					// 倒计时结束，正式开始游戏
					startGame(arena);
					cancel();
				}
			}
		}.runTaskTimer(plugin, 0L, 20L); // 延迟0秒，每 20 Tick (1秒) 执行一次
	}

	/**
	 * 游戏正式开始：分配阵营，传送玩家，设置变身
	 */
	private void startGame(Arena arena) {
		arena.setState(GameState.PLAYING);

		List<UUID> playerList = new ArrayList<>(arena.getPlayers());
		Collections.shuffle(playerList); // 打乱玩家顺序，保证随机性

		// 默认抽取 1 名寻找者 (可以根据总人数写一个比例算法)
		UUID seekerUUID = playerList.getFirst();
		Player seeker = Bukkit.getPlayer(seekerUUID);

		if (seeker != null) {
			arena.getSeekers().add(seekerUUID);
			seeker.teleportAsync(arena.getSeekerSpawn());
			seeker.sendMessage(Component.text("你是寻找者！找出所有的动物！", NamedTextColor.RED));
			// TODO: 给寻找者添加失明效果 (Blindness) 和武器
		}

		// 其余玩家设为躲藏者
		List<String> allowedAnimals = configManager.getArenaConfigs().get(arena.getArenaName()).getStringList("allowed-animals");

		for (int i = 1; i < playerList.size(); i++) {
			UUID hiderUUID = playerList.get(i);
			Player hider = Bukkit.getPlayer(hiderUUID);
			if (hider != null) {
				arena.getHiders().add(hiderUUID);
				hider.teleportAsync(arena.getHiderSpawn());

				// 随机抽取一个允许的动物类型进行变身
				String randomAnimalStr = allowedAnimals.get(new Random().nextInt(allowedAnimals.size()));
				try {
					DisguiseType type = DisguiseType.valueOf(randomAnimalStr.toUpperCase());
					disguiseManager.disguisePlayer(hider, type);
				} catch (IllegalArgumentException e) {
					plugin.getComponentLogger().warn("未知的变身类型: " + randomAnimalStr);
				}

				hider.sendMessage(Component.text("你是躲藏者！快找个地方藏起来！", NamedTextColor.GREEN));
			}
		}
	}

	/**
	 * 结束指定房间的游戏，进行结算与数据清理
	 *
	 * @param arena  目标房间
	 * @param winner 获胜的阵营
	 */
	public void endGame(Arena arena, PlayerRole winner) {
		arena.setState(GameState.ENDING);

		Component winMessage;
		if (winner == PlayerRole.HIDER) {
			winMessage = Component.text("========================\n", NamedTextColor.GREEN)
					.append(Component.text("      躲藏者 获得了胜利！\n", NamedTextColor.YELLOW))
					.append(Component.text("========================", NamedTextColor.GREEN));
		} else {
			winMessage = Component.text("========================\n", NamedTextColor.RED)
					.append(Component.text("      寻找者 获得了胜利！\n", NamedTextColor.YELLOW))
					.append(Component.text("========================", NamedTextColor.RED));
		}
		arena.broadcast(winMessage);

		Location mainLobby = configManager.getLocation(configManager.getMainConfig().getConfigurationSection("main-lobby"));

		for (UUID uuid : arena.getPlayers()) {
			Player player = Bukkit.getPlayer(uuid);
			if (player != null) {
				disguiseManager.undisguisePlayer(player);

				player.getInventory().clear();
				player.setHealth(20.0);
				player.setFoodLevel(20);
				player.setFireTicks(0);
				player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));

				if (mainLobby != null) {
					player.teleportAsync(mainLobby);
				}
			}
		}

		arena.reset();
	}

	/**
	 * 根据房间名称获取房间
	 *
	 * @param name 房间名称
	 * @return 房间对象
	 */
	public Arena getArena(String name) {
		return arenas.get(name);
	}

	/**
	 * 根据玩家获取其所在的房间
	 */
	public Arena getArenaByPlayer(Player player) {
		for (Arena arena : arenas.values()) {
			if (arena.getPlayers().contains(player.getUniqueId())) {
				return arena;
			}
		}
		return null;
	}
}