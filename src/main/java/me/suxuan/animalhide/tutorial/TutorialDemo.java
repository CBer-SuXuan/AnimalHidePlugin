package me.suxuan.animalhide.tutorial;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.Locale;

/**
 * 大厅教程的演示桩类型。
 * <p>
 * 每个常量代表一个独立的"技能演示展台"，包含载体形态、悬浮说明文字、手持物品、
 * 动画周期与触发动作。{@link me.suxuan.animalhide.manager.TutorialManager} 负责把它们渲染出来。
 */
public enum TutorialDemo {

	// ========== 躲藏者 - 嘲讽类 ==========
	SAFE_TAUNT(
			Camp.HIDER, Carrier.ANIMAL, EntityType.PIG, Material.PINK_DYE,
			"§a§l安全嘲讽",
			"§f粉色染料  §7·  §eCD 5s  §7·  §6+2分",
			"§7右键释放 · 卖萌音效与音符粒子",
			100L  // 5s，与游戏内 CD 一致
	),
	RISKY_TAUNT(
			Camp.HIDER, Carrier.ANIMAL, EntityType.COW, Material.GLOWSTONE_DUST,
			"§e§l冒险嘲讽",
			"§f荧石粉  §7·  §eCD 15s  §7·  §6+4分",
			"§7右键释放 · 喧闹音效 + 掉落便便",
			300L  // 15s
	),
	FIREWORK_TAUNT(
			Camp.HIDER, Carrier.ANIMAL, EntityType.SHEEP, Material.FIREWORK_ROCKET,
			"§6§l烟花嘲讽",
			"§f烟花火箭  §7·  §eCD 15s  §7·  §6+7分  §c·  限5次",
			"§7右键释放 · 升空炫彩烟花",
			300L  // 15s
	),
	DANGEROUS_TAUNT(
			Camp.HIDER, Carrier.ANIMAL, EntityType.CHICKEN, Material.REDSTONE_TORCH,
			"§c§l危险嘲讽",
			"§f红石火把  §7·  §eCD 60s  §7·  §6+10分",
			"§c⚠ 暴露大致坐标 · 10秒禁变身 · 附加加速",
			400L  // 20s，演示比真实CD短一点观感更好
	),

	// ========== 躲藏者 - 工具/武器 ==========
	DISGUISE_WAND(
			Camp.HIDER, Carrier.ANIMAL_CYCLE, EntityType.RABBIT, Material.BLAZE_ROD,
			"§b§l变身魔杖",
			"§f烈焰棒  §7·  §a右键生物切换伪装",
			"§7游戏开始后 · 你将能模仿任意允许的生物",
			80L  // 4s 切换一次外观
	),
	HIDER_BOW(
			Camp.HIDER, Carrier.ARMOR_STAND, null, Material.BOW,
			"§9§l击退弓",
			"§f弓 + 自动补给箭  §7·  §a击退寻找者 · 命中升级",
			"§7游戏开始后 · 每 5 秒补一支箭 · 最多 5 支",
			120L  // 6s 演示射箭
	),

	// ========== 寻找者 ==========
	SEEKER_KIT(
			Camp.SEEKER, Carrier.ARMOR_STAND, null, Material.WOODEN_SWORD,
			"§c§l寻找者装备",
			"§f木剑 + 无限弓  §7·  §a追杀躲藏者",
			"§7游戏开始前会被致盲与定身 · 解除后开始狩猎",
			80L  // 4s 挥剑
	),
	EXPLOSIVE_SHEEP(
			Camp.SEEKER, Carrier.ARMOR_STAND, null, Material.SHEEP_SPAWN_EGG,
			"§c§l爆炸绵羊",
			"§f绵羊蛋  §7·  §eCD 20s  §7·  §a清理AI · 伤害躲藏者",
			"§7右键释放 · 30tick 倒计时后炸开 · 范围 6 格",
			160L  // 8s 演示一次爆炸
	);

	public enum Camp {
		HIDER("§a躲藏者"),
		SEEKER("§c寻找者");

		private final String displayName;

		Camp(String displayName) {
			this.displayName = displayName;
		}

		public String getDisplayName() {
			return displayName;
		}
	}

	public enum Carrier {
		ANIMAL,
		ANIMAL_CYCLE,
		ARMOR_STAND
	}

	private final Camp camp;
	private final Carrier carrier;
	private final EntityType animalType;
	private final Material heldItem;
	private final String titleLine;
	private final String subTitleLine;
	private final String hintLine;
	private final long animationIntervalTicks;

	TutorialDemo(Camp camp, Carrier carrier, EntityType animalType, Material heldItem,
				 String titleLine, String subTitleLine, String hintLine, long animationIntervalTicks) {
		this.camp = camp;
		this.carrier = carrier;
		this.animalType = animalType;
		this.heldItem = heldItem;
		this.titleLine = titleLine;
		this.subTitleLine = subTitleLine;
		this.hintLine = hintLine;
		this.animationIntervalTicks = animationIntervalTicks;
	}

	public Camp getCamp() {
		return camp;
	}

	public Carrier getCarrier() {
		return carrier;
	}

	public EntityType getAnimalType() {
		return animalType;
	}

	public Material getHeldItem() {
		return heldItem;
	}

	public String getTitleLine() {
		return titleLine;
	}

	public String getSubTitleLine() {
		return subTitleLine;
	}

	public String getHintLine() {
		return hintLine;
	}

	public long getAnimationIntervalTicks() {
		return animationIntervalTicks;
	}

	/**
	 * 配置文件/命令使用的小写连字符 ID，例如 SAFE_TAUNT -> "safe-taunt"。
	 */
	public String getId() {
		return name().toLowerCase(Locale.ROOT).replace('_', '-');
	}

	/**
	 * 把命令/配置里的 ID 字符串解析回枚举常量。
	 * <p>
	 * 兼容大小写、下划线与连字符；非法输入返回 {@code null}。
	 */
	public static TutorialDemo fromId(String raw) {
		if (raw == null || raw.isBlank()) return null;
		String norm = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
		for (TutorialDemo d : values()) {
			if (d.name().equals(norm)) return d;
		}
		return null;
	}

	/**
	 * 把"旧教程系统"里残留的动物类型映射到新版演示桩，确保升级时不丢失已布置的 NPC。
	 *
	 * @param legacyType 旧 config 里保存的 "pig" / "sheep" / "cow" / "chicken"
	 * @return 对应的新版演示桩；映射不到时返回 {@code null}
	 */
	public static TutorialDemo fromLegacyType(String legacyType) {
		if (legacyType == null) return null;
		return switch (legacyType.toLowerCase(Locale.ROOT)) {
			case "pig" -> SAFE_TAUNT;
			case "sheep" -> RISKY_TAUNT;
			case "cow" -> FIREWORK_TAUNT;
			case "chicken" -> DANGEROUS_TAUNT;
			default -> null;
		};
	}
}
