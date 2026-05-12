package me.suxuan.animalhide.game;

import lombok.Getter;

@Getter
public enum ArenaMode {
	ANIMAL("生物模式"),
	MONSTER("怪物模式");

	private final String displayName;

	ArenaMode(String displayName) {
		this.displayName = displayName;
	}

}