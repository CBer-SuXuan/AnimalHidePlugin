package me.suxuan.animalhide.skill.hider;

import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.Disguise;
import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.manager.DecoyManager;
import me.suxuan.animalhide.skill.SkillContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TauntTraceSupport {

	private static final int SEEKER_COMPASS_SLOT = 7;
	private static final long POOP_LIFETIME_MILLIS = 45_000L;
	private static final double SEEKER_HEAR_RANGE_SQUARED = 48 * 48;

	private final AnimalHidePlugin plugin;
	private final Map<String, List<PoopMarker>> poopMarkersByArena = new ConcurrentHashMap<>();
	private BukkitTask cleanupTask;

	public TauntTraceSupport(AnimalHidePlugin plugin) {
		this.plugin = plugin;
	}

	public void start() {
		if (cleanupTask != null) {
			cleanupTask.cancel();
		}
		cleanupTask = new BukkitRunnable() {
			@Override
			public void run() {
				cleanupExpiredMarkers();
			}
		}.runTaskTimer(plugin, 20L, 20L);
	}

	public void shutdown() {
		if (cleanupTask != null) {
			cleanupTask.cancel();
			cleanupTask = null;
		}
		for (List<PoopMarker> markers : poopMarkersByArena.values()) {
			for (PoopMarker marker : markers) {
				removeMarkerEntity(marker);
			}
		}
		poopMarkersByArena.clear();
	}

	public void clearArena(Arena arena) {
		List<PoopMarker> removed = poopMarkersByArena.remove(arenaKey(arena));
		if (removed != null) {
			for (PoopMarker marker : removed) {
				removeMarkerEntity(marker);
			}
		}
		refreshSeekers(arena, null, false);
	}

	public Location resolveTauntLocation(SkillContext context) {
		DecoyManager decoyManager = context.plugin().getDecoyManager();
		return decoyManager.getAnchorOrPlayerLocation(context.player(), context.arena());
	}

	public PoopMarker createPoopMarker(SkillContext context, Location source, String displayName) {
		Location spawnLoc = source.clone();
		ItemStack poopStack = new ItemStack(Material.COCOA_BEANS);
		ItemMeta meta = poopStack.getItemMeta();
		meta.displayName(Component.text(displayName, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
		meta.lore(List.of(
				Component.text("会暴露附近线索的便便", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
				Component.text("寻找者的指南针会指向这里", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
				Component.text("寻找者拾取后可获得积分", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)
		));
		poopStack.setItemMeta(meta);

		Item itemEntity = spawnLoc.getWorld().dropItem(spawnLoc, poopStack);
		itemEntity.setCustomName("§6" + displayName);
		itemEntity.setCustomNameVisible(true);
		itemEntity.setPickupDelay(0);
		itemEntity.setUnlimitedLifetime(false);
		itemEntity.setCanMobPickup(false);
		itemEntity.setWillAge(false);

		PoopMarker marker = new PoopMarker(
				arenaKey(context.arena()),
				context.player().getUniqueId(),
				spawnLoc.clone(),
				System.currentTimeMillis() + POOP_LIFETIME_MILLIS,
				itemEntity.getUniqueId(),
				displayName
		);
		poopMarkersByArena.computeIfAbsent(arenaKey(context.arena()), key -> new ArrayList<>()).add(marker);
		return marker;
	}

	public void refreshSeekers(Arena arena, Location forcedTarget, boolean broadcastHint) {
		for (UUID seekerId : arena.getSeekers()) {
			Player seeker = Bukkit.getPlayer(seekerId);
			if (seeker == null) continue;
			Location target = forcedTarget != null ? forcedTarget : getNearestPoopLocation(arena, seeker.getLocation());
			boolean hasTarget = target != null;
			seeker.getInventory().setItem(SEEKER_COMPASS_SLOT, createCompass(target, hasTarget));
			if (broadcastHint) {
				seeker.sendActionBar(Component.text(hasTarget ? "指南针已锁定最近便便！" : "附近暂无便便目标", hasTarget ? NamedTextColor.YELLOW : NamedTextColor.GRAY));
			}
		}
	}

	public void refreshSeekersToNearestHider(Arena arena) {
		for (UUID seekerId : arena.getSeekers()) {
			Player seeker = Bukkit.getPlayer(seekerId);
			if (seeker == null || !seeker.isOnline()) continue;
			Location target = getNearestHiderLocation(arena, seeker.getLocation());
			boolean hasTarget = target != null;
			seeker.getInventory().setItem(SEEKER_COMPASS_SLOT, createFinalRevealCompass(target, hasTarget));
		}
	}

	public void playAnimalSound(SkillContext context, Location at, float volume, float pitch) {
		Disguise disguise = DisguiseAPI.getDisguise(context.player());
		if (disguise == null) return;

		String typeName = disguise.getType().name();
		try {
			Sound sound = Sound.valueOf("ENTITY_" + typeName + "_AMBIENT");
			at.getWorld().playSound(at, sound, volume, pitch);
		} catch (IllegalArgumentException e) {
			at.getWorld().playSound(at, Sound.ENTITY_GENERIC_EAT, Math.max(0.6f, volume * 0.6f), pitch);
		}
	}

	public void spawnPoopParticles(Player player) {
		Location at = player.getLocation();
		World world = at.getWorld();
		world.spawnParticle(Particle.FALLING_DUST, at.clone().add(0, 0.2, 0), 6, 0.18, 0.08, 0.18, Material.BROWN_MUSHROOM_BLOCK.createBlockData());
		world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, at.clone().add(0, 0.3, 0), 3, 0.12, 0.08, 0.12, 0.01);
	}

	public void spawnStinkCloud(Player player, long durationTicks) {
		new BukkitRunnable() {
			long lived = 0;

			@Override
			public void run() {
				if (lived >= durationTicks || !player.isOnline()) {
					cancel();
					return;
				}
				Location at = player.getLocation();
				World world = at.getWorld();
				world.spawnParticle(Particle.DUST, at.clone().add(0, 0.8, 0), 14, 0.55, 0.35, 0.55, new Particle.DustOptions(Color.fromRGB(121, 173, 84), 1.2f));
				world.spawnParticle(Particle.SNEEZE, at.clone().add(0, 0.8, 0), 6, 0.4, 0.25, 0.4, 0.01);
				lived += 10;
			}
		}.runTaskTimer(plugin, 0L, 10L);
	}

	public void spawnBeaconColumn(Player player, long durationTicks, Particle particle, int count, double spread) {
		new BukkitRunnable() {
			long lived = 0;

			@Override
			public void run() {
				if (lived >= durationTicks || !player.isOnline()) {
					cancel();
					return;
				}
				Location at = player.getLocation();
				World world = at.getWorld();
				for (double y = 0.5; y <= 3.5; y += 0.5) {
					world.spawnParticle(particle, at.clone().add(0, y, 0), count, spread, 0.02, spread, 0.01);
				}
				lived += 5;
			}
		}.runTaskTimer(plugin, 0L, 5L);
	}

	public void spawnDustBeaconColumn(Player player, long durationTicks, Particle.DustOptions dust, int count, double spread, double maxHeight) {
		new BukkitRunnable() {
			long lived = 0;

			@Override
			public void run() {
				if (lived >= durationTicks || !player.isOnline()) {
					cancel();
					return;
				}
				Location at = player.getLocation();
				World world = at.getWorld();
				for (double y = 0.5; y <= maxHeight; y += 0.75) {
					world.spawnParticle(Particle.DUST, at.clone().add(0, y, 0), count, spread, 0.05, spread, 0, dust);
				}
				lived += 5;
			}
		}.runTaskTimer(plugin, 0L, 5L);
	}

	public void spawnPartyBurst(Player player, long durationTicks) {
		new BukkitRunnable() {
			long lived = 0;

			@Override
			public void run() {
				if (lived >= durationTicks || !player.isOnline()) {
					cancel();
					return;
				}
				Location at = player.getLocation();
				World world = at.getWorld();
				world.spawnParticle(Particle.DUST, at.clone().add(0, 1.0, 0), 18, 0.8, 0.4, 0.8, new Particle.DustOptions(Color.FUCHSIA, 1.4f));
				world.spawnParticle(Particle.DUST, at.clone().add(0, 1.5, 0), 18, 0.8, 0.5, 0.8, new Particle.DustOptions(Color.YELLOW, 1.4f));
				world.spawnParticle(Particle.HAPPY_VILLAGER, at.clone().add(0, 1.2, 0), 8, 0.75, 0.45, 0.75, 0.01);
				world.spawnParticle(Particle.NOTE, at.clone().add(0, 2.0, 0), 6, 0.7, 0.4, 0.7, 1);
				lived += 8;
			}
		}.runTaskTimer(plugin, 0L, 8L);
	}

	public void pulseSeekerAudio(Arena arena, Location source, Sound sound, float volume, float pitch) {
		for (UUID seekerId : arena.getSeekers()) {
			Player seeker = Bukkit.getPlayer(seekerId);
			if (seeker == null || seeker.getWorld() != source.getWorld()) continue;
			if (seeker.getLocation().distanceSquared(source) <= SEEKER_HEAR_RANGE_SQUARED) {
				seeker.playSound(source, sound, volume, pitch);
			}
		}
	}

	private void cleanupExpiredMarkers() {
		long now = System.currentTimeMillis();
		Iterator<Map.Entry<String, List<PoopMarker>>> arenaIt = poopMarkersByArena.entrySet().iterator();
		while (arenaIt.hasNext()) {
			Map.Entry<String, List<PoopMarker>> entry = arenaIt.next();
			List<PoopMarker> markers = entry.getValue();
			markers.removeIf(marker -> {
				boolean expired = marker.expireAt() <= now || !isEntityAlive(marker);
				if (expired) {
					removeMarkerEntity(marker);
				}
				return expired;
			});
			if (markers.isEmpty()) {
				arenaIt.remove();
			}
		}
		for (Arena arena : plugin.getGameManager().getActiveMatches()) {
			refreshSeekers(arena, null, false);
		}
	}

	private boolean isEntityAlive(PoopMarker marker) {
		World world = Bukkit.getWorlds().stream().filter(w -> w.getName().equals(marker.location().getWorld().getName())).findFirst().orElse(null);
		if (world == null) return false;
		return world.getEntity(marker.entityId()) != null;
	}

	public PoopMarker takeMarkerByItem(UUID itemEntityId) {
		for (List<PoopMarker> markers : poopMarkersByArena.values()) {
			Iterator<PoopMarker> it = markers.iterator();
			while (it.hasNext()) {
				PoopMarker marker = it.next();
				if (marker.entityId().equals(itemEntityId)) {
					it.remove();
					return marker;
				}
			}
		}
		return null;
	}

	private void removeMarkerEntity(PoopMarker marker) {
		World world = marker.location().getWorld();
		if (world == null) return;
		if (world.getEntity(marker.entityId()) instanceof Item item) {
			item.remove();
		}
	}

	private Location getNearestPoopLocation(Arena arena, Location origin) {
		List<PoopMarker> markers = poopMarkersByArena.getOrDefault(arenaKey(arena), List.of());
		long now = System.currentTimeMillis();
		return markers.stream()
				.filter(marker -> marker.expireAt() > now)
				.filter(marker -> marker.location().getWorld() != null && marker.location().getWorld().equals(origin.getWorld()))
				.min(Comparator.comparingDouble(marker -> marker.location().distanceSquared(origin)))
				.map(marker -> marker.location().clone())
				.orElse(null);
	}

	private Location getNearestHiderLocation(Arena arena, Location origin) {
		return arena.getHiders().stream()
				.map(Bukkit::getPlayer)
				.filter(player -> player != null && player.isOnline())
				.map(Player::getLocation)
				.filter(location -> location.getWorld() != null && location.getWorld().equals(origin.getWorld()))
				.min(Comparator.comparingDouble(location -> location.distanceSquared(origin)))
				.map(Location::clone)
				.orElse(null);
	}

	private ItemStack createCompass(Location target, boolean hasTarget) {

		ItemStack compass = new ItemStack(Material.COMPASS);
		CompassMeta meta = (CompassMeta) compass.getItemMeta();
		meta.displayName(Component.text(hasTarget ? "▶ 寻便指南针 ◀" : "▶ 寻便指南针（暂无目标）◀", hasTarget ? NamedTextColor.GOLD : NamedTextColor.GRAY)
				.decoration(TextDecoration.ITALIC, false));
		meta.lore(hasTarget
				? List.of(
						Component.text("会自动指向最近的便便线索", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
						Component.text("顺着方向走，就能逼近躲藏者", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
				)
				: List.of(
						Component.text("当前场上还没有便便线索", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
				));
		meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
		if (hasTarget && target != null) {
			meta.setLodestone(target);
			meta.setLodestoneTracked(false);
		}
		compass.setItemMeta(meta);
		return compass;
	}

	private ItemStack createFinalRevealCompass(Location target, boolean hasTarget) {
		ItemStack compass = new ItemStack(Material.COMPASS);
		CompassMeta meta = (CompassMeta) compass.getItemMeta();
		meta.displayName(Component.text(hasTarget ? "▶ 最终追踪指南针 ◀" : "▶ 最终追踪指南针（暂无目标）◀", hasTarget ? NamedTextColor.RED : NamedTextColor.GRAY)
				.decoration(TextDecoration.ITALIC, false));
		meta.lore(hasTarget
				? List.of(
						Component.text("最后 30 秒：指向最近躲藏者当前位置", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
						Component.text("目标会随躲藏者移动持续刷新", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
				)
				: List.of(
						Component.text("当前没有可追踪的躲藏者", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
				));
		meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
		if (hasTarget && target != null) {
			meta.setLodestone(target);
			meta.setLodestoneTracked(false);
		}
		compass.setItemMeta(meta);
		return compass;
	}

	private String arenaKey(Arena arena) {
		return arena.getInstanceName();
	}

	public record PoopMarker(String arenaKey, UUID ownerId, Location location, long expireAt, UUID entityId, String displayName) {
	}
}
