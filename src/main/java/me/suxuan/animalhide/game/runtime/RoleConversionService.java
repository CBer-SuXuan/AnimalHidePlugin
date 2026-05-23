package me.suxuan.animalhide.game.runtime;

import me.suxuan.animalhide.AnimalHidePlugin;
import me.suxuan.animalhide.game.Arena;
import me.suxuan.animalhide.game.PlayerRole;
import me.suxuan.animalhide.manager.DisguiseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

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

		arena.broadcast(Component.text("☠ ", NamedTextColor.GRAY)
				.append(Component.text(victim.getName(), NamedTextColor.RED))
				.append(Component.text(" 被 ", NamedTextColor.GRAY))
				.append(Component.text(seeker.getName(), NamedTextColor.AQUA))
				.append(Component.text(" 找到了！", NamedTextColor.GRAY)));

		arena.getHiders().remove(victim.getUniqueId());
		plugin.getDecoyManager().clear(victim, arena);
		arena.getSeekers().add(victim.getUniqueId());

		int killScore = arena.getTemplate().getScoring().getSeekerKillHider();
		arena.addMatchScore(seeker.getUniqueId(), killScore);
		arena.addMatchKill(seeker.getUniqueId());
		seeker.sendMessage(Component.text("击杀躲藏者！积分 +" + killScore, NamedTextColor.GREEN));

		roleSetupService.applySeekerLevelUp(seeker, arena);

		victim.setHealth(20.0);
		victim.setFoodLevel(20);
		victim.setSaturation(20f);
		disguiseManager.undisguisePlayer(victim);
		victim.teleportAsync(arena.getSeekerSpawn());
		victim.sendMessage(Component.text("你已经被发现！现在你加入了寻找者阵营！", NamedTextColor.YELLOW));

		roleSetupService.equipSeeker(victim, 0);

		if (arena.getHiders().isEmpty()) {
			endGameCallback.accept(arena, PlayerRole.SEEKER);
		}
	}
}
