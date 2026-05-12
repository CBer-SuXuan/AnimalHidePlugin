package me.suxuan.animalhide.manager;

import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.MobDisguise;
import me.libraryaddict.disguise.disguisetypes.watchers.LivingWatcher;
import me.suxuan.animalhide.AnimalHidePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * 变身管理器
 * 负责调用 LibsDisguises API 实现玩家的变身与复原
 */
public class DisguiseManager {

	private final AnimalHidePlugin plugin;

	public DisguiseManager(AnimalHidePlugin plugin) {
		this.plugin = plugin;
	}

	/**
	 * 将玩家变身为指定的动物
	 *
	 * @param player 目标玩家
	 * @param type   变身的动物类型 (例如 DisguiseType.PIG)
	 */
	public void disguisePlayer(Player player, DisguiseType type) {
		if (!type.isMob()) {
			plugin.getComponentLogger().warn("尝试将玩家变为非生物类型: {}", type.name());
			return;
		}

		MobDisguise disguise = new MobDisguise(type);

		// 配置变身属性
		disguise.setViewSelfDisguise(true);  // 设置玩家自己能看到自己的变身形态
		// disguise.setname(false);  // 隐藏名字标签
		disguise.setHearSelfDisguise(false);  // 静音

		// 配置观察者属性
		LivingWatcher watcher = disguise.getWatcher();
		watcher.setGlowing(false);
		watcher.setCustomNameVisible(false);

		// 应用变身
		DisguiseAPI.disguiseToAll(player, disguise);

		player.sendMessage(Component.text("你已经变身为: ", NamedTextColor.YELLOW)
				.append(Component.text(type.name(), NamedTextColor.GREEN)));
	}

	/**
	 * 移除玩家的变身状态
	 *
	 * @param player 目标玩家
	 */
	public void undisguisePlayer(Player player) {
		if (DisguiseAPI.isDisguised(player)) {
			DisguiseAPI.undisguiseToAll(player);
			player.sendMessage(Component.text("你的伪装已解除！", NamedTextColor.RED));
		}
	}
}