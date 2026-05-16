package me.suxuan.animalhide.commands;

import me.suxuan.animalhide.game.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

public class ReloadSubCommand implements SubCommand {

	private final GameManager gameManager;

	public ReloadSubCommand(GameManager gameManager) {
		this.gameManager = gameManager;
	}

	@Override
	public String getName() {
		return "reload";
	}

	@Override
	public String getUsage() {
		return "/hide reload";
	}

	@Override
	public String getPermission() {
		return "animalhide.admin";
	}

	@Override
	public boolean execute(CommandSender sender, String[] args) {
		gameManager.reload();
		sender.sendMessage(Component.text("插件配置重载成功！", NamedTextColor.GREEN));
		return true;
	}
}
