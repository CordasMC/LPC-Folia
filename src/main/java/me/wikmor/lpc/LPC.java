package me.wikmor.lpc;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.cacheddata.CachedMetaData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class LPC extends JavaPlugin implements Listener {

	private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
	private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

	private LuckPerms luckPerms;

	@Override
	public void onEnable() {
		this.luckPerms = getServer().getServicesManager().load(LuckPerms.class);

		saveDefaultConfig();
		getServer().getPluginManager().registerEvents(this, this);
	}

	@Override
	public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
		if (args.length == 1 && "reload".equals(args[0])) {
			reloadConfig();

			sender.sendMessage(LEGACY_SERIALIZER.deserialize("&aLPC has been reloaded."));
			return true;
		}

		return false;
	}

	@Override
	public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
		if (args.length == 1)
			return Collections.singletonList("reload");

		return new ArrayList<>();
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onChat(final AsyncChatEvent event) {
		event.renderer(new CustomChatRenderer());
	}

	private class CustomChatRenderer implements ChatRenderer {
		@Override
		public Component render(Player source, Component sourceDisplayName, Component message, Audience viewer) {
			final String messageText = PlainTextComponentSerializer.plainText().serialize(message);

			final UserManager userManager = luckPerms.getUserManager();
			final User user = userManager.getUser(source.getUniqueId());

			if (user == null) {
				return sourceDisplayName.append(Component.text(": ")).append(message);
			}

			final CachedMetaData metaData = user.getCachedData().getMetaData();
			final String group = user.getPrimaryGroup();

			String format = getConfig().getString(getConfig().getString("group-formats." + group) != null ? "group-formats." + group : "chat-format")
					.replace("{prefix}", metaData.getPrefix() != null ? metaData.getPrefix() : "")
					.replace("{suffix}", metaData.getSuffix() != null ? metaData.getSuffix() : "")
					.replace("{prefixes}", metaData.getPrefixes().keySet().stream().map(key -> metaData.getPrefixes().get(key)).collect(Collectors.joining()))
					.replace("{suffixes}", metaData.getSuffixes().keySet().stream().map(key -> metaData.getSuffixes().get(key)).collect(Collectors.joining()))
					.replace("{world}", source.getWorld().getName())
					.replace("{name}", source.getName())
					.replace("{displayname}", source.getDisplayName())
					.replace("{username-color}", metaData.getMetaValue("username-color") != null ? metaData.getMetaValue("username-color") : "")
					.replace("{message-color}", metaData.getMetaValue("message-color") != null ? metaData.getMetaValue("message-color") : "");

			format = getServer().getPluginManager().isPluginEnabled("PlaceholderAPI") ?
					PlaceholderAPI.setPlaceholders(source, format) : format;

			format = translateHexColorCodes(format);

			String formattedMessageText;
			if (source.hasPermission("lpc.colorcodes") && source.hasPermission("lpc.rgbcodes")) {
				formattedMessageText = translateHexColorCodes(messageText);
			} else if (source.hasPermission("lpc.colorcodes")) {
				formattedMessageText = messageText; 
			} else if (source.hasPermission("lpc.rgbcodes")) {
				formattedMessageText = translateHexColorCodes(messageText);
			} else {
				formattedMessageText = messageText;
			}

			String finalFormat = format.replace("{message}", formattedMessageText).replace("%", "%%");

			return LEGACY_SERIALIZER.deserialize(finalFormat);
		}
	}

	private String translateHexColorCodes(final String message) {
		final Matcher matcher = HEX_PATTERN.matcher(message);
		final StringBuffer buffer = new StringBuffer(message.length() + 4 * 8);

		while (matcher.find()) {
			final String group = matcher.group(1);

			matcher.appendReplacement(buffer, "&x"
					+ "&" + group.charAt(0) + "&" + group.charAt(1)
					+ "&" + group.charAt(2) + "&" + group.charAt(3)
					+ "&" + group.charAt(4) + "&" + group.charAt(5));
		}

		return matcher.appendTail(buffer).toString();
	}
}