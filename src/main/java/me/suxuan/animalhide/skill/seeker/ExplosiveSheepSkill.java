package me.suxuan.animalhide.skill.seeker;

import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.game.GameState;
import me.suxuan.animalhide.skill.ItemBasedSkill;
import me.suxuan.animalhide.skill.SkillContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

public class ExplosiveSheepSkill extends ItemBasedSkill {

	private final AnimalHidePlugin plugin;

	public ExplosiveSheepSkill(AnimalHidePlugin plugin) {
		super("explosive_sheep", Material.SHEEP_SPAWN_EGG);
		this.plugin = plugin;
	}

	@Override
	public boolean additionalMatches(SkillContext context, Entity target) {
		return context.arena() != null
				&& context.arena().getState() == GameState.PLAYING
				&& context.arena().getSeekers().contains(context.player().getUniqueId());
	}

	@Override
	public void execute(SkillContext context, Entity target) {
		Player player = context.player();
		if (context.arena().isHidePhase()) {
			player.sendActionBar(Component.text("还没到寻找者出动的时间，无法使用！", NamedTextColor.RED));
			return;
		}

		if (player.hasCooldown(Material.SHEEP_SPAWN_EGG)) {
			player.sendActionBar(Component.text("还在冷却中！", NamedTextColor.RED));
			return;
		}

		spawnSheep(player, context);
	}

	private void spawnSheep(Player seeker, SkillContext context) {
		Location spawnLoc = resolveGroundSpawn(seeker);

		Sheep bombSheep = (Sheep) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.SHEEP);
		bombSheep.setAI(false);
		bombSheep.setCustomName("§c§l即将爆炸...");
		bombSheep.setCustomNameVisible(true);
		bombSheep.setInvulnerable(true);
		bombSheep.setGravity(false);
		bombSheep.setRotation(spawnLoc.getYaw(), 0f);
		bombSheep.setVelocity(new Vector(0, 0, 0));

		seeker.setCooldown(Material.SHEEP_SPAWN_EGG, 20 * 20);

		new BukkitRunnable() {
			int remainingTicks = 60;
			boolean isRed = false;

			@Override
			public void run() {
				if (context.arena().getState() != GameState.PLAYING || !bombSheep.isValid()) {
					bombSheep.remove();
					cancel();
					return;
				}

				if (remainingTicks > 0) {
					isRed = !isRed;
					bombSheep.setColor(isRed ? DyeColor.RED : DyeColor.WHITE);
					float pitch = 1.0f + ((60 - remainingTicks) * 0.015f);
					bombSheep.getWorld().playSound(bombSheep.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, pitch);
					remainingTicks -= 5;
				} else {
					executeExplosion(bombSheep, seeker, context);
					bombSheep.remove();
					cancel();
				}
			}
		}.runTaskTimer(plugin, 0L, 5L);
	}

	private Location resolveGroundSpawn(Player seeker) {
		Location origin = seeker.getLocation();
		World world = origin.getWorld();
		int blockX = origin.getBlockX();
		int blockZ = origin.getBlockZ();

		int startY = origin.getBlockY() + 1;
		int minY = Math.max(world.getMinHeight(), origin.getBlockY() - 8);
		for (int y = startY; y >= minY; y--) {
			Block ground = world.getBlockAt(blockX, y - 1, blockZ);
			Block feet = world.getBlockAt(blockX, y, blockZ);
			Block head = world.getBlockAt(blockX, y + 1, blockZ);
			if (ground.getType().isSolid() && feet.isPassable() && head.isPassable()) {
				Location result = new Location(world, blockX + 0.5, y, blockZ + 0.5);
				result.setYaw(origin.getYaw());
				result.setPitch(0f);
				return result;
			}
		}

		Location fallback = origin.clone();
		fallback.setPitch(0f);
		return fallback;
	}

	private void executeExplosion(Sheep bombSheep, Player seeker, SkillContext context) {
		Location loc = bombSheep.getLocation();
		loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1);
		loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);

		double radius = 5.0;
		List<Entity> nearby = (List<Entity>) loc.getWorld().getNearbyEntities(loc, radius, radius, radius);

		for (Entity entity : nearby) {
			if (context.arena().getAiAnimals().contains(entity)) {
				entity.remove();
				context.arena().getAiAnimals().remove(entity);
			}

			if (entity instanceof Player victim && context.arena().getHiders().contains(victim.getUniqueId())) {
				victim.damage(10.0, seeker);
				victim.sendActionBar(Component.text("⚠ 你受到了爆炸绵羊的冲击！", NamedTextColor.RED));
				seeker.sendActionBar(Component.text("✔ 爆炸命中了躲藏者！", NamedTextColor.GREEN));
			}
		}
	}
}
