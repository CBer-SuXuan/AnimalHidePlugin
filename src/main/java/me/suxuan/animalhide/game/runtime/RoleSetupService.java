package me.suxuan.animalhide.game.runtime;

import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.manager.DisguiseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.List;
import java.util.Random;

public class RoleSetupService {

	public static final int MAX_SEEKER_LEVEL = 5;

	private final DisguiseManager disguiseManager;
	private final Random random = new Random();

	public RoleSetupService(DisguiseManager disguiseManager) {
		this.disguiseManager = disguiseManager;
	}

	public void setupSeeker(Player seeker, Arena arena, int hideTimeTicks) {
		seeker.teleportAsync(arena.getSeekerSpawn());
		seeker.setGameMode(org.bukkit.GameMode.ADVENTURE);
		seeker.setFoodLevel(20);
		seeker.setSaturation(20f);
		seeker.sendMessage(Component.text("你是寻找者！找出所有的动物！", NamedTextColor.RED));

		equipSeeker(seeker, 0);

		seeker.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0);
		seeker.getAttribute(Attribute.SNEAKING_SPEED).setBaseValue(0);
		seeker.getAttribute(Attribute.JUMP_STRENGTH).setBaseValue(0);
		seeker.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, hideTimeTicks + 100, 1, false, false, false));
	}

	public void setupHider(Player hider, Arena arena, List<String> allowedAnimals) {
		hider.teleportAsync(arena.getHiderSpawn());
		hider.setGameMode(org.bukkit.GameMode.ADVENTURE);
		hider.setFoodLevel(20);
		hider.setSaturation(0f);

		List<Entity> aiList = arena.getAiAnimals();
		if (!aiList.isEmpty()) {
			Entity randomAi = aiList.get(random.nextInt(aiList.size()));
			disguiseManager.disguisePlayerAsEntity(hider, randomAi);
		}

		hider.sendMessage(Component.text("你是躲藏者！", NamedTextColor.GREEN));
		equipHider(hider);
	}

	public static int seekerLevelOf(int kills) {
		return Math.min(MAX_SEEKER_LEVEL, kills + 1);
	}

	public static int killsForNextLevel(int kills) {
		int level = seekerLevelOf(kills);
		if (level >= MAX_SEEKER_LEVEL) return -1;
		return level;
	}

	public void equipSeeker(Player seeker, int kills) {
		int level = seekerLevelOf(kills);

		seeker.getInventory().clear();

		ItemStack sword = new ItemStack(Material.STONE_SWORD);
		ItemMeta swordMeta = sword.getItemMeta();
		swordMeta.setUnbreakable(true);
		int knockback = (level >= 5) ? 2 : 1;
		swordMeta.addEnchant(org.bukkit.enchantments.Enchantment.KNOCKBACK, knockback, true);
		if (level >= 2) {
			int sharpness = Math.min(3, level - 1);
			swordMeta.addEnchant(org.bukkit.enchantments.Enchantment.SHARPNESS, sharpness, true);
		}
		swordMeta.displayName(Component.text("★ 寻找者佩剑 [Lv." + level + "] ★", NamedTextColor.RED)
				.decoration(TextDecoration.ITALIC, false));
		sword.setItemMeta(swordMeta);
		seeker.getInventory().setItem(0, sword);

		ItemStack bow = new ItemStack(Material.BOW);
		ItemMeta bowMeta = bow.getItemMeta();
		bowMeta.setUnbreakable(true);
		bowMeta.addEnchant(org.bukkit.enchantments.Enchantment.INFINITY, 1, true);
		if (level >= 3) {
			int power = (level >= 5) ? 3 : (level - 2);
			bowMeta.addEnchant(org.bukkit.enchantments.Enchantment.POWER, power, true);
		}
		if (level >= 5) {
			bowMeta.addEnchant(org.bukkit.enchantments.Enchantment.PUNCH, 1, true);
		}
		bowMeta.displayName(Component.text("★ 寻找者之弓 [Lv." + level + "] ★", NamedTextColor.RED)
				.decoration(TextDecoration.ITALIC, false));
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

		if (level >= 3) {
			seeker.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false, false));
		}
		if (level >= 5) {
			seeker.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 0, false, false, false));
		}
	}

	public void applySeekerLevelUp(Player seeker, Arena arena) {
		int kills = arena.getMatchKills(seeker.getUniqueId());
		int newLevel = seekerLevelOf(kills);
		int oldLevel = seekerLevelOf(kills - 1);

		seeker.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1, false, false, false));
		equipSeeker(seeker, kills);

		if (newLevel > oldLevel) {
			seeker.playSound(seeker.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
			seeker.showTitle(Title.title(
					Component.text("Lv." + oldLevel + " → Lv." + newLevel, NamedTextColor.GOLD),
					newLevel >= MAX_SEEKER_LEVEL
							? Component.text("⚔ 寻找者已满级！", NamedTextColor.RED)
							: Component.text("⚔ 寻找者升级！装备已强化", NamedTextColor.YELLOW),
					Title.Times.times(Duration.ofMillis(150), Duration.ofSeconds(2), Duration.ofMillis(300))
			));
		}
	}

	private void equipHider(Player hider) {
		ItemStack wand = new ItemStack(Material.BLAZE_ROD);
		ItemMeta wandMeta = wand.getItemMeta();
		wandMeta.displayName(Component.text("★ 变身魔杖 (右键生物) ★", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
		wand.setItemMeta(wandMeta);
		hider.getInventory().setItem(0, wand);

		ItemStack decoyItem = new ItemStack(Material.LEAD);
		ItemMeta decoyMeta = decoyItem.getItemMeta();
		decoyMeta.displayName(Component.text("▶ 定点伪装 (右键切换) ◀", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
		decoyMeta.lore(List.of(
				Component.text("锁定当前位置与朝向，可转动视角", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
				Component.text("仅可在站立于地面时使用", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
				Component.text("期间无法移动或射箭，可使用嘲讽", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
		));
		decoyItem.setItemMeta(decoyMeta);
		hider.getInventory().setItem(2, decoyItem);

		ItemStack bow = new ItemStack(Material.BOW);
		ItemMeta bowMeta = bow.getItemMeta();
		bowMeta.displayName(Component.text("★ 击退弓 (射击寻找者升级) ★", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
		bowMeta.setUnbreakable(true);
		bow.setItemMeta(bowMeta);
		hider.getInventory().setItem(1, bow);

		ItemStack safeTaunt = new ItemStack(Material.PINK_DYE);
		ItemMeta safeMeta = safeTaunt.getItemMeta();
		safeMeta.displayName(Component.text("▶ 便便嘲讽 (CD: 5秒) ◀", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
		safeMeta.lore(List.of(
				Component.text("拉下一坨便便并发出叫声", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
				Component.text("寻找者会获得指向最近便便的指南针", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
		));
		safeTaunt.setItemMeta(safeMeta);
		hider.getInventory().setItem(3, safeTaunt);

		ItemStack modTaunt = new ItemStack(Material.GLOWSTONE_DUST);
		ItemMeta modMeta = modTaunt.getItemMeta();
		modMeta.displayName(Component.text("▶ 臭气嘲讽 (CD: 15秒) ◀", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
		modMeta.lore(List.of(
				Component.text("留下便便并持续散发臭气", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
				Component.text("靠近后更容易被寻找者肉眼发现", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
		));
		modTaunt.setItemMeta(modMeta);
		hider.getInventory().setItem(4, modTaunt);

		ItemStack fwTaunt = new ItemStack(Material.FIREWORK_ROCKET);
		ItemMeta fwMeta = fwTaunt.getItemMeta();
		fwMeta.displayName(Component.text("▶ 尖叫嘲讽 (CD: 20秒) ◀", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
		fwMeta.lore(List.of(
				Component.text("发出大动静并升起明显标记柱", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
				Component.text("远处寻找者也能循声赶来", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
		));
		fwTaunt.setItemMeta(fwMeta);
		hider.getInventory().setItem(5, fwTaunt);

		ItemStack dangTaunt = new ItemStack(Material.REDSTONE_TORCH);
		ItemMeta dangMeta = dangTaunt.getItemMeta();
		dangMeta.displayName(Component.text("▶ 派对嘲讽 (CD: 45秒) ◀", NamedTextColor.DARK_RED).decoration(TextDecoration.ITALIC, false));
		dangMeta.lore(List.of(
				Component.text("触发全场最显眼的嘲讽演出", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
				Component.text("全体寻找者的指南针会强锁定这次目标", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
		));
		dangTaunt.setItemMeta(dangMeta);
		hider.getInventory().setItem(6, dangTaunt);

		if (me.suxuan.animalhide.AnimalHidePlugin.getInstance().getConfigManager().isHiderDisguiseInvisibilityEnabled()) {
			ItemStack invisSkill = new ItemStack(Material.AMETHYST_SHARD);
			ItemMeta invisMeta = invisSkill.getItemMeta();
			invisMeta.displayName(Component.text("▶ 一次性伪装隐身 (5秒) ◀", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
			invisMeta.lore(List.of(
					Component.text("右键后让你的伪装短暂隐身", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
					Component.text("一次性技能，用完即消失", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
			));
			invisSkill.setItemMeta(invisMeta);
			hider.getInventory().setItem(7, invisSkill);
		}

		hider.getInventory().setItem(8, new ItemStack(Material.ARROW, 5));
	}
}
