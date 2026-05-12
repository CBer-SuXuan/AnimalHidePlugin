package me.suxuan.animalhide.game;

import lombok.Getter;

/**
 * 玩家角色枚举
 * 用于在游戏结算或事件判定时，快速识别玩家的阵营
 */
@Getter
public enum PlayerRole {
	HIDER("躲藏者"),     // 变身为动物的玩家
	SEEKER("寻找者"),    // 负责抓捕的玩家
	SPECTATOR("旁观者"); // 死亡后或中途加入观看的玩家

	private final String displayName;

	PlayerRole(String displayName) {
		this.displayName = displayName;
	}

}