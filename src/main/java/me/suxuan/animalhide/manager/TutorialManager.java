package me.suxuan.animalhide.manager;

import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.ScoringConfig;
import me.suxuan.animalhide.skill.hider.HiderSkillSupport;
import me.suxuan.animalhide.tutorial.DemoStation;
import me.suxuan.animalhide.tutorial.QueueTutorialText;
import me.suxuan.animalhide.tutorial.TutorialDemo;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

/**
 * 大厅教程演示桩管理器。
 * <p>
 * 维护一组持久化的"技能展台"，每个展台由一个载体实体 + 一个 {@link TextDisplay} 头顶悬浮字组成，
 * 并按 {@link TutorialDemo#getAnimationIntervalTicks()} 的周期循环播放对应的技能特效。
 *
 * <h3>持久化</h3>
 * 数据保存在 {@code config.yml -> tutorial-stations}：
 * <pre>
 * tutorial-stations:
 *   &lt;random-id&gt;:
 *     demo: SAFE_TAUNT
 *     location: { ... }
 * </pre>
 * <p>
 * 旧版字段 {@code tutorial-npcs} 会在启动时被自动迁移到新结构（按 pig→SAFE_TAUNT、
 * sheep→RISKY_TAUNT、cow→FIREWORK_TAUNT、chicken→DANGEROUS_TAUNT 的对应关系），
 * 完成后旧字段会被清除。
 *
 * <h3>实体安全</h3>
 * 所有由该管理器生成的实体会被打上 {@link #ROOT_TAG} 标签。插件启动时会先扫描所有世界，
 * 清理掉残留的标签实体，确保 config 始终是唯一权威来源。
 */
public class TutorialManager {

	private static final String ROOT_TAG = "animalhide_tutorial";
	private static final String QUEUE_ROOT_TAG = "animalhide_tutorial_queue";
	private static final String CONFIG_KEY = "tutorial-stations";
	private static final String LEGACY_CONFIG_KEY = "tutorial-npcs";
	private static final Transformation SMALL_TEXT_TRANSFORMATION = new Transformation(
			new Vector3f(0f, 0f, 0f),
			new AxisAngle4f(),
			new Vector3f(0.68f, 0.68f, 0.68f),
			new AxisAngle4f()
	);
	private static final Display.Brightness BRIGHT_TEXT_BRIGHTNESS = new Display.Brightness(15, 15);

	/**
	 * 变身魔杖演示桩循环展示的伪装形态池。
	 */
	private static final EntityType[] DISGUISE_CYCLE_POOL = new EntityType[]{
			EntityType.PIG, EntityType.SHEEP, EntityType.COW, EntityType.CHICKEN,
			EntityType.RABBIT, EntityType.FOX, EntityType.WOLF, EntityType.CAT
	};

	private final AnimalHidePlugin plugin;
	private final Random random = new Random();
	private final Map<String, DemoStation> stations = new LinkedHashMap<>();
	private final Map<String, List<DemoStation>> queueStations = new HashMap<>();
	private final Map<String, List<QueueTutorialText>> queueTexts = new HashMap<>();
	private int disguiseCycleCursor = 0;

	public TutorialManager(AnimalHidePlugin plugin) {
		this.plugin = plugin;

		migrateLegacyConfig();
		cleanupOrphanEntities();
		loadStationsFromConfig();
		startAnimationLoop();
	}

	// =====================================================================
	// 对外 API
	// =====================================================================

	/**
	 * 在指定坐标放置一个新的演示桩并写入配置。
	 *
	 * @return 创建成功时返回新桩的 ID；失败返回 {@code null}
	 */
	public String spawn(Location loc, TutorialDemo demo) {
		String id = generateId();
		DemoStation station = createStation(id, demo, loc);
		if (station == null) return null;

		stations.put(id, station);
		saveStationToConfig(id, demo, loc);
		return id;
	}

	/**
	 * 清除以 {@code center} 为中心、半径 {@code radius} 内的所有演示桩。
	 *
	 * @return 实际清除的桩数量
	 */
	public int removeNear(Location center, double radius) {
		double r2 = radius * radius;
		List<String> toRemove = new ArrayList<>();
		for (Map.Entry<String, DemoStation> e : stations.entrySet()) {
			Location loc = e.getValue().getAnchor();
			if (loc.getWorld() != null && loc.getWorld().equals(center.getWorld())
					&& loc.distanceSquared(center) <= r2) {
				toRemove.add(e.getKey());
			}
		}
		for (String id : toRemove) removeById(id);
		return toRemove.size();
	}

	/**
	 * 一次性清空全部演示桩及对应配置。
	 */
	public void removeAll() {
		for (DemoStation s : stations.values()) s.destroy();
		stations.clear();
		plugin.getConfig().set(CONFIG_KEY, null);
		plugin.saveConfig();
	}

	/**
	 * 仅用于插件 onDisable，把活动实体清干净以免污染世界数据，但不修改 config。
	 */
	public void shutdown() {
		for (DemoStation s : stations.values()) s.destroy();
		stations.clear();
		for (String worldName : new ArrayList<>(queueStations.keySet())) {
			clearQueueTutorial(worldName);
		}
		for (String worldName : new ArrayList<>(queueTexts.keySet())) {
			clearQueueTutorial(worldName);
		}
	}

	public Collection<DemoStation> getStations() {
		return Collections.unmodifiableCollection(stations.values());
	}

	public void spawnQueueTutorial(Arena arena) {
		if (arena == null || arena.getCurrentWorld() == null) return;
		if (!arena.getTemplate().isQueueRoom()) return;
		if (!plugin.getConfigManager().isQueueTutorialEnabled()) {
			return;
		}

		String worldName = arena.getCurrentWorld().getName();
		clearQueueTutorial(worldName);

		org.bukkit.configuration.file.FileConfiguration arenaConfig = plugin.getConfigManager().getArenaConfigs().get(arena.getArenaName());
		if (arenaConfig == null) {
			return;
		}

		ConfigurationSection stationSec = arenaConfig.getConfigurationSection("tutorial.stations");
		if (stationSec != null) {
			List<DemoStation> createdStations = new ArrayList<>();
			for (String demoId : stationSec.getKeys(false)) {
				TutorialDemo demo = TutorialDemo.fromId(demoId);
				if (demo == null) {
					continue;
				}
				ConfigurationSection demoSec = stationSec.getConfigurationSection(demoId);
				Location loc = plugin.getConfigManager().getDynamicLocation(demoSec);
				if (loc == null) {
					continue;
				}
				loc.setWorld(arena.getCurrentWorld());
				DemoStation station = createStation("queue-" + worldName + "-" + demo.getId(), demo, loc, true, worldName);
				if (station != null) {
					createdStations.add(station);
				}
			}
			if (!createdStations.isEmpty()) {
				queueStations.put(worldName, createdStations);
			}
		}

		ConfigurationSection hintsSec = arenaConfig.getConfigurationSection("tutorial.hints");
		if (hintsSec != null) {
			List<QueueTutorialText> createdTexts = new ArrayList<>();
			for (String hintKey : hintsSec.getKeys(false)) {
				ConfigurationSection group = hintsSec.getConfigurationSection(hintKey);
				if (group == null) {
					continue;
				}
				for (String nodeId : group.getKeys(false)) {
					ConfigurationSection node = group.getConfigurationSection(nodeId);
					if (node == null) {
						continue;
					}
					Location loc = plugin.getConfigManager().getDynamicLocation(node);
					if (loc == null) {
						continue;
					}
					loc.setWorld(arena.getCurrentWorld());
					QueueTutorialText text = createQueueHintText(worldName, hintKey, nodeId, loc);
					if (text != null) {
						createdTexts.add(text);
					}
				}
			}
			if (!createdTexts.isEmpty()) {
				queueTexts.put(worldName, createdTexts);
			}
		}
	}

	public void clearQueueTutorial(World world) {
		if (world == null) return;
		clearQueueTutorial(world.getName());
	}

	public void clearQueueTutorial(String worldName) {
		List<DemoStation> stationList = queueStations.remove(worldName);
		if (stationList != null) {
			for (DemoStation station : stationList) station.destroy();
		}
		List<QueueTutorialText> textList = queueTexts.remove(worldName);
		if (textList != null) {
			for (QueueTutorialText text : textList) text.destroy();
		}
	}

	// =====================================================================
	// 创建载体 + 悬浮字
	// =====================================================================

	private DemoStation createStation(String id, TutorialDemo demo, Location loc) {
		return createStation(id, demo, loc, false, null);
	}

	private DemoStation createStation(String id, TutorialDemo demo, Location loc, boolean queueTutorial, String worldName) {
		World world = loc.getWorld();
		if (world == null) {
			plugin.getComponentLogger().warn("无法在 null 世界生成演示桩 {}", id);
			return null;
		}

		Entity carrier = spawnCarrier(loc, demo);
		if (carrier == null) {
			plugin.getComponentLogger().warn("生成演示桩 {} 的载体失败 (demo={})", id, demo.name());
			return null;
		}
		applyQueueTag(carrier, queueTutorial, worldName);

		TextDisplay hologram = spawnHologram(loc, demo, queueTutorial, worldName);
		return new DemoStation(id, demo, loc, carrier, hologram);
	}

	private Entity spawnCarrier(Location loc, TutorialDemo demo) {
		return switch (demo.getCarrier()) {
			case ANIMAL, ANIMAL_CYCLE -> spawnAnimalCarrier(loc, demo.getAnimalType());
			case ARMOR_STAND -> spawnArmorStandCarrier(loc, demo);
		};
	}

	private LivingEntity spawnAnimalCarrier(Location loc, EntityType type) {
		if (type == null || !type.isAlive()) return null;
		Entity raw = loc.getWorld().spawnEntity(loc, type);
		if (!(raw instanceof LivingEntity living)) {
			raw.remove();
			return null;
		}
		living.setAI(false);
		living.setInvulnerable(true);
		living.setSilent(true);
		living.setCollidable(false);
		living.setPersistent(true);
		living.setRemoveWhenFarAway(false);
		living.customName(null);
		living.setCustomNameVisible(false);
		living.addScoreboardTag(ROOT_TAG);
		return living;
	}

	private ArmorStand spawnArmorStandCarrier(Location loc, TutorialDemo demo) {
		ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class, as -> {
			as.setBasePlate(false);
			as.setArms(true);
			as.setInvulnerable(true);
			as.setGravity(false);
			as.setPersistent(true);
			as.setCanPickupItems(false);
			as.setCustomNameVisible(false);
			as.addScoreboardTag(ROOT_TAG);
		});

		EntityEquipment eq = stand.getEquipment();
		if (eq != null) {
			eq.setItemInMainHand(new ItemStack(demo.getHeldItem()));
			if (demo == TutorialDemo.SEEKER_KIT) {
				eq.setItemInOffHand(new ItemStack(Material.BOW));
			} else if (demo == TutorialDemo.HIDER_BOW) {
				eq.setItemInOffHand(new ItemStack(Material.ARROW, 5));
			}
		}

		// 默认姿势：手臂自然下垂；动画里再短暂修改
		stand.setRightArmPose(new EulerAngle(Math.toRadians(-10), 0, Math.toRadians(-5)));
		stand.setLeftArmPose(new EulerAngle(Math.toRadians(-10), 0, Math.toRadians(5)));
		return stand;
	}

	private TextDisplay spawnHologram(Location anchor, TutorialDemo demo, boolean queueTutorial, String worldName) {
		double yOffset = demo.getCarrier() == TutorialDemo.Carrier.ARMOR_STAND ? 2.55 : 2.05;
		Location loc = anchor.clone().add(0, yOffset, 0);

		TextDisplay td = loc.getWorld().spawn(loc, TextDisplay.class, t -> {
			t.setBillboard(Display.Billboard.CENTER);
			t.setAlignment(TextDisplay.TextAlignment.CENTER);
			t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
			t.setSeeThrough(true);
			t.setShadowed(true);
			t.setBrightness(BRIGHT_TEXT_BRIGHTNESS);
			t.setLineWidth(240);
			t.setViewRange(1.15f);
			t.setTransformation(SMALL_TEXT_TRANSFORMATION);
			t.setPersistent(true);
			t.addScoreboardTag(ROOT_TAG);
		});
		applyQueueTag(td, queueTutorial, worldName);

		String raw = String.join("\n",
				"§7【 " + demo.getCamp().getDisplayName() + " §7】",
				getDemoTitleLine(demo),
				getDemoSubTitleLine(demo),
				getDemoHintLine(demo)
		);
		td.text(LegacyComponentSerializer.legacySection().deserialize(raw));
		return td;
	}

	// =====================================================================
	// 持久化 & 旧数据迁移
	// =====================================================================

	private void saveStationToConfig(String id, TutorialDemo demo, Location loc) {
		ConfigurationSection root = plugin.getConfig().getConfigurationSection(CONFIG_KEY);
		if (root == null) root = plugin.getConfig().createSection(CONFIG_KEY);
		ConfigurationSection sec = root.createSection(id);
		sec.set("demo", demo.name());
		sec.set("location", loc);
		plugin.saveConfig();
	}

	private void loadStationsFromConfig() {
		ConfigurationSection root = plugin.getConfig().getConfigurationSection(CONFIG_KEY);
		if (root == null) return;

		for (String id : root.getKeys(false)) {
			String demoName = root.getString(id + ".demo");
			Location loc = root.getLocation(id + ".location");
			TutorialDemo demo = TutorialDemo.fromId(demoName);
			if (demo == null) {
				try {
					demo = TutorialDemo.valueOf(demoName);
				} catch (Exception ignored) {
					// fall through
				}
			}
			if (demo == null || loc == null || loc.getWorld() == null) {
				plugin.getComponentLogger().warn("跳过非法的教程演示桩配置: id={}, demo={}", id, demoName);
				continue;
			}
			DemoStation station = createStation(id, demo, loc);
			if (station != null) stations.put(id, station);
		}
		if (!stations.isEmpty()) {
			plugin.getComponentLogger().info("已加载 {} 个教程演示桩。", stations.size());
		}
	}

	/**
	 * 把旧 {@code tutorial-npcs} 数据迁移到新的 {@link #CONFIG_KEY}。
	 */
	private void migrateLegacyConfig() {
		ConfigurationSection legacy = plugin.getConfig().getConfigurationSection(LEGACY_CONFIG_KEY);
		if (legacy == null) return;

		int migrated = 0;
		for (String key : legacy.getKeys(false)) {
			String type = legacy.getString(key + ".type");
			Location loc = legacy.getLocation(key + ".location");
			TutorialDemo demo = TutorialDemo.fromLegacyType(type);
			if (demo == null || loc == null) continue;
			saveStationToConfig(generateId(), demo, loc);
			migrated++;
		}
		plugin.getConfig().set(LEGACY_CONFIG_KEY, null);
		plugin.saveConfig();
		if (migrated > 0) {
			plugin.getComponentLogger().warn("已将 {} 个旧版教程 NPC 迁移到新教程系统。", migrated);
		}
	}

	/**
	 * 启动时清理所有标签为 {@link #ROOT_TAG} 的残留实体（被强行 /kill、之前 crash 等场景）。
	 */
	private void cleanupOrphanEntities() {
		int total = 0;
		for (World w : Bukkit.getWorlds()) {
			for (Entity e : w.getEntities()) {
				if (e.getScoreboardTags().contains(ROOT_TAG) || e.getScoreboardTags().contains(QUEUE_ROOT_TAG)) {
					e.remove();
					total++;
				}
			}
		}
		if (total > 0) {
			plugin.getComponentLogger().info("已清理 {} 个旧的教程实体残留。", total);
		}
	}

	private void removeById(String id) {
		DemoStation s = stations.remove(id);
		if (s != null) s.destroy();
		ConfigurationSection root = plugin.getConfig().getConfigurationSection(CONFIG_KEY);
		if (root != null) {
			root.set(id, null);
			plugin.saveConfig();
		}
	}

	private QueueTutorialText createQueueHintText(String worldName, String hintKey, String nodeId, Location loc) {
		List<String> lines = new ArrayList<>();
		String title = plugin.getConfigManager().getQueueTutorialHintTitle(hintKey);
		if (title != null && !title.isBlank()) {
			lines.add(title);
		}
		lines.addAll(QueueTutorialText.normalizeLines(plugin.getConfigManager().getQueueTutorialHintTemplateLines(hintKey)));
		if (lines.isEmpty()) {
			return null;
		}

		TextDisplay display = loc.getWorld().spawn(loc, TextDisplay.class, t -> {
			t.setBillboard(plugin.getConfigManager().getQueueTutorialHintBillboard());
			t.setAlignment(plugin.getConfigManager().getQueueTutorialHintAlignment());
			t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
			t.setSeeThrough(true);
			t.setShadowed(true);
			t.setBrightness(BRIGHT_TEXT_BRIGHTNESS);
			t.setLineWidth(plugin.getConfigManager().getQueueTutorialHintLineWidth());
			t.setViewRange(Math.max(plugin.getConfigManager().getQueueTutorialHintViewRange(), 1.15f));
			t.setTransformation(SMALL_TEXT_TRANSFORMATION);
			t.setPersistent(true);
			t.addScoreboardTag(ROOT_TAG);
		});
		applyQueueTag(display, true, worldName);
		display.text(LegacyComponentSerializer.legacySection().deserialize(String.join("\n", lines)));
		return new QueueTutorialText(hintKey + "-" + nodeId, loc, display);
	}

	private void applyQueueTag(Entity entity, boolean queueTutorial, String worldName) {
		if (!queueTutorial || worldName == null || worldName.isBlank()) return;
		entity.addScoreboardTag(QUEUE_ROOT_TAG);
		entity.addScoreboardTag("animalhide_tutorial_instance_" + worldName);
	}

	private void spawnTemporaryPoopDisplay(DemoStation station, Location dropLoc, long lifetimeTicks) {
		ItemDisplay disp = dropLoc.getWorld().spawn(dropLoc, ItemDisplay.class, d -> {
			d.setItemStack(new ItemStack(Material.COCOA_BEANS));
			d.setBillboard(Display.Billboard.VERTICAL);
			d.addScoreboardTag(ROOT_TAG);
		});
		station.getExtras().add(disp);
		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			if (disp.isValid()) disp.remove();
			station.getExtras().remove(disp);
		}, lifetimeTicks);
	}

	private String getDemoTitleLine(TutorialDemo demo) {
		return plugin.getConfigManager().getTutorialDemoText(demo.getId(), "title", demo.getDefaultTitleLine());
	}

	private String getDemoSubTitleLine(TutorialDemo demo) {
		ScoringConfig scoring = getQueueScoringOrDefault();
		String queueArenaName = plugin.getConfigManager().getQueueTemplateName();
		return switch (demo) {
			case SAFE_TAUNT ->
					"§f可可豆  §7·  §eCD " + HiderSkillSupport.getPoopTauntCooldownSeconds(queueArenaName) + "s  §7·  §6+" + scoring.getTauntSafe() + "分";
			case RISKY_TAUNT ->
					"§f史莱姆球  §7·  §eCD " + HiderSkillSupport.getStinkyTauntCooldownSeconds(queueArenaName) + "s  §7·  §6+" + scoring.getTauntRisky() + "分";
			case FIREWORK_TAUNT ->
					"§f山羊角  §7·  §eCD " + HiderSkillSupport.getScreamTauntCooldownSeconds(queueArenaName) + "s  §7·  §6+" + scoring.getTauntFirework() + "分";
			case DANGEROUS_TAUNT ->
					"§f烟火之星  §7·  §eCD " + HiderSkillSupport.getPartyTauntCooldownSeconds(queueArenaName) + "s  §7·  §6+" + scoring.getTauntDangerous() + "分";
			default ->
					plugin.getConfigManager().getTutorialDemoText(demo.getId(), "subtitle", demo.getDefaultSubTitleLine());
		};
	}

	private String getDemoHintLine(TutorialDemo demo) {
		return switch (demo) {
			case FIREWORK_TAUNT -> "§7右键释放 · 尖叫大动静 + 彩色光柱直冲天空";
			case DANGEROUS_TAUNT -> "§c⚠ 连续显眼特效跟随自己 · 自身发光 5 秒";
			default -> plugin.getConfigManager().getTutorialDemoText(demo.getId(), "hint", demo.getDefaultHintLine());
		};
	}

	private ScoringConfig getQueueScoringOrDefault() {
		org.bukkit.configuration.file.FileConfiguration queueConfig = plugin.getConfigManager().getArenaConfigs().get(plugin.getConfigManager().getQueueTemplateName());
		if (queueConfig == null) return ScoringConfig.defaults();
		return ScoringConfig.from(queueConfig.getConfigurationSection("scoring"));
	}

	private String generateId() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	// =====================================================================
	// 动画循环
	// =====================================================================

	private void startAnimationLoop() {
		new BukkitRunnable() {
			@Override
			public void run() {
				long now = Bukkit.getCurrentTick();
				for (DemoStation s : stations.values()) {
					if (s.getCarrier() == null || !s.getCarrier().isValid()) continue;
					if (now < s.getNextAnimationTick()) continue;

					runAnimation(s);
					s.setNextAnimationTick(now + s.getDemo().getAnimationIntervalTicks());
				}
				for (List<DemoStation> list : queueStations.values()) {
					for (DemoStation s : list) {
						if (s.getCarrier() == null || !s.getCarrier().isValid()) continue;
						if (now < s.getNextAnimationTick()) continue;

						runAnimation(s);
						s.setNextAnimationTick(now + s.getDemo().getAnimationIntervalTicks());
					}
				}
			}
		}.runTaskTimer(plugin, 20L, 5L); // 每 5 tick (1/4 秒) 调度一次
	}

	private void runAnimation(DemoStation station) {
		switch (station.getDemo()) {
			case SAFE_TAUNT -> playSafeTaunt(station);
			case RISKY_TAUNT -> playRiskyTaunt(station);
			case FIREWORK_TAUNT -> playFireworkTaunt(station);
			case DANGEROUS_TAUNT -> playDangerousTaunt(station);
			case DISGUISE_WAND -> playDisguiseWand(station);
			case HIDER_BOW -> playHiderBow(station);
			case SEEKER_KIT -> playSeekerKit(station);
			case EXPLOSIVE_SHEEP -> playExplosiveSheep(station);
		}
	}

	// ---------------- 嘲讽演示 ----------------

	private void playSafeTaunt(DemoStation s) {
		Entity e = s.getCarrier();
		Location loc = e.getLocation().add(0, 0.3, 0);
		World world = loc.getWorld();
		world.spawnParticle(Particle.FALLING_DUST, loc.clone().add(0, 0.1, 0), 6, 0.18, 0.08, 0.18,
				Material.BROWN_MUSHROOM_BLOCK.createBlockData());
		world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, loc.clone().add(0, 0.2, 0), 3, 0.12, 0.08, 0.12, 0.01);
		world.playSound(loc, matchAmbientSound(e.getType(), Sound.ENTITY_PIG_AMBIENT), 1f, 1f);
		spawnTemporaryPoopDisplay(s, e.getLocation().add(0.45, 0.15, 0), 30L);
	}

	private void playRiskyTaunt(DemoStation s) {
		Entity e = s.getCarrier();
		Location loc = e.getLocation().add(0, 0.8, 0);
		World world = loc.getWorld();
		world.playSound(loc, matchAmbientSound(e.getType(), Sound.ENTITY_COW_AMBIENT), 1.2f, 0.9f);
		world.playSound(loc, Sound.ENTITY_SLIME_SQUISH, 1f, 0.7f);
		world.spawnParticle(Particle.FALLING_DUST, loc.clone().add(0, -0.5, 0), 6, 0.18, 0.08, 0.18,
				Material.BROWN_MUSHROOM_BLOCK.createBlockData());
		world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, loc.clone().add(0, -0.4, 0), 3, 0.12, 0.08, 0.12, 0.01);
		spawnTemporaryPoopDisplay(s, e.getLocation().add(0.45, 0.15, 0), 40L);

		new BukkitRunnable() {
			int ticks = 0;

			@Override
			public void run() {
				if (ticks >= 80 || !e.isValid()) {
					cancel();
					return;
				}
				Location at = e.getLocation().add(0, 0.8, 0);
				World w = at.getWorld();
				w.spawnParticle(Particle.DUST, at, 14, 0.55, 0.35, 0.55,
						new Particle.DustOptions(Color.fromRGB(121, 173, 84), 1.2f));
				w.spawnParticle(Particle.SNEEZE, at, 6, 0.4, 0.25, 0.4, 0.01);
				ticks += 10;
			}
		}.runTaskTimer(plugin, 0L, 10L);
	}

	private void playFireworkTaunt(DemoStation s) {
		Entity e = s.getCarrier();
		Location loc = e.getLocation();
		World world = loc.getWorld();
		world.playSound(loc, matchAmbientSound(e.getType(), Sound.ENTITY_GOAT_SCREAMING_PREPARE_RAM), 2f, 1.2f);
		world.playSound(loc, Sound.ENTITY_GOAT_SCREAMING_PREPARE_RAM, 1.2f, 1.4f);
		world.playSound(loc, Sound.BLOCK_BELL_RESONATE, 0.8f, 1.6f);

		new BukkitRunnable() {
			int lived = 0;

			@Override
			public void run() {
				if (lived >= 70 || !e.isValid()) {
					cancel();
					return;
				}
				Location at = e.getLocation();
				World w = at.getWorld();
				for (double y = 0.5; y <= 24.0; y += 0.75) {
					w.spawnParticle(Particle.DUST, at.clone().add(0, y, 0), 5, 0.09, 0.05, 0.09, 0,
							new Particle.DustOptions(Color.AQUA, 1.35f));
					w.spawnParticle(Particle.DUST, at.clone().add(0, y, 0), 4, 0.12, 0.05, 0.12, 0,
							new Particle.DustOptions(Color.fromRGB(180, 80, 255), 1.25f));
				}
				lived += 5;
			}
		}.runTaskTimer(plugin, 0L, 5L);
	}

	private void playDangerousTaunt(DemoStation s) {
		Entity e = s.getCarrier();
		Location loc = e.getLocation();
		World world = loc.getWorld();
		world.playSound(loc, matchAmbientSound(e.getType(), Sound.ENTITY_CHICKEN_AMBIENT), 2.2f, 1.1f);
		world.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.2f, 1.1f);
		world.playSound(loc, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.1f, 1.8f);
		world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.8f);
		e.setGlowing(true);
		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			if (e.isValid()) e.setGlowing(false);
		}, 60L);

		new BukkitRunnable() {
			int lived = 0;

			@Override
			public void run() {
				if (lived >= 120 || !e.isValid()) {
					cancel();
					return;
				}
				Location at = e.getLocation();
				World w = at.getWorld();
				w.spawnParticle(Particle.DUST, at.clone().add(0, 1.0, 0), 18, 0.8, 0.4, 0.8,
						new Particle.DustOptions(Color.FUCHSIA, 1.4f));
				w.spawnParticle(Particle.DUST, at.clone().add(0, 1.5, 0), 18, 0.8, 0.5, 0.8,
						new Particle.DustOptions(Color.YELLOW, 1.4f));
				w.spawnParticle(Particle.HAPPY_VILLAGER, at.clone().add(0, 1.2, 0), 8, 0.75, 0.45, 0.75, 0.01);
				w.spawnParticle(Particle.NOTE, at.clone().add(0, 2.0, 0), 6, 0.7, 0.4, 0.7, 1);
				for (double y = 0.5; y <= 3.5; y += 0.5) {
					w.spawnParticle(Particle.TOTEM_OF_UNDYING, at.clone().add(0, y, 0), 4, 0.12, 0.02, 0.12, 0.01);
				}
				lived += 8;
			}
		}.runTaskTimer(plugin, 0L, 8L);
	}

	// ---------------- 变身魔杖（载体循环切换） ----------------

	private void playDisguiseWand(DemoStation s) {
		Entity old = s.getCarrier();
		Location loc = old.getLocation();

		EntityType next;
		do {
			disguiseCycleCursor = (disguiseCycleCursor + 1) % DISGUISE_CYCLE_POOL.length;
			next = DISGUISE_CYCLE_POOL[disguiseCycleCursor];
		} while (old.getType() == next);

		old.remove();
		LivingEntity fresh = spawnAnimalCarrier(loc, next);
		s.setCarrier(fresh);

		loc.getWorld().playSound(loc, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.7f, 1.2f);
		loc.getWorld().spawnParticle(Particle.PORTAL, loc.clone().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.1);
	}

	// ---------------- 击退弓 ----------------

	private void playHiderBow(DemoStation s) {
		Entity e = s.getCarrier();
		Location origin = e.getLocation().add(0, 1.4, 0);
		Vector dir = e.getLocation().getDirection().setY(0).normalize();
		e.getWorld().playSound(origin, Sound.ENTITY_ARROW_SHOOT, 0.7f, 1f);

		// 短暂手臂前伸效果
		if (e instanceof ArmorStand stand) {
			stand.setRightArmPose(new EulerAngle(Math.toRadians(-90), 0, 0));
			Bukkit.getScheduler().runTaskLater(plugin, () -> {
				if (stand.isValid()) {
					stand.setRightArmPose(new EulerAngle(Math.toRadians(-10), 0, Math.toRadians(-5)));
				}
			}, 10L);
		}

		// 用粒子模拟箭轨迹，避免真实箭物理碰撞 + 拾取问题
		new BukkitRunnable() {
			int step = 0;

			@Override
			public void run() {
				if (step >= 20 || !e.isValid()) {
					cancel();
					return;
				}
				Location p = origin.clone().add(dir.clone().multiply(step * 0.5));
				e.getWorld().spawnParticle(Particle.CRIT, p, 2, 0.05, 0.05, 0.05, 0);
				step++;
			}
		}.runTaskTimer(plugin, 0L, 1L);
	}

	// ---------------- 寻找者装备 ----------------

	private void playSeekerKit(DemoStation s) {
		Entity e = s.getCarrier();
		if (!(e instanceof ArmorStand stand)) return;
		Location loc = stand.getLocation().add(0, 1.4, 0);

		stand.setRightArmPose(new EulerAngle(Math.toRadians(-90), 0, Math.toRadians(-10)));
		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			if (stand.isValid()) {
				stand.setRightArmPose(new EulerAngle(Math.toRadians(-10), 0, Math.toRadians(-5)));
			}
		}, 8L);

		loc.getWorld().playSound(loc, Sound.ITEM_TRIDENT_THROW, 0.6f, 1.4f);
		loc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc.clone().add(stand.getLocation().getDirection().multiply(1.2)), 1, 0, 0, 0, 0);
	}

	// ---------------- 爆炸绵羊 ----------------

	private void playExplosiveSheep(DemoStation s) {
		Entity e = s.getCarrier();
		Location standLoc = e.getLocation();
		Location sheepLoc = standLoc.clone().add(standLoc.getDirection().setY(0).normalize().multiply(1.6));

		Sheep sheep = (Sheep) sheepLoc.getWorld().spawnEntity(sheepLoc, EntityType.SHEEP);
		sheep.setAI(false);
		sheep.setInvulnerable(true);
		sheep.setSilent(true);
		sheep.setCollidable(false);
		sheep.setPersistent(false);
		sheep.setCustomName("§c§l即将爆炸...");
		sheep.setCustomNameVisible(true);
		sheep.addScoreboardTag(ROOT_TAG);
		s.getExtras().add(sheep);

		new BukkitRunnable() {
			int ticks = 30;
			boolean red = false;

			@Override
			public void run() {
				if (ticks <= 0 || !sheep.isValid()) {
					if (sheep.isValid()) {
						Location loc = sheep.getLocation();
						loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1);
						loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1f);
						sheep.remove();
					}
					s.getExtras().remove(sheep);
					cancel();
					return;
				}
				red = !red;
				sheep.setColor(red ? DyeColor.RED : DyeColor.WHITE);
				float pitch = 1.0f + ((30 - ticks) * 0.03f);
				sheep.getWorld().playSound(sheep.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, pitch);
				ticks -= 5;
			}
		}.runTaskTimer(plugin, 0L, 5L);
	}

	// ---------------- 工具 ----------------

	private Sound matchAmbientSound(EntityType type, Sound fallback) {
		try {
			return Sound.valueOf("ENTITY_" + type.name() + "_AMBIENT");
		} catch (IllegalArgumentException ignored) {
			return fallback;
		}
	}
}
