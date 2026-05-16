package me.suxuan.animalhide.game;

import lombok.Getter;
import org.bukkit.Location;

import java.util.List;

/**
 * AI 生成点。
 * <p>
 * 每个点可独立配置：
 * - {@code types}：允许在此点生成的生物种类。为空则回退到地图全局 allowed-animals/allowed-monsters。
 * - {@code weight}：抽签权重，默认 1.0。权重越高，被选中作为生成中心的概率越大。
 */
@Getter
public class SpawnPoint {

	private final Location location;
	private final List<String> types;
	private final double weight;

	public SpawnPoint(Location location, List<String> types, double weight) {
		this.location = location;
		this.types = types;
		this.weight = weight > 0 ? weight : 1.0;
	}

	/**
	 * 返回一个绑定了具体 World 的副本，其他字段（types、weight）保持不变。
	 */
	public SpawnPoint withLocation(Location boundLocation) {
		return new SpawnPoint(boundLocation, this.types, this.weight);
	}

	/**
	 * 是否有自定义的种类白名单。
	 */
	public boolean hasTypes() {
		return types != null && !types.isEmpty();
	}
}
