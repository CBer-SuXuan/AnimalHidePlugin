package me.suxuan.animalhide;

import lombok.Getter;
import me.suxuan.animalhide.commands.GameCommand;
import me.suxuan.animalhide.config.ConfigManager;
import me.suxuan.animalhide.game.GameManager;
import me.suxuan.animalhide.listeners.GameListener;
import me.suxuan.animalhide.manager.DisguiseManager;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public final class AnimalHidePlugin extends JavaPlugin {

	@Getter
	private static AnimalHidePlugin instance;

	private ConfigManager configManager;
	private DisguiseManager disguiseManager;
	private GameManager gameManager;

	@Override
	public void onEnable() {
		instance = this;

		// 初始化管理器
		initManagers();

		// 注册事件监听器
		registerListeners();

		// 注册指令
		registerCommands();
	}

	private void initManagers() {
		configManager = new ConfigManager(this);
		disguiseManager = new DisguiseManager(this);
		gameManager = new GameManager(this, configManager, disguiseManager);
	}

	private void registerListeners() {
		getServer().getPluginManager().registerEvents(new GameListener(gameManager), this);
	}

	private void registerCommands() {
		getCommand("hide").setExecutor(new GameCommand(gameManager));
	}

	@Override
	public void onDisable() {
		// Plugin shutdown logic
	}

}
