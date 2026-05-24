package me.suxuan.animalhide.tutorial;

import org.bukkit.Location;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.List;

/**
 * queue 实例内的纯文本教程节点。
 */
public class QueueTutorialText {

	private final String id;
	private final Location anchor;
	private final TextDisplay display;

	public QueueTutorialText(String id, Location anchor, TextDisplay display) {
		this.id = id;
		this.anchor = anchor;
		this.display = display;
	}

	public String getId() {
		return id;
	}

	public Location getAnchor() {
		return anchor;
	}

	public TextDisplay getDisplay() {
		return display;
	}

	public void destroy() {
		if (display != null && display.isValid()) {
			display.remove();
		}
	}

	public static List<String> normalizeLines(List<String> lines) {
		List<String> out = new ArrayList<>();
		for (String line : lines) {
			if (line == null) continue;
			String trimmed = line.trim();
			if (!trimmed.isEmpty()) out.add(trimmed);
		}
		return out;
	}
}
