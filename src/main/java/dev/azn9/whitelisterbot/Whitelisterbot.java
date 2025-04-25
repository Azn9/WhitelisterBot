package dev.azn9.whitelisterbot;

import org.bukkit.plugin.java.JavaPlugin;

public final class Whitelisterbot extends JavaPlugin {

    private Bot bot;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();

        String token = this.getConfig().getString("config.token");
        long channelId = this.getConfig().getLong("config.channel");
        long threadId = this.getConfig().getLong("config.threadId");

        if (token == null || token.isEmpty() || (channelId == 0 && threadId == 0)) {
            this.getServer().getLogger().severe("Vous n'avez pas précisé toutes les configurations requises !");

            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.bot = new Bot(this, token, channelId, threadId);
        this.bot.start();
    }

    @Override
    public void onDisable() {
        this.bot.stop();
    }
}
