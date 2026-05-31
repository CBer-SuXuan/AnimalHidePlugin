package me.suxuan.animalhide.game.runtime;

import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.PlayerRole;
import me.suxuan.animalhide.manager.DisguiseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.BiConsumer;

public class RoleConversionService {

	private final AnimalHidePlugin plugin;
	private final DisguiseManager disguiseManager;
	private final RoleSetupService roleSetupService;
	private final BiConsumer<Arena, PlayerRole> endGameCallback;

	public RoleConversionService(AnimalHidePlugin plugin, DisguiseManager disguiseManager, RoleSetupService roleSetupService, BiConsumer<Arena, PlayerRole> endGameCallback) {
		this.plugin = plugin;
		this.disguiseManager = disguiseManager;
		this.roleSetupService = roleSetupService;
		this.endGameCallback = endGameCallback;
	}

	public void processHiderFound(Arena arena, Player victim, Player seeker) {
		if (!arena.getHiders().contains(victim.getUniqueId())) return;

		notifyMatchPlayers(arena,
				Component.text("☠ ", NamedTextColor.GRAY)
						.append(Component.text(victim.getName(), NamedTextColor.RED))
						.append(Component.text(" 被 ", NamedTextColor.GRAY))
						.append(Component.text(seeker.getName(), NamedTextColor.AQUA))
						.append(Component.text(" 找到了！", NamedTextColor.GRAY)),
				Component.text(victim.getName(), NamedTextColor.RED)
						.append(Component.text(" 被 ", NamedTextColor.GRAY))
						.append(Component.text(seeker.getName(), NamedTextColor.AQUA))
						.append(Component.text(" 找到了！", NamedTextColor.YELLOW)));

		int killScore = arena.getTemplate().getScoring().getSeekerKillHider();
		arena.addMatchScore(seeker.getUniqueId(), killScore);
		arena.addMatchKill(seeker.getUniqueId());
		seeker.sendMessage(Component.text("击杀躲藏者！积分 +" + killScore, NamedTextColor.GREEN));
		roleSetupService.applySeekerLevelUp(seeker, arena);

		convertHiderToSeeker(arena, victim, Component.text("你已经被发现！现在你加入了寻找者阵营！", NamedTextColor.YELLOW));
	}

	/**
	 * 躲藏者因非寻找者击杀（环境伤害、技能、指令等）失去躲藏资格，直接转为寻找者。
	 */
	public void processHiderEliminated(Arena arena, Player victim) {
		if (!arena.getHiders().contains(victim.getUniqueId())) return;

		notifyMatchPlayers(arena,
				Component.text("☠ ", NamedTextColor.GRAY)
						.append(Component.text(victim.getName(), NamedTextColor.RED))
						.append(Component.text(" 因受伤死亡，加入了寻找者阵营！", NamedTextColor.GRAY)),
				Component.text(victim.getName(), NamedTextColor.RED)
						.append(Component.text(" 因受伤死亡加入寻找者阵营！", NamedTextColor.YELLOW)));

		convertHiderToSeeker(arena, victim, Component.text("你因受伤死亡，加入了寻找者阵营！", NamedTextColor.YELLOW));
	}

	private void notifyMatchPlayers(Arena arena, Component chatMessage, Component actionBarMessage) {
		for (UUID uuid : arena.getPlayers()) {
			Player player = Bukkit.getPlayer(uuid);
			if (player == null || !player.isOnline()) continue;
			player.sendMessage(chatMessage);
			player.sendActionBar(actionBarMessage);
			player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.75f);
		}
	}

	private void convertHiderToSeeker(Arena arena, Player victim, Component victimMessage) {
		arena.getHiders().remove(victim.getUniqueId());
		plugin.getDecoyManager().clear(victim, arena);
		arena.getSeekers().add(victim.getUniqueId());

		victim.setHealth(20.0);
		victim.setFoodLevel(20);
		victim.setSaturation(20f);
		disguiseManager.undisguisePlayer(victim);
		if (arena.getSeekerSpawn() != null) {
			victim.teleportAsync(arena.getSeekerSpawn());
		}
		victim.sendMessage(victimMessage);
		roleSetupService.equipSeeker(victim, 0);

		if (arena.getHiders().isEmpty()) {
			endGameCallback.accept(arena, PlayerRole.SEEKER);
		}
	}
}
