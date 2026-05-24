package me.suxuan.animalhide.game;

import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 每个地图独立的积分规则配置。
 * <p>
 * 通过 {@link #from(ConfigurationSection)} 从 arenas/&lt;map&gt;.yml 的 {@code scoring} 节读取，
 * 缺省项自动使用 {@link #DEFAULTS} 中的默认值，保证老地图无感升级。
 * <p>
 * 所有配置项的 yml key 都集中定义为 {@code KEY_xxx} 常量，便于在
 * {@code /hide score set/get/reset} 命令、tab 补全与持久化层之间共享。
 */
@Getter
public class ScoringConfig {

	// ============================================================
	// 配置项 key 常量（同时作为 yml 节点名）
	// ============================================================
	public static final String KEY_SEEKER_KILL_HIDER = "seeker-kill-hider";
	public static final String KEY_SEEKER_WIN_ORIGINAL = "seeker-win-original";
	public static final String KEY_SEEKER_WIN_INFECTED = "seeker-win-infected";

	public static final String KEY_HIDER_WIN = "hider-win";
	public static final String KEY_HIDER_SURVIVAL_REWARD = "hider-survival-reward";
	public static final String KEY_HIDER_SURVIVAL_INTERVAL = "hider-survival-interval";

	public static final String KEY_TAUNT_POOP = "taunt-poop";
	public static final String KEY_TAUNT_STINKY = "taunt-stinky";
	public static final String KEY_TAUNT_SCREAM = "taunt-scream";
	public static final String KEY_TAUNT_PARTY = "taunt-party";
	private static final String LEGACY_KEY_TAUNT_SAFE = "taunt-safe";
	private static final String LEGACY_KEY_TAUNT_RISKY = "taunt-risky";
	private static final String LEGACY_KEY_TAUNT_FIREWORK = "taunt-firework";
	private static final String LEGACY_KEY_TAUNT_DANGEROUS = "taunt-dangerous";
	public static final String KEY_SEEKER_PICKUP_POOP = "seeker-pickup-poop";

	/**
	 * 所有合法 key 列表，顺序即展示顺序，用于 list/tab 补全。
	 */
	public static final List<String> ALL_KEYS = List.of(
			KEY_SEEKER_KILL_HIDER,
			KEY_SEEKER_WIN_ORIGINAL,
			KEY_SEEKER_WIN_INFECTED,
			KEY_HIDER_WIN,
			KEY_HIDER_SURVIVAL_REWARD,
			KEY_HIDER_SURVIVAL_INTERVAL,
			KEY_TAUNT_POOP,
			KEY_TAUNT_STINKY,
			KEY_TAUNT_SCREAM,
			KEY_TAUNT_PARTY,
			KEY_SEEKER_PICKUP_POOP
	);

	/**
	 * 所有配置项的默认值，新加 key 必须同步登记在这里。
	 */
	public static final Map<String, Integer> DEFAULTS;

	static {
		Map<String, Integer> m = new LinkedHashMap<>();
		m.put(KEY_SEEKER_KILL_HIDER, 10);
		m.put(KEY_SEEKER_WIN_ORIGINAL, 20);
		m.put(KEY_SEEKER_WIN_INFECTED, 5);
		m.put(KEY_HIDER_WIN, 20);
		m.put(KEY_HIDER_SURVIVAL_REWARD, 1);
		m.put(KEY_HIDER_SURVIVAL_INTERVAL, 15);
		m.put(KEY_TAUNT_POOP, 2);
		m.put(KEY_TAUNT_STINKY, 4);
		m.put(KEY_TAUNT_SCREAM, 7);
		m.put(KEY_TAUNT_PARTY, 10);
		m.put(KEY_SEEKER_PICKUP_POOP, 1);
		DEFAULTS = Map.copyOf(m);
	}

	/**
	 * 人类可读的中文标签，仅用于 {@code /hide score list} 与提示消息展示。
	 */
	public static final Map<String, String> LABELS;

	static {
		Map<String, String> m = new LinkedHashMap<>();
		m.put(KEY_SEEKER_KILL_HIDER, "击杀躲藏者");
		m.put(KEY_SEEKER_WIN_ORIGINAL, "寻找者胜利-原始寻找者");
		m.put(KEY_SEEKER_WIN_INFECTED, "寻找者胜利-感染变节者");
		m.put(KEY_HIDER_WIN, "躲藏者胜利");
		m.put(KEY_HIDER_SURVIVAL_REWARD, "潜行存活奖励");
		m.put(KEY_HIDER_SURVIVAL_INTERVAL, "潜行结算间隔(秒)");
		m.put(KEY_TAUNT_POOP, "便便嘲讽");
		m.put(KEY_TAUNT_STINKY, "臭气嘲讽");
		m.put(KEY_TAUNT_SCREAM, "尖叫嘲讽");
		m.put(KEY_TAUNT_PARTY, "派对嘲讽");
		m.put(KEY_SEEKER_PICKUP_POOP, "寻找者拾取便便");
		LABELS = Map.copyOf(m);
	}

	// ============================================================
	// 字段
	// ============================================================
	private final int seekerKillHider;
	private final int seekerWinOriginal;
	private final int seekerWinInfected;
	private final int hiderWin;
	private final int hiderSurvivalReward;
	private final int hiderSurvivalInterval;
	private final int tauntSafe;
	private final int tauntRisky;
	private final int tauntFirework;
	private final int tauntDangerous;
	private final int seekerPickupPoop;

	private ScoringConfig(int seekerKillHider, int seekerWinOriginal, int seekerWinInfected,
	                      int hiderWin, int hiderSurvivalReward, int hiderSurvivalInterval,
	                      int tauntSafe, int tauntRisky, int tauntFirework, int tauntDangerous, int seekerPickupPoop) {
		this.seekerKillHider = seekerKillHider;
		this.seekerWinOriginal = seekerWinOriginal;
		this.seekerWinInfected = seekerWinInfected;
		this.hiderWin = hiderWin;
		this.hiderSurvivalReward = hiderSurvivalReward;
		// 间隔最小为 1，避免取模时除零
		this.hiderSurvivalInterval = Math.max(1, hiderSurvivalInterval);
		this.tauntSafe = tauntSafe;
		this.tauntRisky = tauntRisky;
		this.tauntFirework = tauntFirework;
		this.tauntDangerous = tauntDangerous;
		this.seekerPickupPoop = seekerPickupPoop;
	}

	/**
	 * 全字段默认值实例（地图未配置 scoring 节时返回）。
	 */
	public static ScoringConfig defaults() {
		return new ScoringConfig(
				DEFAULTS.get(KEY_SEEKER_KILL_HIDER),
				DEFAULTS.get(KEY_SEEKER_WIN_ORIGINAL),
				DEFAULTS.get(KEY_SEEKER_WIN_INFECTED),
				DEFAULTS.get(KEY_HIDER_WIN),
				DEFAULTS.get(KEY_HIDER_SURVIVAL_REWARD),
				DEFAULTS.get(KEY_HIDER_SURVIVAL_INTERVAL),
				DEFAULTS.get(KEY_TAUNT_POOP),
				DEFAULTS.get(KEY_TAUNT_STINKY),
				DEFAULTS.get(KEY_TAUNT_SCREAM),
				DEFAULTS.get(KEY_TAUNT_PARTY),
				DEFAULTS.get(KEY_SEEKER_PICKUP_POOP)
		);
	}

	/**
	 * 从 yml 的 {@code scoring} 节读取，未填项使用默认值。
	 */
	public static ScoringConfig from(ConfigurationSection section) {
		if (section == null) return defaults();
		return new ScoringConfig(
				section.getInt(KEY_SEEKER_KILL_HIDER, DEFAULTS.get(KEY_SEEKER_KILL_HIDER)),
				section.getInt(KEY_SEEKER_WIN_ORIGINAL, DEFAULTS.get(KEY_SEEKER_WIN_ORIGINAL)),
				section.getInt(KEY_SEEKER_WIN_INFECTED, DEFAULTS.get(KEY_SEEKER_WIN_INFECTED)),
				section.getInt(KEY_HIDER_WIN, DEFAULTS.get(KEY_HIDER_WIN)),
				section.getInt(KEY_HIDER_SURVIVAL_REWARD, DEFAULTS.get(KEY_HIDER_SURVIVAL_REWARD)),
				section.getInt(KEY_HIDER_SURVIVAL_INTERVAL, DEFAULTS.get(KEY_HIDER_SURVIVAL_INTERVAL)),
				readIntWithLegacy(section, KEY_TAUNT_POOP, LEGACY_KEY_TAUNT_SAFE),
				readIntWithLegacy(section, KEY_TAUNT_STINKY, LEGACY_KEY_TAUNT_RISKY),
				readIntWithLegacy(section, KEY_TAUNT_SCREAM, LEGACY_KEY_TAUNT_FIREWORK),
				readIntWithLegacy(section, KEY_TAUNT_PARTY, LEGACY_KEY_TAUNT_DANGEROUS),
				section.getInt(KEY_SEEKER_PICKUP_POOP, DEFAULTS.get(KEY_SEEKER_PICKUP_POOP))
		);
	}

	/**
	 * 校验 key 是否合法（用于命令处理）。
	 */
	public static boolean isValidKey(String key) {
		return ALL_KEYS.contains(key);
	}

	/**
	 * 通过 key 取当前值，未知 key 返回 null（不要给玩家用，仅供命令展示）。
	 */
	public Integer getByKey(String key) {
		return switch (key) {
			case KEY_SEEKER_KILL_HIDER -> seekerKillHider;
			case KEY_SEEKER_WIN_ORIGINAL -> seekerWinOriginal;
			case KEY_SEEKER_WIN_INFECTED -> seekerWinInfected;
			case KEY_HIDER_WIN -> hiderWin;
			case KEY_HIDER_SURVIVAL_REWARD -> hiderSurvivalReward;
			case KEY_HIDER_SURVIVAL_INTERVAL -> hiderSurvivalInterval;
			case KEY_TAUNT_POOP -> tauntSafe;
			case KEY_TAUNT_STINKY -> tauntRisky;
			case KEY_TAUNT_SCREAM -> tauntFirework;
			case KEY_TAUNT_PARTY -> tauntDangerous;
			case KEY_SEEKER_PICKUP_POOP -> seekerPickupPoop;
			default -> null;
		};
	}

	private static int readIntWithLegacy(ConfigurationSection section, String key, String legacyKey) {
		int defaultValue = DEFAULTS.get(key);
		if (section.contains(key)) {
			return section.getInt(key, defaultValue);
		}
		return section.getInt(legacyKey, defaultValue);
	}
}
