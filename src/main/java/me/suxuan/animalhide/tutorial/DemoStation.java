package me.suxuan.animalhide.tutorial;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.List;

/**
 * 大厅里一个已部署的"教程演示桩"实例。
 * <p>
 * 持有其全部活动实体引用，便于动画刷新与清理。
 *
 * <ul>
 *   <li>{@link #carrier} - 主载体（动物或盔甲架）</li>
 *   <li>{@link #hologram} - 头顶的悬浮文字（TextDisplay）</li>
 *   <li>{@link #extras} - 动画过程中临时生成的实体（绵羊、烟花、显示器等）</li>
 * </ul>
 */
public class DemoStation {

	private final String id;
	private final TutorialDemo demo;
	private final Location anchor;
	private Entity carrier;
	private final TextDisplay hologram;
	private final List<Entity> extras = new ArrayList<>();

	private long nextAnimationTick;

	public DemoStation(String id, TutorialDemo demo, Location anchor, Entity carrier, TextDisplay hologram) {
		this.id = id;
		this.demo = demo;
		this.anchor = anchor.clone();
		this.carrier = carrier;
		this.hologram = hologram;
		this.nextAnimationTick = 0L;
	}

	public String getId() {
		return id;
	}

	public TutorialDemo getDemo() {
		return demo;
	}

	public Location getAnchor() {
		return anchor.clone();
	}

	public Entity getCarrier() {
		return carrier;
	}

	public void setCarrier(Entity carrier) {
		this.carrier = carrier;
	}

	public TextDisplay getHologram() {
		return hologram;
	}

	public List<Entity> getExtras() {
		return extras;
	}

	public long getNextAnimationTick() {
		return nextAnimationTick;
	}

	public void setNextAnimationTick(long tick) {
		this.nextAnimationTick = tick;
	}

	/**
	 * 销毁该演示桩拥有的全部实体（载体 + 悬浮字 + 附属）。
	 */
	public void destroy() {
		if (carrier != null && carrier.isValid()) carrier.remove();
		if (hologram != null && hologram.isValid()) hologram.remove();
		for (Entity e : extras) {
			if (e != null && e.isValid()) e.remove();
		}
		extras.clear();
	}
}
