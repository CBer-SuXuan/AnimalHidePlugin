package me.suxuan.animalhide.listeners;

import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.Disguise;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.game.*;
import me.suxuan.animalhide.menus.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class InteractionListener implements Listener {

	private final GameManager gameManager;

	public InteractionListener(GameManager gameManager) {
		this.gameManager = gameManager;
	}

	/**
	 * 防止玩家在游戏中移动物品栏内的 UI 物品
	 */
	@EventHandler
	public void onInventoryClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) return;
		Arena arena = gameManager.getArenaByPlayer(player);

		if (arena != null) {
			event.setCancelled(true);
		}

		// 处理选择模式GUI
		if (event.getInventory().getHolder() instanceof ModeMenuHolder) {
			event.setCancelled(true);
			ItemStack clicked = event.getCurrentItem();
			if (clicked == null || arena == null) return;

			ArenaMode votedMode = (clicked.getType() == Material.PIG_SPAWN_EGG) ? ArenaMode.ANIMAL : ArenaMode.MONSTER;

			arena.getModeVotes().put(player.getUniqueId(), votedMode);
			player.sendMessage(Component.text("✔ 投票成功！当前选择: " + votedMode.getDisplayName(), NamedTextColor.GREEN));

			player.closeInventory();
			return;
		}

		// 处理选择期望身份GUI
		if (event.getInventory().getHolder() instanceof RoleMenuHolder) {
			event.setCancelled(true);
			ItemStack clicked = event.getCurrentItem();
			if (clicked == null || arena == null) return;

			PlayerRole preference = null;
			if (clicked.getType() == Material.DIAMOND_SWORD) preference = PlayerRole.SEEKER;
			else if (clicked.getType() == Material.OAK_LEAVES) preference = PlayerRole.HIDER;

			if (preference != null) {
				arena.getRolePreferences().put(player.getUniqueId(), preference);
				player.sendMessage(Component.text("✔ 已记录你的意向身份: ", NamedTextColor.GREEN).append(Component.text(preference.getDisplayName(), NamedTextColor.YELLOW)));
			} else {
				arena.getRolePreferences().remove(player.getUniqueId());
				player.sendMessage(Component.text("✔ 已重置为随机分配。", NamedTextColor.GREEN));
			}
			player.closeInventory();
		}

		// 处理选择伪装生物GUI
		if (arena != null && arena.getState() == GameState.PLAYING) {
			if (event.getInventory().getHolder() instanceof DisguiseMenuHolder) {
				event.setCancelled(true);

				ItemStack clickedItem = event.getCurrentItem();
				if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

				// 根据点击物品的材质反推 DisguiseType
				// (例如 PIG_SPAWN_EGG -> 截取 PIG)
				String materialName = clickedItem.getType().name();
				if (materialName.endsWith("_SPAWN_EGG")) {
					String animalName = materialName.replace("_SPAWN_EGG", "");
					try {
						DisguiseType newType = DisguiseType.valueOf(animalName);
						AnimalHidePlugin.getInstance().getDisguiseManager().disguisePlayer(player, newType);
						player.closeInventory();

						player.sendMessage(Component.text("✔ 伪装切换成功！", NamedTextColor.GREEN));
					} catch (IllegalArgumentException e) {
						player.sendMessage(Component.text("✘ 无法切换到该伪装。", NamedTextColor.RED));
					}
				}
			}
		}
	}

	/**
	 * 监听玩家右键
	 */
	@EventHandler
	public void onPlayerInteractLobby(PlayerInteractEvent event) {
		if (event.getHand() != EquipmentSlot.HAND) return;

		Player player = event.getPlayer();
		Arena arena = gameManager.getArenaByPlayer(player);

		if (arena != null) {
			// 处理大厅等待阶段的物品右键
			if (arena.getState() == GameState.WAITING || arena.getState() == GameState.STARTING) {
				ItemStack item = event.getItem();
				if (item == null) return;

				event.setCancelled(true);

				if (item.getType() == Material.RED_BED) {
					player.performCommand("hide leave");
				} else if (item.getType() == Material.RECOVERY_COMPASS) {
					ModeMenu.openMenu(player);
				} else if (item.getType() == Material.DIAMOND_HELMET) {
					RoleMenu.openMenu(player);
				}
				return;
			}
			if (arena.getState() != GameState.PLAYING) return;
			if (arena.getHiders().contains(player.getUniqueId())) {

				if (player.getInventory().getHeldItemSlot() == 8) {
					event.setCancelled(true);
					return;
				}

				if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
					ItemStack item = event.getItem();
					if (item != null && item.getType() == Material.NETHER_STAR) {
						event.setCancelled(true);
						DisguiseMenu.openMenu(player, arena, AnimalHidePlugin.getInstance().getConfigManager());
					}
				}
			}
		}
	}

	/**
	 * 监听躲藏者使用变身魔杖 (右键实体)
	 */
	@EventHandler
	public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
		if (event.getHand() != EquipmentSlot.HAND) return;
		Player player = event.getPlayer();
		Arena arena = gameManager.getArenaByPlayer(player);

		if (arena != null && arena.getState() == GameState.PLAYING && arena.getHiders().contains(player.getUniqueId())) {
			ItemStack item = player.getInventory().getItemInMainHand();

			if (item.getType() == Material.BLAZE_ROD) {
				event.setCancelled(true);
				Entity clicked = event.getRightClicked();
				String typeName = clicked.getType().name();

				String listKey = (arena.getArenaMode() == ArenaMode.ANIMAL) ? "allowed-animals" : "allowed-monsters";
				List<String> allowed = AnimalHidePlugin.getInstance().getConfigManager().getArenaConfigs().get(arena.getArenaName()).getStringList(listKey);

				if (allowed.contains(typeName)) {
					try {
						DisguiseType newType = DisguiseType.valueOf(typeName);
						AnimalHidePlugin.getInstance().getDisguiseManager().disguisePlayer(player, newType);

						Component localizedName = Component.translatable(clicked.getType().translationKey(), NamedTextColor.YELLOW);
						player.sendMessage(Component.text("✔ 已利用魔杖变身为: ", NamedTextColor.GREEN).append(localizedName));
					} catch (Exception ignored) {
					}
				} else {
					player.sendMessage(Component.text("✘ 这种生物不能用来伪装！", NamedTextColor.RED));
				}
			}
		}
	}

	/**
	 * 监听玩家右键 (嘲讽道具使用)
	 */
	@EventHandler
	public void onPlayerInteractGame(PlayerInteractEvent event) {
		if (event.getHand() != EquipmentSlot.HAND) return;

		Player player = event.getPlayer();
		Arena arena = gameManager.getArenaByPlayer(player);

		if (arena != null) {
			if (arena.getState() != GameState.PLAYING) return;
			if (arena.getHiders().contains(player.getUniqueId())) {

				if (player.getInventory().getHeldItemSlot() == 7) {
					event.setCancelled(true);
					return;
				}

				if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
					ItemStack item = event.getItem();
					if (item == null) return;

					Material type = item.getType();

					if (type == Material.PINK_DYE || type == Material.GLOWSTONE_DUST ||
							type == Material.FIREWORK_ROCKET || type == Material.REDSTONE_TORCH || type == Material.BLAZE_ROD) {

						event.setCancelled(true);

						// 如果正在冷却中，直接忽略
						if (player.hasCooldown(type)) return;

						// 执行对应嘲讽效果
						if (type == Material.PINK_DYE) {
							player.setCooldown(type, 20 * 10);
							player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0);
							player.sendMessage(Component.text("发动了 安全嘲讽！", NamedTextColor.GREEN));

						} else if (type == Material.GLOWSTONE_DUST) {
							player.setCooldown(type, 20 * 15);
							player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, false, false, false));
							applyDisguiseGlowing(player, 60);
							player.sendMessage(Component.text("发动了 发光嘲讽！自身发光透视3秒！", NamedTextColor.YELLOW));

						} else if (type == Material.FIREWORK_ROCKET) {
							player.setCooldown(type, 20 * 20);
							// 生成一个完全静音的实体烟花
							Firework fw = (Firework) player.getWorld().spawnEntity(player.getLocation(), EntityType.FIREWORK_ROCKET);
							FireworkMeta fwm = fw.getFireworkMeta();
							fwm.addEffect(FireworkEffect.builder().withColor(Color.RED).with(FireworkEffect.Type.BALL_LARGE).build());
							fwm.setPower(1);
							fw.setFireworkMeta(fwm);
							fw.setSilent(true);
							player.sendMessage(Component.text("发动了 烟花嘲讽！烟花已升空！", NamedTextColor.GOLD));

						} else if (type == Material.REDSTONE_TORCH) {
							player.setCooldown(type, 20 * 30);
							player.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, player.getLocation().add(0, 1.5, 0), 100, 1, 2, 1, 0.05);
							player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2, false, false, false));
							player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, false, false, false));
							applyDisguiseGlowing(player, 60);
							player.sendMessage(Component.text("发动了 危险嘲讽！释放了暴露位置的浓烟并减速自身！", NamedTextColor.RED));
						}
					}
				}
			}
		}
	}

	/**
	 * 让玩家的伪装生物发光指定时间
	 */
	private void applyDisguiseGlowing(Player player, int ticks) {
		Disguise disguise = DisguiseAPI.getDisguise(player);
		if (disguise != null) {
			disguise.getWatcher().setGlowing(true);

			// 定时关闭发光
			new BukkitRunnable() {
				@Override
				public void run() {
					if (me.libraryaddict.disguise.DisguiseAPI.getDisguise(player) == disguise) {
						disguise.getWatcher().setGlowing(false);
					}
				}
			}.runTaskLater(AnimalHidePlugin.getInstance(), ticks);
		}
	}

}
