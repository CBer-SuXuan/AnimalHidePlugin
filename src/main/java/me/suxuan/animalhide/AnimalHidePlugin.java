package me.suxuan.animalhide;

import lombok.Getter;
import me.suxuan.animalhide.commands.GameCommand;
import me.suxuan.animalhide.config.ConfigManager;
import me.suxuan.animalhide.game.GameManager;
import me.suxuan.animalhide.hooks.PAPIExpansion;
import me.suxuan.animalhide.listeners.*;
import me.suxuan.animalhide.manager.*;
import me.suxuan.slimearena.api.ArenaManager;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
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
	private AISpawnManager aiSpawnManager;
	private TauntManager tauntManager;
	private ExplosiveSheepManager explosiveSheepManager;

	private ArenaManager arenaManager;

	@Override
	public void onEnable() {
		instance = this;

		// 初始化管理器
		if (!initManagers()) {
			return;
		}

		// 注册事件监听器
		registerListeners();

		// 注册指令
		registerCommands();

		// PlaceholderAPI / TAB 已是 hard depend，启动顺序保证它们已加载，可以无条件注册
		new PAPIExpansion(gameManager).register();
		getComponentLogger().info("已注册 AnimalHide PlaceholderAPI 占位符（含关系占位符 %rel_animalhide_color%）");
	}

	private boolean initManagers() {

		RegisteredServiceProvider<ArenaManager> rsp = getServer().getServicesManager().getRegistration(ArenaManager.class);
		if (rsp != null) {
			this.arenaManager = rsp.getProvider();
		} else {
			getLogger().severe("无法找到 SlimeArenaAPI！");
			getServer().getPluginManager().disablePlugin(this);
			return false;
		}

		configManager = new ConfigManager(this);
		disguiseManager = new DisguiseManager(this);
		gameManager = new GameManager(this, configManager, disguiseManager, arenaManager);
		scoreboardManager = new ScoreboardManager(this, gameManager);
		databaseManager = new DatabaseManager(this);
		tutorialManager = new TutorialManager(this);
		aiSpawnManager = new AISpawnManager(this);
		tauntManager = new TauntManager(this);
		explosiveSheepManager = new ExplosiveSheepManager(this);
		return true;
	}

	private void registerListeners() {
		PluginManager pm = getServer().getPluginManager();
		pm.registerEvents(new CombatListener(gameManager), this);
		pm.registerEvents(new ConnectionListener(gameManager), this);
		pm.registerEvents(new GameRuleListener(gameManager), this);
		pm.registerEvents(new InteractionListener(gameManager), this);
		pm.registerEvents(new ChatListener(gameManager), this);
		pm.registerEvents(new GlobalProtectionListener(), this);
	}

	private void registerCommands() {
		if (getCommand("hide") == null) {
			getLogger().severe("命令 hide 未在 plugin.yml 中注册！");
			return;
		}

		GameCommand command = new GameCommand(gameManager);
		getCommand("hide").setExecutor(command);
		getCommand("hide").setTabCompleter(command);
	}

	@Override
	public void onDisable() {
		if (gameManager != null) {
			gameManager.emergencyCleanup();
		}
		if (databaseManager != null) {
			databaseManager.close();
		}
		if (tutorialManager != null) {
			tutorialManager.shutdown();
		}
	}

}
