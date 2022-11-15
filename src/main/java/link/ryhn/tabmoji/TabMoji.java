package link.ryhn.tabmoji;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.ChatColor;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.EnumWrappers.NativeGameMode;
import com.comphenix.protocol.wrappers.EnumWrappers.PlayerInfoAction;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.ClickEvent.Action;
import net.md_5.bungee.api.chat.hover.content.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class TabMoji extends JavaPlugin implements Listener {
	public HashMap<String, String> emojis;
	Pattern EmojiCodePattern = Pattern.compile(":[A-Za-z0-9+_-]+:");

	List<WrappedGameProfile> emojiPlayers = new ArrayList<WrappedGameProfile>();

	public void LoadEmojis() {
		reloadConfig();

		emojis = new HashMap<String, String>();

		FileConfiguration cfg = getConfig();
		Set<String> keys = cfg.getConfigurationSection("emojis").getKeys(false);
		
		for (String k : keys) {
			String v = cfg.getConfigurationSection("emojis").getString(k);
			emojis.put(k, v);
		}


		{
			PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO);
			packet.getPlayerInfoAction().write(0, PlayerInfoAction.REMOVE_PLAYER);
			List<PlayerInfoData> data = new ArrayList<PlayerInfoData>();

			for (WrappedGameProfile p : emojiPlayers) {
				data.add(new PlayerInfoData(p, 0, NativeGameMode.SPECTATOR, WrappedChatComponent.fromText(p.getName())));
			}
			
			packet.getPlayerInfoDataLists().write(0, data);

			for(Player p: Bukkit.getOnlinePlayers())
			try {
				protocolManager.sendServerPacket(p, packet);
			} catch (Exception e) {
				getLogger().warning(e.toString());
			}
		}

		emojiPlayers = new ArrayList<WrappedGameProfile>();
		{
			PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO);
			packet.getPlayerInfoAction().write(0, PlayerInfoAction.ADD_PLAYER);
			List<PlayerInfoData> data = new ArrayList<PlayerInfoData>();
			
			for (String ek : emojis.keySet()) {
				String name = ":" + ek + ":";

				if(name.length() >= 16)
				{
					getLogger().warning("Emoji \"" + name + "\" is longer than 14, it will not be tab completed");
					continue;
				}

				WrappedGameProfile p = new WrappedGameProfile(UUID.randomUUID(), name);
				data.add(new PlayerInfoData(p, 0, NativeGameMode.SPECTATOR, WrappedChatComponent.fromText(p.getName())));
				emojiPlayers.add(p);
			}
			
			packet.getPlayerInfoDataLists().write(0, data);

			for(Player p: Bukkit.getOnlinePlayers())
			try {
				protocolManager.sendServerPacket(p, packet);
			} catch (Exception e) {
				getLogger().warning(e.toString());
			}
		}
	}

	ProtocolManager protocolManager;

	@Override
	public void onEnable() {
		getLogger().info("TabMoji starting");

		FileConfiguration cfg = getConfig();
		cfg.addDefault("emojis.smile", "😃");
		cfg.addDefault("emojis.poop", "💩");
		cfg.options().copyDefaults(true);
		saveConfig();
		reloadConfig();

		Bukkit.getPluginManager().registerEvents(this, this);

		protocolManager = ProtocolLibrary.getProtocolManager();

		LoadEmojis();

		// Commands
		{
			getCommand("emojis").setExecutor(new CommandExecutor() {
				public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
					int page = 0;
					if(args.length > 0) page = Integer.parseInt(args[0]) - 1;

					Object[] keys = emojis.keySet().toArray();

					int pageSize = 8;
					int totalPages = (keys.length/pageSize)+1;

					if(page <= 0 || page > totalPages) page = 0;

					sender.sendMessage(ChatColor.GREEN + "Emojis, page " + (page + 1) + "/" + totalPages);
					
					for (int i=(page * pageSize); (i < Math.min(keys.length, (page + 1) * pageSize)); i++)
					{
						String e = (String)keys[i];
						sender.sendMessage(":" + e + ": - " + emojis.get(e));
					}

					TextComponent prev = new TextComponent(ChatColor.RED + "[Previous]");
					prev.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,  "/emojis " + (page)));
					prev.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Next page") ));

					TextComponent next = new TextComponent(ChatColor.GREEN + "[Next]");
					next.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/emojis " + (page + 2)));
					next.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Next page") ));

					sender.spigot().sendMessage(new ComponentBuilder().append(prev).append(next).create());

					return true;
				}

			});

			getCommand("reloademojis").setExecutor(new CommandExecutor() {
				public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
					LoadEmojis();
					return true;
				}

			});
		}
	}

	@Override
	public void onDisable() {
		getLogger().info("TabMoji stopping");
	}

	@EventHandler
	public void on(AsyncPlayerChatEvent ev) {
		String msg = ev.getMessage();

		msg = Emojify(msg);

		ev.setMessage(msg);
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void on(PlayerJoinEvent ev) {
		PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO);
		packet.getPlayerInfoAction().write(0, PlayerInfoAction.ADD_PLAYER);

		List<PlayerInfoData> data = new ArrayList<PlayerInfoData>();

		for (WrappedGameProfile p : emojiPlayers) {
			data.add(new PlayerInfoData(p, 0, NativeGameMode.SPECTATOR, WrappedChatComponent.fromText(p.getName())));
		}

		packet.getPlayerInfoDataLists().write(0, data);

		try {
			protocolManager.sendServerPacket(ev.getPlayer(), packet);
		} catch (Exception e) {
			getLogger().warning(e.toString());
		}
	}

	@EventHandler
	public void on(SignChangeEvent ev) {
		String[] lines = ev.getLines();

		for (int l = 0; l < lines.length; l++) {
			String line = lines[l];

			line = Emojify(line);

			ev.setLine(l, line);
		}
	}

	public String Emojify(String source)
	{
		source = ChatColor.translateAlternateColorCodes('&', source);

		Matcher matcher = EmojiCodePattern.matcher(source);
		String replacement = "";

		int cursor = 0;
		while(matcher.find())
		{
			int start = matcher.start();
			int end = matcher.end();

			String col = ChatColor.getLastColors(source.substring(0, start));
			String key = source.substring(start+1, end-1);

			if(emojis.containsKey(key))
			{
				replacement += source.substring(cursor, start);

				replacement += "" + ChatColor.RESET + ChatColor.WHITE;
				replacement += emojis.get(key);
				replacement += ChatColor.RESET; 

				replacement += col;
			}
			else
			{
				replacement += source.substring(cursor, end);
			}

			cursor = end;
		}

		return replacement + source.substring(cursor, source.length());
	}
}