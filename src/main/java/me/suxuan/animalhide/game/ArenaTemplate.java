package me.suxuan.animalhide.game;

import lombok.Getter;
import org.bukkit.Location;

import java.util.List;

/**
 * 竞技场模板 (图纸)
 * 记录从配置文件中读取出来的静态地图数据，用于无限克隆对局实例
 */
@Getter
public class ArenaTemplate {
	private final String mapName;
	private final String templateName; // 对应的 Slime 模板名
	private final int minPlayers;
	private final int maxPlayers;
	private final int aiAnimalCount;

	// 无 World 的相对坐标配置
	private final Location configWaitingLobby;
	private final Location configHiderSpawn;
	private final Location configSeekerSpawn;
	private final List<SpawnPoint> configAiSpawns;

	public ArenaTemplate(String mapName, String templateName, int minPlayers, int maxPlayers,
	                     Location configWaitingLobby, Location configHiderSpawn, Location configSeekerSpawn,
	                     List<SpawnPoint> configAiSpawns, int aiAnimalCount) {
		this.mapName = mapName;
		this.templateName = templateName;
		this.minPlayers = minPlayers;
		this.maxPlayers = maxPlayers;
		this.configWaitingLobby = configWaitingLobby;
		this.configHiderSpawn = configHiderSpawn;
		this.configSeekerSpawn = configSeekerSpawn;
		this.configAiSpawns = configAiSpawns;
		this.aiAnimalCount = aiAnimalCount;
	}
}