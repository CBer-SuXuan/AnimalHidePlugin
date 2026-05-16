package me.suxuan.animalhide.commands;

import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

/**
 * 子命令接口。
 * <p>
 * 每个 /hide xxx 都实现这个接口，由 {@link GameCommand} 统一分发。
 * 子命令收到的 {@code args} 已经去掉了自身命令名（即 args[0] 是用户输入的第一个参数）。
 */
public interface SubCommand {

	/**
	 * 子命令名（用于分发匹配），需要全小写。
	 */
	String getName();

	/**
	 * 用法提示，用于错误时回显给玩家。
	 */
	String getUsage();

	/**
	 * 需要的权限节点；返回 {@code null} 表示无需权限即可使用。
	 */
	String getPermission();

	/**
	 * 是否对所有人显示在 tab 补全里。
	 * 一般 false 表示需要管理员权限的命令；分发器在补全时也会按 {@link #getPermission()} 过滤。
	 */
	default boolean isAdminCommand() {
		return getPermission() != null;
	}

	/**
	 * 执行命令。
	 *
	 * @param sender 命令发送者
	 * @param args   去掉子命令名后的剩余参数
	 * @return 返回值会直接给 Bukkit 的 onCommand 用，约定返回 true
	 */
	boolean execute(CommandSender sender, String[] args);

	/**
	 * Tab 补全。
	 *
	 * @param sender 命令发送者
	 * @param args   去掉子命令名后的剩余参数，最后一项是用户当前正在输入的不完整字符串
	 */
	default List<String> tabComplete(CommandSender sender, String[] args) {
		return Collections.emptyList();
	}
}
