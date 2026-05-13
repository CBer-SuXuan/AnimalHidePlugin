package me.suxuan.animalhide;

import lombok.Getter;
import me.suxuan.animalhide.commands.GameCommand;
import me.suxuan.animalhide.config.ConfigManager;
import me.suxuan.animalhide.game.GameManager;
import me.suxuan.animalhide.hooks.PAPIExpansion;
import me.suxuan.animalhide.listeners.*;
import me.suxuan.animalhide.manager.DatabaseManager;
import me.suxuan.animalhide.manager.DisguiseManager;
import me.suxuan.animalhide.manager.ScoreboardManager;
import me.suxuan.animalhide.manager.TutorialManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public final class AnimalHidePlugin extends JavaPlugin {

	@Getter
	private static AnimalHidePlugin instance;

	private ConfigManager configManager;
	private DisguiseManager disguiseManager;
	private GameManager gameManager;
	private ScoreboardManager scoreboardManager;
	private DatabaseManager databaseManager;
	private TutorialManager tutorialManager;

	@Override
	public void onEnable() {
		instance = this;

		// 初始化管理器
		initManagers();

		// 注册事件监听器
		registerListeners();

		// 注册指令
		registerCommands();

		if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
			new PAPIExpansion(gameManager).register();
			getComponentLogger().info("成功挂载 PlaceholderAPI，已注册占位符！");
		}
	}

	private void initManagers() {
		configManager = new ConfigManager(this);
		disguiseManager = new DisguiseManager(this);
		gameManager = new GameManager(this, configManager, disguiseManager);
		scoreboardManager = new ScoreboardManager(this, gameManager);
		databaseManager = new DatabaseManager(this);
		tutorialManager = new TutorialManager(this);
	}

	private void registerListeners() {
		PluginManager pm = getServer().getPluginManager();
		pm.registerEvents(new CombatListener(gameManager), this);
		pm.registerEvents(new ConnectionListener(gameManager), this);
		pm.registerEvents(new GameRuleListener(gameManager), this);
		pm.registerEvents(new InteractionListener(gameManager), this);
		pm.registerEvents(new ChatListener(gameManager), this);
	}

	private void registerCommands() {
		getCommand("hide").setExecutor(new GameCommand(gameManager));
	}

	@Override
	public void onDisable() {
		gameManager.emergencyCleanup();
		databaseManager.close();
		tutorialManager.clearTutorialNPCs();
	}

}
