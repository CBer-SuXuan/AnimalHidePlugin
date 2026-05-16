package me.suxuan.animalhide.game;

import lombok.Getter;
import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.config.ConfigManager;
import me.suxuan.animalhide.manager.DatabaseManager;
import me.suxuan.animalhide.manager.DisguiseManager;
import me.suxuan.slimearena.api.ArenaManager;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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
	private final ArenaManager slimeArenaManager;
	private final Location mainLobby;

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
		loadTemplates();
	}

	/**
	 * 加载所有的地图配置为 Template 图纸
	 */
	private void loadTemplates() {
		templates.clear();
		for (Map.Entry<String, org.bukkit.configuration.file.FileConfiguration> entry : configManager.getArenaConfigs().entrySet()) {
			String name = entry.getKey();
			org.bukkit.configuration.file.FileConfiguration config = entry.getValue();

			String templateName = config.getString("template-name", name);
			int minPlayers = config.getInt("settings.min-players", 2);
			int maxPlayers = config.getInt("settings.max-players", 12);

			Location waiting = configManager.getDynamicLocation(config.getConfigurationSection("locations.waiting-lobby"));
			Location hiderSpawn = configManager.getDynamicLocation(config.getConfigurationSection("locations.hider-spawn"));
			Location seekerSpawn = configManager.getDynamicLocation(config.getConfigurationSection("locations.seeker-spawn"));
			if (waiting == null || hiderSpawn == null || seekerSpawn == null) {
				plugin.getComponentLogger().error("竞技场 {} 缺少必要坐标配置，已跳过加载。", name);
				continue;
			}

			List<SpawnPoint> aiSpawns = new ArrayList<>();
			org.bukkit.configuration.ConfigurationSection spawnsSec = config.getConfigurationSection("locations.ai-spawns");
			if (spawnsSec != null) {
				for (String key : spawnsSec.getKeys(false)) {
					SpawnPoint point = configManager.getSpawnPoint(spawnsSec.getConfigurationSection(key));
					if (point != null) aiSpawns.add(point);
				}
			}
			int aiAnimalCount = config.getInt("settings.ai-animal-count", 30);

			ScoringConfig scoring = ScoringConfig.from(config.getConfigurationSection("scoring"));

			ArenaTemplate template = new ArenaTemplate(name, templateName, minPlayers, maxPlayers, waiting, hiderSpawn, seekerSpawn, aiSpawns, aiAnimalCount, scoring);
			templates.put(name, template);
			plugin.getComponentLogger().info("已加载竞技场模板: {}", name);
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
		String instanceName = template.getMapName() + "_" + UUID.randomUUID().toString().substring(0, 6);
		Arena newMatch = new Arena(this, template, instanceName);
		activeMatches.add(newMatch);

		plugin.getComponentLogger().info("玩家 {} 触发了匹配秒开，正在生成新对局: {}", player.getName(), instanceName);

		newMatch.addPlayer(player);

		slimeArenaManager.createArenaAsync(template.getTemplateName(), instanceName).thenAccept(world -> {
			Bukkit.getScheduler().runTask(plugin, () -> {
				newMatch.setCurrentWorld(world);
				newMatch.setState(GameState.WAITING);

				for (UUID uuid : newMatch.getPlayers()) {
					Player p = Bukkit.getPlayer(uuid);
					if (p != null) newMatch.teleportAndInitPlayer(p);
				}
			});
		}).exceptionally(ex -> {
			Bukkit.getScheduler().runTask(plugin, () -> {
				plugin.getComponentLogger().error("生成对局世界失败: {}", instanceName, ex);
				player.sendMessage(Component.text("服务器资源调度失败，请稍后再试！", NamedTextColor.RED));
				activeMatches.remove(newMatch);
			});
			return null;
		});
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
			int countdown = 30; // 默认 10 秒倒计时

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
					arena.setTimeLeft(countdown);
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

		int animalVotes = arena.getModeVoteCount(ArenaMode.ANIMAL);
		int monsterVotes = arena.getModeVoteCount(ArenaMode.MONSTER);

		if (monsterVotes > animalVotes) {
			arena.setArenaMode(ArenaMode.MONSTER);
		} else {
			arena.setArenaMode(ArenaMode.ANIMAL);
		}

		arena.broadcast(Component.text("投票结束！本局最终模式: ", NamedTextColor.YELLOW)
				.append(Component.text(arena.getArenaMode().getDisplayName(), NamedTextColor.GREEN)));

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
				.get(arena.getArenaName())
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
				.get(arena.getArenaName())
				.getInt("settings.preparation-time", 30);
		int hideTimeTicks = hideTime * 20;

		for (int i = 0; i < seekerCount; i++) {
			UUID seekerUUID = finalSeekerPool.get(i);
			Player seeker = Bukkit.getPlayer(seekerUUID);

			if (seeker != null) {
				arena.getSeekers().add(seekerUUID);
				arena.getOriginalSeekers().add(seekerUUID);
				setupSeeker(seeker, arena, hideTimeTicks);
			}
		}

		String listKey = (arena.getArenaMode() == ArenaMode.ANIMAL) ? "allowed-animals" : "allowed-monsters";
		List<String> allowedEntities = configManager.getArenaConfigs().get(arena.getArenaName()).getStringList(listKey);

		plugin.getAiSpawnManager().spawnAIEntities(arena, allowedEntities);

		for (int i = seekerCount; i < finalSeekerPool.size(); i++) {
			UUID hiderId = finalSeekerPool.get(i);
			Player hider = Bukkit.getPlayer(hiderId);
			if (hider != null) {
				arena.getHiders().add(hiderId);
				setupHider(hider, arena, allowedEntities);
			}
		}

		startHidePhaseTask(arena, hideTime);
	}

	private void setupSeeker(Player seeker, Arena arena, int hideTimeTicks) {
		seeker.teleportAsync(arena.getSeekerSpawn());
		seeker.sendMessage(Component.text("你是寻找者！找出所有的动物！", NamedTextColor.RED));

		equipSeeker(seeker);

		seeker.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0);
		seeker.getAttribute(Attribute.SNEAKING_SPEED).setBaseValue(0);
		seeker.getAttribute(Attribute.JUMP_STRENGTH).setBaseValue(0);
		seeker.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, hideTimeTicks + 100, 1, false, false, false));
	}

	/**
	 * 为寻找者发放专属装备
	 */
	public void equipSeeker(Player seeker) {
		seeker.getInventory().clear();

		ItemStack sword = new ItemStack(Material.WOODEN_SWORD);
		ItemMeta swordMeta = sword.getItemMeta();
		swordMeta.setUnbreakable(true);
		swordMeta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 3, true);
		sword.setItemMeta(swordMeta);
		seeker.getInventory().setItem(0, sword);

		ItemStack bow = new ItemStack(Material.BOW);
		ItemMeta bowMeta = bow.getItemMeta();
		bowMeta.setUnbreakable(true);
		bowMeta.addEnchant(org.bukkit.enchantments.Enchantment.INFINITY, 1, true);
		bowMeta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 3, true);
		bow.setItemMeta(bowMeta);
		seeker.getInventory().setItem(1, bow);

		ItemStack sheepTrap = new ItemStack(Material.SHEEP_SPAWN_EGG);
		ItemMeta trapMeta = sheepTrap.getItemMeta();
		trapMeta.displayName(Component.text("★ 爆炸绵羊 (右键释放) ★", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
		trapMeta.lore(List.of(
				Component.text("释放一只会爆炸的绵羊，清理周围的 AI 并伤害玩家！", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
				Component.text("冷却时间: 20 秒", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
		));
		sheepTrap.setItemMeta(trapMeta);
		seeker.getInventory().setItem(2, sheepTrap);

		seeker.getInventory().setItem(9, new ItemStack(Material.ARROW, 1));
	}

	/**
	 * 通用的击杀结算逻辑：处理躲藏者被发现并转化为寻找者
	 */
	public void processHiderFound(Arena arena, Player victim, Player seeker) {
		if (!arena.getHiders().contains(victim.getUniqueId())) return;

		arena.broadcast(Component.text("☠ ", NamedTextColor.GRAY)
				.append(Component.text(victim.getName(), NamedTextColor.RED))
				.append(Component.text(" 被 ", NamedTextColor.GRAY))
				.append(Component.text(seeker.getName(), NamedTextColor.AQUA))
				.append(Component.text(" 找到了！", NamedTextColor.GRAY)));

		arena.getHiders().remove(victim.getUniqueId());
		arena.getSeekers().add(victim.getUniqueId());

		int killScore = arena.getTemplate().getScoring().getSeekerKillHider();
		arena.addMatchScore(seeker.getUniqueId(), killScore); // 击杀得分，由该地图 scoring 配置决定
		arena.addMatchKill(seeker.getUniqueId()); // 记录 1 次击杀
		seeker.sendMessage(Component.text("击杀躲藏者！积分 +" + killScore, NamedTextColor.GREEN));

		// 恢复状态并传送
		victim.setHealth(20.0);
		disguiseManager.undisguisePlayer(victim);
		victim.teleportAsync(arena.getSeekerSpawn());
		victim.sendMessage(Component.text("你已经被发现！现在你加入了寻找者阵营！", NamedTextColor.YELLOW));

		// 给新变身的寻找者发装备
		equipSeeker(victim);

		// 检查游戏是否结束
		if (arena.getHiders().isEmpty()) {
			endGame(arena, PlayerRole.SEEKER);
		}
	}

	private void setupHider(Player hider, Arena arena, List<String> allowedAnimals) {
		hider.teleportAsync(arena.getHiderSpawn());

		List<Entity> aiList = arena.getAiAnimals();
		if (!aiList.isEmpty()) {
			Entity randomAi = aiList.get(new Random().nextInt(aiList.size()));
			disguiseManager.disguisePlayerAsEntity(hider, randomAi);
		}

		hider.sendMessage(Component.text("你是躲藏者！", NamedTextColor.GREEN));

		equipHider(hider);
	}

	/**
	 * 为躲藏者发放专属装备
	 */
	private void equipHider(Player hider) {
		// 1. 变身魔杖 (第 1 格，索引 0)
		ItemStack wand = new ItemStack(Material.BLAZE_ROD);
		ItemMeta wandMeta = wand.getItemMeta();
		wandMeta.displayName(Component.text("★ 变身魔杖 (右键生物) ★", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
		wand.setItemMeta(wandMeta);
		hider.getInventory().setItem(0, wand);

		// 2. 击退弓 (第 2 格，索引 1)
		ItemStack bow = new ItemStack(Material.BOW);
		ItemMeta bowMeta = bow.getItemMeta();
		// 新增：给弓加上炫酷的名字，提示玩家可以通过射击升级
		bowMeta.displayName(Component.text("★ 击退弓 (射击寻找者升级) ★", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
		bowMeta.setUnbreakable(true);
		bow.setItemMeta(bowMeta);
		hider.getInventory().setItem(1, bow);

		// 3. 安全嘲讽 (第 4 格，索引 3)
		ItemStack safeTaunt = new ItemStack(Material.PINK_DYE);
		ItemMeta safeMeta = safeTaunt.getItemMeta();
		safeMeta.displayName(Component.text("▶ 安全嘲讽 (CD: 5秒) ◀", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
		safeTaunt.setItemMeta(safeMeta);
		hider.getInventory().setItem(3, safeTaunt);

		// 4. 冒险嘲讽 (第 5 格，索引 4)
		ItemStack modTaunt = new ItemStack(Material.GLOWSTONE_DUST);
		ItemMeta modMeta = modTaunt.getItemMeta();
		modMeta.displayName(Component.text("▶ 冒险嘲讽 (CD: 15秒) ◀", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
		modTaunt.setItemMeta(modMeta);
		hider.getInventory().setItem(4, modTaunt);

		// 5. 烟花嘲讽 (第 6 格，索引 5)
		ItemStack fwTaunt = new ItemStack(Material.FIREWORK_ROCKET, 5);
		ItemMeta fwMeta = fwTaunt.getItemMeta();
		fwMeta.displayName(Component.text("▶ 烟花嘲讽 (CD: 15秒 | 限5次) ◀", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
		fwTaunt.setItemMeta(fwMeta);
		hider.getInventory().setItem(5, fwTaunt);

		// 6. 危险嘲讽 (第 7 格，索引 6)
		ItemStack dangTaunt = new ItemStack(Material.REDSTONE_TORCH);
		ItemMeta dangMeta = dangTaunt.getItemMeta();
		dangMeta.displayName(Component.text("▶ 危险嘲讽 (CD: 60秒) ◀", NamedTextColor.DARK_RED).decoration(TextDecoration.ITALIC, false));
		dangTaunt.setItemMeta(dangMeta);
		hider.getInventory().setItem(6, dangTaunt);

		// 7. 箭 x5 (第 9 格，索引 8)
		hider.getInventory().setItem(8, new ItemStack(Material.ARROW, 5));
	}

	/**
	 * 躲藏阶段倒计时任务
	 */
	private void startHidePhaseTask(Arena arena, int hideTimeSeconds) {
		new BukkitRunnable() {
			int timeLeft = hideTimeSeconds;

			@Override
			public void run() {
				if (arena.getState() != GameState.PLAYING) {
					cancel();
					return;
				}

				if (timeLeft > 0) {
					arena.setTimeLeft(timeLeft);
					// 动态更新两方阵营的 Action Bar 提示
					Component seekerText = Component.text("距离释放还有: " + timeLeft + " 秒", NamedTextColor.RED);
					for (UUID id : arena.getSeekers()) {
						Player p = Bukkit.getPlayer(id);
						if (p != null) p.sendActionBar(seekerText);
					}

					Component hiderText = Component.text("寻找者将在 " + timeLeft + " 秒后出动！", NamedTextColor.YELLOW);
					for (UUID id : arena.getHiders()) {
						Player p = Bukkit.getPlayer(id);
						if (p != null) p.sendActionBar(hiderText);
					}

					timeLeft--;
				} else {
					arena.broadcast(Component.text("⚔ 寻找者已出动！快跑！", NamedTextColor.DARK_RED));

					for (UUID seekerId : arena.getSeekers()) {
						Player s = Bukkit.getPlayer(seekerId);
						if (s != null) {

							s.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.1);
							s.getAttribute(Attribute.SNEAKING_SPEED).setBaseValue(0.3);
							s.getAttribute(Attribute.JUMP_STRENGTH).setBaseValue(0.42);
							s.removePotionEffect(PotionEffectType.BLINDNESS);

							// 屏幕中心大字警告
							s.showTitle(Title.title(
									Component.text("开始寻找！", NamedTextColor.RED),
									Component.text("找出所有动物！", NamedTextColor.YELLOW),
									Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(200))
							));
						}
					}

					cancel();

					int gameDuration = configManager.getArenaConfigs()
							.get(arena.getArenaName())
							.getInt("settings.game-duration", 300); // 默认 300 秒 (5分钟)

					startGameTimer(arena, gameDuration);
				}
			}
		}.runTaskTimer(plugin, 0L, 20L); // 每秒运行一次
	}

	/**
	 * 游戏主阶段倒计时任务
	 * 使用 BossBar 在屏幕上方显示剩余时间
	 */
	private void startGameTimer(Arena arena, int durationSeconds) {

		BossBar timeBar = BossBar.bossBar(
				Component.text("⏳ 游戏剩余时间: " + durationSeconds + " 秒", NamedTextColor.WHITE),
				1.0f,
				BossBar.Color.RED,
				BossBar.Overlay.PROGRESS
		);

		arena.setTimeBar(timeBar);
		for (UUID uuid : arena.getPlayers()) {
			Player p = Bukkit.getPlayer(uuid);
			if (p != null) p.showBossBar(timeBar);
		}

		ScoringConfig scoring = arena.getTemplate().getScoring();
		int survivalReward = scoring.getHiderSurvivalReward();
		int survivalInterval = scoring.getHiderSurvivalInterval();

		new BukkitRunnable() {
			int timeLeft = durationSeconds;

			@Override
			public void run() {
				if (arena.getState() != GameState.PLAYING) {
					cancel();
					return;
				}

				if (timeLeft > 0) {
					arena.setTimeLeft(timeLeft);
					float progress = (float) timeLeft / durationSeconds;
					timeBar.progress(progress);

					if (timeLeft <= 30) {
						timeBar.name(Component.text("⏳ 游戏剩余时间: " + timeLeft + " 秒", NamedTextColor.RED));
					} else {
						timeBar.name(Component.text("⏳ 游戏剩余时间: " + timeLeft + " 秒", NamedTextColor.WHITE));
					}

					// Bug 修复：首 tick 的 timeLeft == durationSeconds，若周期能整除（如 300 % 15 == 0）
					// 会立刻给躲藏者发一次潜行奖励——但寻找者还没出动。
					// 用 elapsed 代替 timeLeft 取模，并且 elapsed > 0 才发，杜绝零秒奖励。
					int elapsed = durationSeconds - timeLeft;
					if (survivalReward > 0 && elapsed > 0 && elapsed % survivalInterval == 0) {
						for (UUID hiderId : arena.getHiders()) {
							arena.addMatchScore(hiderId, survivalReward);
							Player hider = Bukkit.getPlayer(hiderId);
							if (hider != null) {
								hider.sendActionBar(Component.text("✔ 潜行存活奖励: 积分 +" + survivalReward, NamedTextColor.GREEN));
							}
						}
					}

					// 箭矢回补复用 elapsed，避免和潜行奖励一样的首秒触发问题
					if (elapsed > 0 && elapsed % 5 == 0) {
						for (UUID hiderId : arena.getHiders()) {
							Player hider = Bukkit.getPlayer(hiderId);
							if (hider != null) {
								ItemStack arrowItem = hider.getInventory().getItem(8);

								if (arrowItem == null || arrowItem.getType() == Material.AIR) {
									hider.getInventory().setItem(8, new ItemStack(Material.ARROW, 1));
								} else if (arrowItem.getType() == Material.ARROW && arrowItem.getAmount() < 5) {
									arrowItem.setAmount(arrowItem.getAmount() + 1);
								}
							}
						}
					}

					timeLeft--;
				} else {
					endGame(arena, PlayerRole.HIDER);
					cancel();
				}
			}
		}.runTaskTimer(plugin, 0L, 20L);
	}

	/**
	 * 结束指定房间的游戏，进行结算与数据清理
	 *
	 * @param arena  目标房间
	 * @param winner 获胜的阵营
	 */
	public void endGame(Arena arena, PlayerRole winner) {
		arena.setState(GameState.ENDING);

		Set<UUID> winners = (winner == PlayerRole.SEEKER) ? arena.getSeekers() : arena.getHiders();
		ScoringConfig scoring = arena.getTemplate().getScoring();
		if (winner == PlayerRole.SEEKER) {
			for (UUID u : arena.getSeekers()) {
				if (arena.getOriginalSeekers().contains(u)) {
					arena.addMatchScore(u, scoring.getSeekerWinOriginal()); // 初始母体寻找者
				} else {
					arena.addMatchScore(u, scoring.getSeekerWinInfected()); // 被抓后变节的感染者
				}
			}
		} else {
			for (UUID u : arena.getHiders()) {
				arena.addMatchScore(u, scoring.getHiderWin()); // 胜利的躲藏者
			}
		}

		String winnerMsg = winner == PlayerRole.SEEKER ? "§c寻找者" : "§a躲藏者";
		arena.broadcast(Component.text("=========================", NamedTextColor.YELLOW));
		arena.broadcast(Component.text("      游戏结束！ " + winnerMsg + " 获得了胜利！", NamedTextColor.GOLD));
		arena.broadcast(Component.text(""));

		// 2. 计算本局排名 (取出前三名)
		List<Map.Entry<UUID, Integer>> sortedScores = new ArrayList<>(arena.getMatchScores().entrySet());
		sortedScores.sort((a, b) -> b.getValue().compareTo(a.getValue())); // 降序排序

		arena.broadcast(Component.text("      【本局积分排行】", NamedTextColor.AQUA));
		for (int i = 0; i < Math.min(3, sortedScores.size()); i++) {
			UUID u = sortedScores.get(i).getKey();
			int score = sortedScores.get(i).getValue();
			Player p = Bukkit.getPlayer(u);
			String name = p != null ? p.getName() : "离线玩家";

			String rankColor = i == 0 ? "§6① " : (i == 1 ? "§e② " : "§7③ ");
			arena.broadcast(Component.text("      " + rankColor + name + " §f- §a" + score + " 分"));
		}
		arena.broadcast(Component.text("=========================", NamedTextColor.YELLOW));

		// 3. 将本局数据保存入库
		DatabaseManager db = AnimalHidePlugin.getInstance().getDatabaseManager();
		for (UUID uuid : arena.getPlayers()) {
			Player p = Bukkit.getPlayer(uuid);
			if (p == null) continue;
			int scoreEarned = arena.getMatchScores().getOrDefault(uuid, 0);
			int killsEarned = arena.getMatchKills(uuid);
			int winEarned = winners.contains(uuid) ? 1 : 0;

			// 异步入库
			db.addStatsAsync(uuid, p.getName(), scoreEarned, winEarned, killsEarned);
			p.sendMessage(Component.text("已结算入库：+" + scoreEarned + " 总积分", NamedTextColor.GRAY));
		}

		for (UUID uuid : arena.getPlayers()) {
			Player player = Bukkit.getPlayer(uuid);
			resetPlayerData(player, arena);
			updatePlayerVisibility(player);
		}

		destroyArenaMatch(arena);
	}

	/**
	 * 彻底销毁一个对局及其对应的 Slime 世界
	 */
	public void destroyArenaMatch(Arena match) {
		// 1. 从活跃对局列表中移除，停止一切该房间的业务逻辑
		activeMatches.remove(match);
		World oldWorld = match.getCurrentWorld();

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
		Location mainLobby = configManager.getLocation(configManager.getMainConfig().getConfigurationSection("main-lobby"));

		for (Arena arena : new ArrayList<>(activeMatches)) {
			if (arena.getState() != GameState.WAITING) {
				for (UUID uuid : arena.getPlayers()) {
					Player player = org.bukkit.Bukkit.getPlayer(uuid);
					if (player != null) {
						disguiseManager.undisguisePlayer(player);

						player.getInventory().clear();
						player.setHealth(20.0);
						player.setFoodLevel(20);
						player.setFireTicks(0);
						player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));

						if (mainLobby != null) {
							player.teleport(mainLobby);
						}
					}
				}
				destroyArenaMatch(arena);
			}
		}
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
		if (player != null) {
			resetPlayerDataWithoutLobby(player, arena);

			if (mainLobby != null) {
				player.teleportAsync(mainLobby);
			}
		}
	}

	public void resetPlayerDataWithoutLobby(Player player, Arena arena) {
		if (player != null) {
			if (arena.getTimeBar() != null)
				player.hideBossBar(arena.getTimeBar());

			disguiseManager.undisguisePlayer(player);

			player.setAllowFlight(false);
			player.setFlying(false);

			player.resetPlayerTime();
			player.getInventory().clear();
			player.setGameMode(GameMode.SURVIVAL);
			player.setHealth(20.0);
			player.setFoodLevel(20);
			player.setFireTicks(0);
			player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
		}
	}

	/**
	 * 更新玩家的可见性与 TAB 列表隔离
	 */
	public void updatePlayerVisibility(Player target) {
		Arena targetArena = getArenaByPlayer(target);
		boolean targetInActiveGame = (targetArena != null && targetArena.getState() == GameState.PLAYING);

		for (Player online : Bukkit.getOnlinePlayers()) {
			if (online.equals(target)) continue;

			Arena onlineArena = getArenaByPlayer(online);
			boolean onlineInActiveGame = (onlineArena != null && onlineArena.getState() == GameState.PLAYING);

			// --- 核心逻辑 ---
			// 1. 如果其中一方处于正在进行的对局中
			if (targetInActiveGame || onlineInActiveGame) {
				// 只有在同一个正在运行的房间，且符合旁观者逻辑时才可见
				if (targetArena == onlineArena) {
					// 这里保留你之前的旁观者逻辑
					boolean targetIsSpec = targetArena.getSpectators().contains(target.getUniqueId());
					boolean onlineIsSpec = onlineArena.getSpectators().contains(online.getUniqueId());

					if (targetIsSpec && !onlineIsSpec) {
						online.hidePlayer(plugin, target);
						target.showPlayer(plugin, online);
					} else if (!targetIsSpec && onlineIsSpec) {
						target.hidePlayer(plugin, online);
						online.showPlayer(plugin, target);
					} else {
						online.showPlayer(plugin, target);
						target.showPlayer(plugin, online);
					}
				} else {
					// 不在同一个房间，且有人在比赛，必须隐藏
					online.hidePlayer(plugin, target);
					target.hidePlayer(plugin, online);
				}
			}
			// 2. 如果双方都不在“比赛中”（即都在大厅、等待中或结算中）
			else {
				// 全员互相可见，回归大厅大家庭
				online.showPlayer(plugin, target);
				target.showPlayer(plugin, online);
			}
		}
	}
}