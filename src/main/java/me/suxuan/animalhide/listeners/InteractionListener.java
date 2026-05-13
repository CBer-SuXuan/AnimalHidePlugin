package me.suxuan.animalhide.listeners;

import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.Disguise;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.game.*;
import me.suxuan.animalhide.menus.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

			if (arena.getSpectators().contains(player.getUniqueId())) {
				event.setCancelled(true);
				ItemStack item = event.getItem();
				if (item != null && item.getType() == Material.RED_BED) {
					player.performCommand("hide leave");
				}
			} else if (arena.getHiders().contains(player.getUniqueId())) {

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
			} else if (arena.getSeekers().contains(player.getUniqueId())) {
				if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
					ItemStack item = event.getItem();
					if (item == null) return;

					// 判定右键红石 (爆炸陷阱)
					if (item.getType() == Material.REDSTONE) {
						event.setCancelled(true); // 绝对取消，防止把红石铺在地上

						if (player.hasPotionEffect(PotionEffectType.BLINDNESS)) {
							player.sendActionBar(Component.text("还没到寻找者出动的时间，无法使用陷阱！", NamedTextColor.RED));
							return;
						}

						if (player.hasCooldown(Material.REDSTONE)) {
							player.sendActionBar(Component.text("陷阱还在冷却中！", NamedTextColor.RED));
							return;
						}

						player.setCooldown(Material.REDSTONE, 20 * 20);

						Location trapLoc = player.getLocation();
						startExplosiveTrap(player, arena, trapLoc);
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
					if (DisguiseAPI.getDisguise(player) == disguise) {
						disguise.getWatcher().setGlowing(false);
					}
				}
			}.runTaskLater(AnimalHidePlugin.getInstance(), ticks);
		}
	}

	/**
	 * 执行爆炸陷阱逻辑 (超强特效版：密集红圈 + 悬浮大红感叹号)
	 */
	private void startExplosiveTrap(Player seeker, Arena arena, Location loc) {
		double radius = 5.0;
		double radiusSquared = radius * radius;

		new BukkitRunnable() {
			int ticks = 60;

			@Override
			public void run() {
				// 如果对局已经结束，停止陷阱
				if (arena.getState() != GameState.PLAYING) {
					cancel();
					return;
				}

				if (ticks > 0) {
					// 1. 每 20 Tick (1秒) 播放一次提示音
					if (ticks % 20 == 0) {
						int seconds = ticks / 20;
						loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
						arena.broadcast(Component.text("⚠ 寻找者 " + seeker.getName() + " 释放了陷阱！将在 " + seconds + " 秒后爆炸！", NamedTextColor.RED));
					}

					// 2. 每 4 Tick (0.2秒) 绘制一次高密度特效
					if (ticks % 4 == 0) {
						// 设置外圈的粒子大小为 1.5，感叹号的粒子大小为 2.5 (极其显眼)
						Particle.DustOptions circleDust = new Particle.DustOptions(Color.RED, 5.5f);
						Particle.DustOptions markDust = new Particle.DustOptions(Color.RED, 2.5f);

						// 【新增】绘制更密集的底圈 (由原本的 16 个点增加到 32 个点)
						for (double t = 0; t < Math.PI * 2; t += Math.PI / 16) {
							double x = radius * Math.cos(t);
							double z = radius * Math.sin(t);
							loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(x, 0.2, z), 2, 0.05, 0, 0.05, 0, circleDust);
						}

						// 画感叹号下方的“点” (高度 0.6 到 0.8)
						for (double y = 1.0; y <= 1.2; y += 0.1) {
							loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0, y, 0), 3, 0.05, 0.05, 0.05, 0, markDust);
						}

						// 画感叹号上方的“竖杠” (高度 1.3 到 2.8，比玩家头顶还高一点)
						for (double y = 2.2; y <= 4; y += 0.1) {
							loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0, y, 0), 3, 0.05, 0.05, 0.05, 0, markDust);
						}
					}

					// 3. 实时范围检测与躲藏者警示
					List<UUID> hiderIds = new ArrayList<>(arena.getHiders());
					for (UUID id : hiderIds) {
						Player hider = Bukkit.getPlayer(id);
						if (hider != null && hider.getLocation().distanceSquared(loc) <= radiusSquared) {
							hider.sendActionBar(Component.text("⚠ 警告：你在爆炸陷阱范围内！快跑！", NamedTextColor.DARK_RED));
						}
					}

					ticks--;
				} else {
					// === 爆炸时刻 ===
					loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
					// 爆炸核心粒子加大
					loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 2);

					// 【新增】爆炸的瞬间，在圈的边缘释放出一层猛烈的真实火焰和岩浆飞溅特效
					for (double t = 0; t < Math.PI * 2; t += Math.PI / 16) {
						Location edge = loc.clone().add(radius * Math.cos(t), 0.2, radius * Math.sin(t));
						loc.getWorld().spawnParticle(Particle.FLAME, edge, 5, 0.2, 0.5, 0.2, 0.1);
						loc.getWorld().spawnParticle(Particle.LAVA, edge, 1);
					}

					// 击杀判定
					List<UUID> hiderIds = new ArrayList<>(arena.getHiders());
					for (UUID id : hiderIds) {
						Player hider = Bukkit.getPlayer(id);
						if (hider != null && hider.getLocation().distanceSquared(loc) <= radiusSquared) {
							gameManager.processHiderFound(arena, hider, seeker);
						}
					}

					cancel();
				}
			}
		}.runTaskTimer(AnimalHidePlugin.getInstance(), 0L, 1L);
	}

}
