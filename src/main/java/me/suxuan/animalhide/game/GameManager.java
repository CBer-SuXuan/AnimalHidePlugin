package me.suxuan.animalhide.game;

import lombok.Getter;
import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.config.ConfigManager;
import me.suxuan.animalhide.game.runtime.*;
import me.suxuan.animalhide.manager.DisguiseManager;
import me.suxuan.slimearena.api.ArenaManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * 游戏核心管理器
 * 负责解析配置生成竞技场，控制游戏进程、倒计时与阵营分配
 */
public class GameManager {

	private final AnimalHidePlugin plugin;
	private final ConfigManager configManager;
	private final DisguiseManager disguiseManager;
	private final ArenaManager slimeArenaManager;
	private final Location mainLobby;
	private final PlayerStateService playerStateService;
	private final MatchResultService matchResultService;
	private final LobbyCountdownService lobbyCountdownService;
	private final MatchTimerService matchTimerService;
	@Getter
	private final RoleSetupService roleSetupService;
	private final RoleConversionService roleConversionService;

	@Getter
	private final Map<String, ArenaTemplate> templates = new HashMap<>();
	@Getter
	private final List<Arena> activeMatches = new ArrayList<>();

	public GameManager(AnimalHidePlugin plugin, ConfigManager configManager, DisguiseManager disguiseManager, ArenaManager slimeArenaManager) {
		this.plugin = plugin;
		this.configManager = configManager;
		this.disguiseManager = disguiseManager;
		this.slimeArenaManager = slimeArenaManager;
		this.mainLobby = configManager.getLocation(configManager.getMainConfig().getConfigurationSection("main-lobby"));
		this.playerStateService = new PlayerStateService(plugin, configManager, disguiseManager, mainLobby);
		this.matchResultService = new MatchResultService(plugin, configManager, playerStateService);
		this.lobbyCountdownService = new LobbyCountdownService(plugin, configManager, this::startGame);
		this.matchTimerService = new MatchTimerService(plugin, configManager, arena -> endGame(arena, PlayerRole.HIDER));
		this.roleSetupService = new RoleSetupService(disguiseManager, configManager);
		this.roleConversionService = new RoleConversionService(plugin, disguiseManager, roleSetupService, this::endGame);
		loadTemplates();
	}

	/**
	 * 加载所有的地图配置为 Template 图纸
	 */
	private void loadTemplates() {
		templates.clear();
		String queueTemplateName = configManager.getQueueTemplateName();
		for (Map.Entry<String, FileConfiguration> entry : configManager.getArenaConfigs().entrySet()) {
			String keyName = entry.getKey();
			FileConfiguration config = entry.getValue();

			String name = config.getString("name", keyName);
			String templateName = config.getString("template-name", keyName);
			int minPlayers = config.getInt("settings.min-players", 2);
			int maxPlayers = config.getInt("settings.max-players", 12);

			Location waiting = configManager.getDynamicLocation(config.getConfigurationSection("locations.waiting-lobby"));
			Location hiderSpawn = configManager.getDynamicLocation(config.getConfigurationSection("locations.hider-spawn"));
			Location seekerSpawn = configManager.getDynamicLocation(config.getConfigurationSection("locations.seeker-spawn"));
			if (waiting == null || hiderSpawn == null || seekerSpawn == null) {
				plugin.getComponentLogger().error("竞技场 {} 缺少必要坐标配置，已跳过加载。", keyName);
				continue;
			}

			List<SpawnPoint> aiSpawns = new ArrayList<>();
			ConfigurationSection spawnsSec = config.getConfigurationSection("locations.ai-spawns");
			if (spawnsSec != null) {
				for (String key : spawnsSec.getKeys(false)) {
					SpawnPoint point = configManager.getSpawnPoint(spawnsSec.getConfigurationSection(key));
					if (point != null) aiSpawns.add(point);
				}
			}
			int aiAnimalCount = config.getInt("settings.ai-animal-count", 30);

			ScoringConfig scoring = ScoringConfig.from(config.getConfigurationSection("scoring"));
			BlockRegion phaseWall = configManager.getPhaseWallRegion(config);
			boolean queueRoom = configManager.isQueueEnabled() && templateName.equalsIgnoreCase(queueTemplateName);

			ArenaTemplate template = new ArenaTemplate(keyName, name, templateName, queueRoom, minPlayers, maxPlayers, waiting, hiderSpawn, seekerSpawn, aiSpawns, phaseWall, aiAnimalCount, scoring);
			templates.put(name, template);
		}
	}

	/**
	 * 查询玩家当前所在的对局
	 */
	public Arena getArenaByPlayer(Player player) {
		for (Arena match : activeMatches) {
			if (match.getPlayers().contains(player.getUniqueId())) return match;
		}
		return null;
	}

	public void autoJoinQueue(Player player) {
		if (!configManager.isQueueEnabled()) {
			return;
		}
		if (getArenaByPlayer(player) != null) {
			return;
		}

		ArenaTemplate queueTemplate = templates.values().stream()
				.filter(ArenaTemplate::isQueueRoom)
				.findFirst()
				.orElse(null);
		if (queueTemplate == null) {
			player.sendMessage(Component.text("队列房模板未配置，无法自动匹配。", NamedTextColor.RED));
			return;
		}

		for (Arena match : activeMatches) {
			if (match.getTemplate().isQueueRoom()
					&& match.getPlayers().size() < match.getMaxPlayers()
					&& (match.getState() == GameState.WAITING || match.getState() == GameState.STARTING || match.getState() == GameState.ENDING)) {
				match.addPlayer(player);
				return;
			}
		}

		String instanceName = queueTemplate.getTemplateName() + "_" + UUID.randomUUID().toString().substring(0, 6);
		Arena queueMatch = new Arena(this, queueTemplate, instanceName);
		activeMatches.add(queueMatch);
		queueMatch.addPlayer(player);

		slimeArenaManager.createArenaAsync(queueTemplate.getTemplateName(), instanceName).thenAccept(world -> {
			Bukkit.getScheduler().runTask(plugin, () -> {
				if (!activeMatches.contains(queueMatch) || queueMatch.getPlayers().isEmpty()) {
					return;
				}
				queueMatch.setCurrentWorld(world);
				plugin.getTutorialManager().spawnQueueTutorial(queueMatch);
				queueMatch.setState(GameState.WAITING);
				initializeQueuePlayersSequentially(queueMatch, new ArrayList<>(queueMatch.getPlayers()));
			});
		}).exceptionally(ex -> {

			Bukkit.getScheduler().runTask(plugin, () -> abortArenaCreation(queueMatch, Component.text("队列房创建失败，请稍后再试！", NamedTextColor.RED)));
			return null;
		});
	}

	/**
	 * 匹配系统
	 */
	public void joinMatchmaking(Player player, String mapName) {
		if (getArenaByPlayer(player) != null) {
			player.sendMessage(Component.text("你已经在游戏中了！", NamedTextColor.RED));
			return;
		}

		ArenaTemplate template = templates.get(mapName);
		if (template == null) {
			player.sendMessage(Component.text("找不到名为 " + mapName + " 的地图！", NamedTextColor.RED));
			return;
		}

		// 1. 尝试寻找正在等待且未满的同一地图对局
		for (Arena match : activeMatches) {
			if (match.getTemplate().equals(template) && match.getPlayers().size() < match.getMaxPlayers()) {
				// ENDING 状态在此处代表世界正在生成中
				if (match.getState() == GameState.WAITING || match.getState() == GameState.STARTING || match.getState() == GameState.ENDING) {
					match.addPlayer(player);
					return;
				}
			}
		}

		// 2. 如果没有可用的房间，或者全都满了/在游戏中，秒开新房！
		String instanceName = template.getTemplateName() + "_" + UUID.randomUUID().toString().substring(0, 6);
		Arena newMatch = new Arena(this, template, instanceName);
		activeMatches.add(newMatch);

		plugin.getComponentLogger().info("玩家 {} 触发了匹配秒开，正在生成新对局: {}", player.getName(), instanceName);

		newMatch.addPlayer(player);

		slimeArenaManager.createArenaAsync(template.getTemplateName(), instanceName).thenAccept(world -> {
			Bukkit.getScheduler().runTask(plugin, () -> {
				if (!activeMatches.contains(newMatch) || newMatch.getPlayers().isEmpty()) {
					return;
				}
				newMatch.setCurrentWorld(world);
				newMatch.setState(GameState.WAITING);

				initializePlayersSequentially(newMatch, new ArrayList<>(newMatch.getPlayers()), 1L);
			});
		}).exceptionally(ex -> {
			Bukkit.getScheduler().runTask(plugin, () -> {
				plugin.getComponentLogger().error("生成对局世界失败: {}", instanceName, ex);
				abortArenaCreation(newMatch, Component.text("服务器资源调度失败，请稍后再试！", NamedTextColor.RED));
			});
			return null;
		});
	}

	private static final long QUEUE_INIT_INTERVAL_TICKS = 10L;
	private static final long QUEUE_REJOIN_INTERVAL_TICKS = 2L;

	private void initializeQueuePlayersSequentially(Arena arena, List<UUID> playerIds) {
		initializePlayersSequentially(arena, playerIds, QUEUE_INIT_INTERVAL_TICKS);
	}

	private void autoJoinQueueSequentially(List<UUID> playerIds, long intervalTicks) {
		if (playerIds == null || playerIds.isEmpty()) {
			return;
		}

		long delay = 0L;
		for (UUID uuid : playerIds) {
			Bukkit.getScheduler().runTaskLater(plugin, () -> {
				Player player = Bukkit.getPlayer(uuid);
				if (player == null || !player.isOnline()) {
					return;
				}
				autoJoinQueue(player);
			}, delay);
			delay += Math.max(1L, intervalTicks);
		}
	}

	private void initializePlayersSequentially(Arena arena, List<UUID> playerIds, long intervalTicks) {
		if (arena == null || playerIds == null || playerIds.isEmpty()) {
			return;
		}

		long delay = 0L;
		for (UUID uuid : playerIds) {
			Bukkit.getScheduler().runTaskLater(plugin, () -> {
				if (!activeMatches.contains(arena) || arena.getCurrentWorld() == null) {
					return;
				}
				if (!arena.getPlayers().contains(uuid)) {
					return;
				}
				Player player = Bukkit.getPlayer(uuid);
				if (player == null || !player.isOnline()) {
					return;
				}
				arena.teleportAndInitPlayer(player);
			}, delay);
			delay += Math.max(1L, intervalTicks);
		}
	}

	/**
	 * 销毁并重建一个小游戏动态世界
	 */
	public void rebuildArenaWorld(Arena arena) {
		arena.setState(GameState.ENDING);

		String instanceName = arena.getTemplate().getTemplateName() + "_" + UUID.randomUUID().toString().substring(0, 6);
		arena.setInstanceName(instanceName);

		plugin.getComponentLogger().info("正在通过 SlimeAPI 生成竞技场 {} (使用模板: {})...", arena.getArenaName(), arena.getTemplate().getTemplateName());

		slimeArenaManager.createArenaAsync(arena.getTemplate().getTemplateName(), instanceName).thenAccept(world -> {
			Bukkit.getScheduler().runTask(plugin, () -> {
				arena.setCurrentWorld(world);
				arena.setState(GameState.WAITING);
				plugin.getComponentLogger().info("✔ 竞技场 {} 世界生成完毕! (实例: {})", arena.getArenaName(), instanceName);
			});
		}).exceptionally(ex -> {
			plugin.getComponentLogger().error("✘ 竞技场 {} 生成失败!", arena.getArenaName(), ex);
			return null;
		});
	}

	public enum ForceStartResult {
		SUCCESS,
		WORLD_NOT_READY,
		ALREADY_PLAYING,
		ENDING,
		NO_PLAYERS
	}

	/**
	 * 是否可被管理员强行开始（等待/倒计时且世界已就绪、至少一名玩家）。
	 */
	public boolean canForceStart(Arena arena) {
		if (arena.getCurrentWorld() == null || arena.getPlayers().isEmpty()) {
			return false;
		}
		GameState state = arena.getState();
		return state == GameState.WAITING || state == GameState.STARTING;
	}

	/**
	 * 查找指定地图名、处于大厅阶段且可开始的对局（多个时取列表中第一个）。
	 */
	public Arena findLobbyArenaByMapName(String mapName) {
		for (Arena match : activeMatches) {
			if (match.getArenaName().equalsIgnoreCase(mapName) && canForceStart(match)) {
				return match;
			}
		}
		return null;
	}

	/**
	 * 管理员强行开始：跳过大厅倒计时与最少人数限制。
	 */
	public ForceStartResult forceStartGame(Arena arena) {
		if (arena.getCurrentWorld() == null) {
			return ForceStartResult.WORLD_NOT_READY;
		}
		if (arena.getPlayers().isEmpty()) {
			return ForceStartResult.NO_PLAYERS;
		}
		return switch (arena.getState()) {
			case WAITING, STARTING -> {
				startGame(arena);
				yield ForceStartResult.SUCCESS;
			}
			case PLAYING -> ForceStartResult.ALREADY_PLAYING;
			case ENDING -> ForceStartResult.ENDING;
		};
	}

	/**
	 * 检查房间是否满足启动条件，如果满足则开始倒计时；已在倒计时时根据人数刷新模式
	 */
	public void checkAndStartCountdown(Arena arena) {
		lobbyCountdownService.checkAndStartCountdown(arena);
	}

	/**
	 * 人数变化时刷新大厅倒计时（取消、切换长/短倒计时）
	 */
	public void refreshLobbyCountdown(Arena arena) {
		lobbyCountdownService.refreshLobbyCountdown(arena);
	}

	/**
	 * 游戏正式开始：分配阵营，传送玩家，设置变身
	 */
	private void startGame(Arena arena) {
		if (arena.getTemplate().isQueueRoom()) {
			startQueueDispatch(arena);
			return;
		}
		lobbyCountdownService.cancelLobbyCountdown(arena);
		arena.setFinalRevealActive(false);
		arena.setTauntUnlockedAtMillis(0L);
		arena.setState(GameState.PLAYING);

		int animalVotes = arena.getModeVoteCount(ArenaMode.ANIMAL);
		int monsterVotes = arena.getModeVoteCount(ArenaMode.MONSTER);

		if (monsterVotes > animalVotes) {
			arena.setArenaMode(ArenaMode.MONSTER);
		} else {
			arena.setArenaMode(ArenaMode.ANIMAL);
		}

//		arena.broadcast(Component.text("投票结束！本局最终模式: ", NamedTextColor.YELLOW)
//				.append(Component.text(arena.getArenaMode().getDisplayName(), NamedTextColor.GREEN)));

		List<UUID> players = new ArrayList<>(arena.getPlayers());

		long targetTime = (arena.getArenaMode() == ArenaMode.MONSTER) ? 13000L : 6000L;
		for (UUID uuid : players) {
			Player p = Bukkit.getPlayer(uuid);
			if (p != null) {
				p.setPlayerTime(targetTime, false);
			}
		}

		int total = players.size();
		double ratio = configManager.getArenaConfigs()
				.get(arena.getTemplate().getConfigKey())
				.getDouble("settings.seeker-ratio", 0.2);
		ratio = Math.clamp(ratio, 0.0, 1.0);
		int seekerCount = (int) Math.max(1, Math.floor(total * ratio));

		List<UUID> candidatesForSeeker = new ArrayList<>();
		List<UUID> noPreference = new ArrayList<>();
		List<UUID> forcedHiders = new ArrayList<>();

		for (UUID uuid : players) {
			PlayerRole pref = arena.getRolePreferences().get(uuid);
			if (pref == PlayerRole.SEEKER) candidatesForSeeker.add(uuid);
			else if (pref == PlayerRole.HIDER) forcedHiders.add(uuid);
			else noPreference.add(uuid);
		}

		// 随机洗牌以保证公平
		Collections.shuffle(candidatesForSeeker);
		Collections.shuffle(noPreference);
		Collections.shuffle(forcedHiders);

		// 组合备选池：优先想当寻找者的，其次无所谓的，最后实在不够再抽想当躲藏者的
		List<UUID> finalSeekerPool = new ArrayList<>();
		finalSeekerPool.addAll(candidatesForSeeker);
		finalSeekerPool.addAll(noPreference);
		finalSeekerPool.addAll(forcedHiders);
		seekerCount = Math.min(seekerCount, finalSeekerPool.size());

		int hideTime = configManager.getArenaConfigs()
				.get(arena.getTemplate().getConfigKey())
				.getInt("settings.preparation-time", 30);
		int hideTimeTicks = hideTime * 20;

		for (int i = 0; i < seekerCount; i++) {
			UUID seekerUUID = finalSeekerPool.get(i);
			Player seeker = Bukkit.getPlayer(seekerUUID);

			if (seeker != null) {
				arena.getSeekers().add(seekerUUID);
				arena.getOriginalSeekers().add(seekerUUID);
				roleSetupService.setupSeeker(seeker, arena, hideTimeTicks);
			}
		}

		String listKey = (arena.getArenaMode() == ArenaMode.ANIMAL) ? "allowed-animals" : "allowed-monsters";
		List<String> allowedEntities = configManager.getArenaConfigs().get(arena.getTemplate().getConfigKey()).getStringList(listKey);

		plugin.getAiSpawnManager().spawnAIEntities(arena, allowedEntities);

		for (int i = seekerCount; i < finalSeekerPool.size(); i++) {
			UUID hiderId = finalSeekerPool.get(i);
			Player hider = Bukkit.getPlayer(hiderId);
			if (hider != null) {
				arena.getHiders().add(hiderId);
				roleSetupService.setupHider(hider, arena, allowedEntities);
			}
		}

		arena.markMatchStart(players.size(), arena.getHiders().size(), arena.getSeekers().size());
		matchTimerService.startHidePhaseTask(arena, hideTime);
	}

	/**
	 * 寻找者击杀升级系统的最高等级（含 L1 起始等级）。
	 */
	public static final int MAX_SEEKER_LEVEL = RoleSetupService.MAX_SEEKER_LEVEL;

	/**
	 * 把击杀数换算成寻找者等级（每杀 1 只升 1 级，封顶 {@link #MAX_SEEKER_LEVEL}）。
	 */
	public static int seekerLevelOf(int kills) {
		return RoleSetupService.seekerLevelOf(kills);
	}

	/**
	 * 升到下一级所需的累计击杀数；已经满级返回 -1。
	 */
	public static int killsForNextLevel(int kills) {
		return RoleSetupService.killsForNextLevel(kills);
	}

	/**
	 * 为寻找者发放专属装备，并根据等级动态附魔与添加永久效果。
	 *
	 * <p>等级表（每杀 1 只动物升 1 级，最高 5 级）：
	 * <ul>
	 *   <li>L1 (0 杀): 石剑(无法破坏+击退I)、弓(无限+无法破坏)</li>
	 *   <li>L2 (1 杀): + 锋利I</li>
	 *   <li>L3 (2 杀): + 锋利II、弓+力量I、永久速度I</li>
	 *   <li>L4 (3 杀): + 锋利III、弓+力量II</li>
	 *   <li>L5 (4 杀, 满级): + 击退II、弓+力量III+冲击I、永久力量I</li>
	 * </ul>
	 */
	public void equipSeeker(Player seeker, int kills) {
		roleSetupService.equipSeeker(seeker, kills);
	}

	/**
	 * 寻找者击杀升级的统一入口：刷新装备 + 升级特效 + 满级提示。
	 * 调用约定：必须在 {@link Arena#addMatchKill(UUID)} 之后调用，使用最新的 matchKills 数。
	 */
	public void applySeekerLevelUp(Player seeker, Arena arena) {
		roleSetupService.applySeekerLevelUp(seeker, arena);
	}

	/**
	 * 通用的击杀结算逻辑：处理躲藏者被发现并转化为寻找者
	 */
	public void processHiderFound(Arena arena, Player victim, Player seeker) {
		roleConversionService.processHiderFound(arena, victim, seeker);
	}

	/**
	 * 躲藏者因非寻找者击杀途径失去生命后，直接转为寻找者（不计入击杀者积分）。
	 */
	public void processHiderEliminated(Arena arena, Player victim) {
		roleConversionService.processHiderEliminated(arena, victim);
	}

	/**
	 * 结束指定房间的游戏，进行结算与数据清理
	 *
	 * @param arena  目标房间
	 * @param winner 获胜的阵营
	 */
	public void endGame(Arena arena, PlayerRole winner) {
		matchResultService.endGame(arena, winner, finishedArena -> {
			autoJoinQueueSequentially(new ArrayList<>(finishedArena.getPlayers()), QUEUE_REJOIN_INTERVAL_TICKS);
			destroyArenaMatch(finishedArena);
		});
	}

	/**
	 * 彻底销毁一个对局及其对应的 Slime 世界
	 */
	public void destroyArenaMatch(Arena match) {
		match.clearMatchSettlement();
		plugin.getTauntTraceSupport().clearArena(match);
		match.openPhaseDoors();
		lobbyCountdownService.cancelLobbyCountdown(match);
		// 1. 从活跃对局列表中移除，停止一切该房间的业务逻辑
		activeMatches.remove(match);
		World oldWorld = match.getCurrentWorld();
		plugin.getTutorialManager().clearQueueTutorial(oldWorld);

		if (oldWorld != null) {
			String worldName = oldWorld.getName();
			plugin.getComponentLogger().info("对局结束，已交由 SlimeArenaAPI 处理临时世界 {} 的安全销毁...", worldName);

			// 2. 直接一行代码调用 API！(API 内部会自动处理传送、延迟和 WG 清理)
			slimeArenaManager.discardArenaAsync(oldWorld, mainLobby).thenRun(() -> {

				// 这里是 Future 完成后的回调，当这行代码执行时，世界已经 100% 被扬了
				plugin.getComponentLogger().info("✔ 躲猫猫对局 {} 的内存回收已彻底完成。", worldName);

			}).exceptionally(ex -> {

				// 捕捉并打印可能出现的极端报错
				plugin.getComponentLogger().error("✘ 躲猫猫世界 {} 内存回收失败！", worldName, ex);
				return null;

			});
		}
	}

	/**
	 * 只重新加载地图模板（不结束进行中的对局）。
	 * <p>
	 * 进行中的 {@link Arena} 实例继续持有旧 {@link ArenaTemplate} 引用，不受影响；
	 * 之后秒开的新房间会使用刷新后的模板。
	 */
	public void reloadTemplatesOnly() {
		configManager.loadConfigs();
		loadTemplates();
	}

	/**
	 * 重新加载所有配置文件与数据
	 */
	public void reload() {
		stop();

		activeMatches.clear();

		configManager.loadConfigs();

		loadTemplates();

		plugin.getComponentLogger().info(Component.text("插件配置与地图数据已成功重载！", NamedTextColor.GREEN));
	}

	/**
	 * 当服务器关闭或者插件重载时，强制结束所有正在进行的游戏
	 */
	public void stop() {
		for (Arena arena : new ArrayList<>(activeMatches)) {
			endGame(arena, PlayerRole.SPECTATOR);
		}
	}

	/**
	 * 紧急清理：用于服务器关闭或插件重载时，强制清理所有玩家状态
	 */
	public void emergencyCleanup() {
		playerStateService.emergencyCleanup(activeMatches, this::destroyArenaMatch);
	}

	/**
	 * 根据房间名称获取房间
	 *
	 * @param name 房间名称
	 * @return 房间对象
	 */
	public Arena getArena(String name) {
		for (Arena arena : activeMatches) {
			if (arena.getArenaName().equals(name)) {
				return arena;
			}
		}
		return null;
	}

	public void resetPlayerData(Player player, Arena arena) {
		playerStateService.resetPlayerData(player, arena);
	}

	public void resetPlayerDataWithoutLobby(Player player, Arena arena) {
		playerStateService.resetPlayerDataWithoutLobby(player, arena);
	}

	/**
	 * 世界生成失败或创建中止：重置所有排队玩家并销毁空房间。
	 */
	private void abortArenaCreation(Arena match, Component message) {
		activeMatches.remove(match);
		List<UUID> playersToRequeue = new ArrayList<>();
		for (UUID uuid : new ArrayList<>(match.getPlayers())) {
			Player p = Bukkit.getPlayer(uuid);
			if (p != null) {
				resetPlayerData(p, match);
				AnimalHidePlugin.getInstance().getScoreboardManager().removeBoard(p);
				p.sendMessage(message);
				playersToRequeue.add(uuid);
			}
		}
		autoJoinQueueSequentially(playersToRequeue, QUEUE_REJOIN_INTERVAL_TICKS);
		match.getPlayers().clear();
		destroyArenaMatch(match);
	}

	private void startQueueDispatch(Arena queueArena) {
		lobbyCountdownService.cancelLobbyCountdown(queueArena);
		List<ArenaTemplate> playableTemplates = templates.values().stream()
				.filter(template -> !template.isQueueRoom())
				.toList();
		if (playableTemplates.isEmpty()) {
			queueArena.broadcast(Component.text("暂无可用正式地图，队列房无法发车。", NamedTextColor.RED));
			queueArena.setState(GameState.WAITING);
			return;
		}

		ArenaTemplate selected = playableTemplates.get(new Random().nextInt(playableTemplates.size()));
		List<UUID> queuedPlayers = new ArrayList<>(queueArena.getPlayers());
		String instanceName = selected.getTemplateName() + "_" + UUID.randomUUID().toString().substring(0, 6);
		Arena match = new Arena(this, selected, instanceName);
		activeMatches.add(match);
		queueArena.broadcast(Component.text("正在随机前往地图: " + selected.getMapName(), NamedTextColor.GOLD));

		slimeArenaManager.createArenaAsync(selected.getTemplateName(), instanceName).thenAccept(world -> {
			Bukkit.getScheduler().runTask(plugin, () -> {
				if (!activeMatches.contains(match)) return;
				match.setCurrentWorld(world);
				match.setState(GameState.WAITING);
				for (UUID uuid : queuedPlayers) {
					Player player = Bukkit.getPlayer(uuid);
					if (player == null) continue;
					queueArena.getPlayers().remove(uuid);
					match.addPlayerSilently(player);
				}
				queueArena.getPlayers().clear();
				lobbyCountdownService.cancelLobbyCountdown(queueArena);
				queueArena.setState(GameState.ENDING);
				Bukkit.getScheduler().runTaskLater(plugin, () -> destroyArenaMatch(queueArena), 10L);
				if (!match.getPlayers().isEmpty()) {
					startGame(match);
				} else {
					destroyArenaMatch(match);
				}
			});
		}).exceptionally(ex -> {

			Bukkit.getScheduler().runTask(plugin, () -> {
				queueArena.broadcast(Component.text("正式地图创建失败，已返回队列等待。", NamedTextColor.RED));
				queueArena.setState(GameState.WAITING);
			});
			return null;
		});
	}

}