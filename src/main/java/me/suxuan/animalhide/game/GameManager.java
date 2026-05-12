package me.suxuan.animalhide.game;

import com.destroystokyo.paper.entity.ai.VanillaGoal;
import lombok.Getter;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.config.ConfigManager;
import me.suxuan.animalhide.manager.DisguiseManager;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
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
	private final Location mainLobby;

	@Getter
	private final Map<String, Arena> arenas = new HashMap<>();

	public GameManager(AnimalHidePlugin plugin, ConfigManager configManager, DisguiseManager disguiseManager) {
		this.plugin = plugin;
		this.configManager = configManager;
		this.disguiseManager = disguiseManager;
		this.mainLobby = configManager.getLocation(configManager.getMainConfig().getConfigurationSection("main-lobby"));
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
			Location pos1 = configManager.getLocation(config.getConfigurationSection("locations.pos1"));
			Location pos2 = configManager.getLocation(config.getConfigurationSection("locations.pos2"));
			int aiAnimalCount = config.getInt("settings.ai-animal-count", 10);

			Arena arena = new Arena(this, name, minPlayers, maxPlayers, waiting, hiderSpawn, seekerSpawn, pos1, pos2, aiAnimalCount);
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

		int hideTime = configManager.getArenaConfigs()
				.get(arena.getArenaName())
				.getInt("settings.preparation-time", 30);
		int hideTimeTicks = hideTime * 20;

		for (int i = 0; i < seekerCount; i++) {
			UUID seekerUUID = finalSeekerPool.get(i);
			Player seeker = Bukkit.getPlayer(seekerUUID);

			if (seeker != null) {
				arena.getSeekers().add(seekerUUID);
				setupSeeker(seeker, arena, hideTimeTicks);
			}
		}

		String listKey = (arena.getArenaMode() == ArenaMode.ANIMAL) ? "allowed-animals" : "allowed-monsters";
		List<String> allowedEntities = configManager.getArenaConfigs().get(arena.getArenaName()).getStringList(listKey);

		for (int i = seekerCount; i < finalSeekerPool.size(); i++) {
			UUID hiderId = finalSeekerPool.get(i);
			Player hider = Bukkit.getPlayer(hiderId);
			if (hider != null) {
				arena.getHiders().add(hiderId);
				setupHider(hider, arena, allowedEntities);
			}
		}

		spawnAIEntities(arena, allowedEntities);

		startHidePhaseTask(arena, hideTime);
	}

	/**
	 * 在地图区域内随机生成 AI 实体
	 */
	private void spawnAIEntities(Arena arena, List<String> entities) {
		Location pos1 = arena.getPos1();
		Location pos2 = arena.getPos2();
		if (pos1 == null || pos2 == null || pos1.getWorld() == null) return;

		org.bukkit.World world = pos1.getWorld();
		if (entities.isEmpty()) return;

		int count = arena.getAiAnimalCount();
		Random random = new Random();

		int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
		int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
		int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
		int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

		for (int i = 0; i < count; i++) {
			int randomX = minX + random.nextInt(maxX - minX + 1);
			int randomZ = minZ + random.nextInt(maxZ - minZ + 1);

			int highestY = world.getHighestBlockYAt(randomX, randomZ) + 1;
			float randomYaw = random.nextFloat() * 360f;
			Location spawnLoc = new Location(world, randomX + 0.5, highestY, randomZ + 0.5, randomYaw, 0);

			String animalStr = entities.get(random.nextInt(entities.size()));
			try {
				EntityType type = EntityType.valueOf(animalStr.toUpperCase());
				Entity entity = world.spawnEntity(spawnLoc, type);
				entity.setSilent(true);
				if (entity instanceof Mob mob) {
					Bukkit.getMobGoals().removeGoal(mob, VanillaGoal.LOOK_AT_PLAYER);
				}
				if (entity instanceof Ageable ageable) {
					ageable.setAdult();
				}
				if (entity instanceof Sheep sheep) {
					sheep.setColor(DyeColor.WHITE);
				} else if (entity instanceof Wolf wolf) {
					wolf.setVariant(Registry.WOLF_VARIANT.get(NamespacedKey.minecraft("pale")));
				} else if (entity instanceof Cat cat) {
					cat.setCatType(Registry.CAT_VARIANT.get(NamespacedKey.minecraft("tabby")));
				}
				arena.getAiAnimals().add(entity);

			} catch (IllegalArgumentException e) {
				plugin.getComponentLogger().warn("尝试生成未知的 AI 动物类型: {}", animalStr);
			}
		}
		plugin.getComponentLogger().info("已在竞技场 {} 生成了 {} 只 AI 动物。", arena.getArenaName(), count);
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

		// 基础护甲（可选，建议保留以增加对抗性）
		seeker.getInventory().setHelmet(new ItemStack(Material.IRON_HELMET));
		seeker.getInventory().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
		seeker.getInventory().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
		seeker.getInventory().setBoots(new ItemStack(Material.IRON_BOOTS));

		// 1. 木剑 (第 1 格，索引 0)
		ItemStack sword = new ItemStack(Material.WOODEN_SWORD);
		ItemMeta swordMeta = sword.getItemMeta();
		swordMeta.setUnbreakable(true);
		sword.setItemMeta(swordMeta);
		seeker.getInventory().setItem(0, sword);

		// 2. 弓 + 无限 (第 2 格，索引 1)
		ItemStack bow = new ItemStack(Material.BOW);
		ItemMeta bowMeta = bow.getItemMeta();
		bowMeta.setUnbreakable(true);
		bowMeta.addEnchant(org.bukkit.enchantments.Enchantment.INFINITY, 1, true);
		bow.setItemMeta(bowMeta);
		seeker.getInventory().setItem(1, bow);

		// 3. 爆炸陷阱 (第 3 格，索引 2)
		ItemStack trap = new ItemStack(Material.REDSTONE); // 【修改】改为红石
		ItemMeta trapMeta = trap.getItemMeta();
		trapMeta.displayName(Component.text("★ 爆炸陷阱 (右键释放) ★", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
		trapMeta.lore(List.of(
				Component.text("右键释放后 3 秒爆炸，消灭 5 格内所有躲藏者！", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
				Component.text("冷却时间: 30 秒", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
		));
		trap.setItemMeta(trapMeta);
		seeker.getInventory().setItem(2, trap);

		// 4. 一根箭 (用于支持无限弓)
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

		String randomAnimalStr = allowedAnimals.get(new Random().nextInt(allowedAnimals.size()));
		try {
			DisguiseType type = DisguiseType.valueOf(randomAnimalStr.toUpperCase());
			disguiseManager.disguisePlayer(hider, type);
		} catch (IllegalArgumentException e) {
			plugin.getComponentLogger().warn("未知的变身类型: {}", randomAnimalStr);
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
		wandMeta.displayName(Component.text("★ 变身魔杖 (右键场上生物) ★", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
		wand.setItemMeta(wandMeta);
		hider.getInventory().setItem(0, wand);

		// 2. 弓 (第 2 格，索引 1)
		ItemStack bow = new ItemStack(Material.BOW);
		ItemMeta bowMeta = bow.getItemMeta();
		bowMeta.setUnbreakable(true);
		bow.setItemMeta(bowMeta);
		hider.getInventory().setItem(1, bow);

		// 3. 安全嘲讽 (第 4 格，索引 3)
		ItemStack safeTaunt = new ItemStack(Material.PINK_DYE);
		ItemMeta safeMeta = safeTaunt.getItemMeta();
		safeMeta.displayName(Component.text("▶ 安全嘲讽 (微光特效)", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
		safeTaunt.setItemMeta(safeMeta);
		hider.getInventory().setItem(3, safeTaunt);

		// 4. 较为危险的嘲讽 (第 5 格，索引 4)
		ItemStack modTaunt = new ItemStack(Material.GLOWSTONE_DUST);
		ItemMeta modMeta = modTaunt.getItemMeta();
		modMeta.displayName(Component.text("▶ 发光嘲讽 (透视自身3秒)", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
		modTaunt.setItemMeta(modMeta);
		hider.getInventory().setItem(4, modTaunt);

		// 5. 烟花嘲讽 (第 6 格，索引 5)
		ItemStack fwTaunt = new ItemStack(Material.FIREWORK_ROCKET);
		ItemMeta fwMeta = fwTaunt.getItemMeta();
		fwMeta.displayName(Component.text("▶ 烟花嘲讽 (发射烟花)", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
		fwTaunt.setItemMeta(fwMeta);
		hider.getInventory().setItem(5, fwTaunt);

		// 6. 危险嘲讽 (第 7 格，索引 6)
		ItemStack dangTaunt = new ItemStack(Material.REDSTONE_TORCH);
		ItemMeta dangMeta = dangTaunt.getItemMeta();
		dangMeta.displayName(Component.text("▶ 危险嘲讽 (暴露位置+自身减速)", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
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

		Component winMessage;
		if (winner == PlayerRole.HIDER) {
			winMessage = Component.text("========================\n", NamedTextColor.GREEN)
					.append(Component.text("      躲藏者 获得了胜利！\n", NamedTextColor.YELLOW))
					.append(Component.text("========================", NamedTextColor.GREEN));
		} else if (winner == PlayerRole.SEEKER) {
			winMessage = Component.text("========================\n", NamedTextColor.RED)
					.append(Component.text("      寻找者 获得了胜利！\n", NamedTextColor.YELLOW))
					.append(Component.text("========================", NamedTextColor.RED));
		} else {
			winMessage = Component.text("========================\n", NamedTextColor.AQUA)
					.append(Component.text("      管理员强制结束了游戏！\n", NamedTextColor.YELLOW))
					.append(Component.text("========================", NamedTextColor.AQUA));
		}
		arena.broadcast(winMessage);

		for (UUID uuid : arena.getPlayers()) {
			Player player = Bukkit.getPlayer(uuid);
			resetPlayerData(player, arena);
		}

		arena.reset();
	}

	/**
	 * 重新加载所有配置文件与数据
	 */
	public void reload() {
		stop();

		arenas.clear();

		configManager.loadConfigs();

		loadArenas();

		plugin.getComponentLogger().info(Component.text("插件配置与地图数据已成功重载！", NamedTextColor.GREEN));
	}

	/**
	 * 当服务器关闭或者插件重载时，强制结束所有正在进行的游戏
	 */
	public void stop() {
		for (Arena arena : arenas.values()) {
			endGame(arena, PlayerRole.SPECTATOR);
		}
	}

	/**
	 * 紧急清理：用于服务器关闭或插件重载时，强制清理所有玩家状态
	 */
	public void emergencyCleanup() {
		Location mainLobby = configManager.getLocation(configManager.getMainConfig().getConfigurationSection("main-lobby"));

		for (Arena arena : arenas.values()) {
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
				arena.reset();
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
	 * 规则：只有在同一个房间的玩家才能互相看见（包括在 TAB 里）
	 */
	public void updatePlayerVisibility(Player target) {
		Arena targetArena = getArenaByPlayer(target);

		for (Player online : Bukkit.getOnlinePlayers()) {
			Arena onlineArena = getArenaByPlayer(online);

			// 如果两人在同一个房间，或者两人都在主城大厅 (arena 都为 null)
			if (targetArena == onlineArena) {
				online.showPlayer(plugin, target);
				target.showPlayer(plugin, online);
			} else {
				// 否则互相隐藏 (从画面和 TAB 中移除)
				online.hidePlayer(plugin, target);
				target.hidePlayer(plugin, online);
			}
		}
	}
}