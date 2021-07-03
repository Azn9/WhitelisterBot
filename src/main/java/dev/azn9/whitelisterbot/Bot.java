package dev.azn9.whitelisterbot;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.reaction.ReactionEmoji;
import net.minecraft.server.v1_16_R3.WhiteListEntry;
import org.bukkit.craftbukkit.v1_16_R3.CraftServer;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

public class Bot {

    private final Plugin plugin;
    private final String token;
    private final long channelId;
    private Thread thread;
    private GatewayDiscordClient client;
    private BukkitTask task;

    public Bot(Plugin plugin, String token, long channelId) {
        this.plugin = plugin;
        this.token = token;
        this.channelId = channelId;
    }

    public void start() {
        this.task = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, () -> {
            try {
                ((CraftServer) this.plugin.getServer()).getHandle().getWhitelist().save();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, 60L, 600L);

        this.thread = new Thread(() -> {
            DiscordClient discordClient = DiscordClient.create(token);
            this.client = discordClient.login().block();

            if (this.client == null) {
                this.plugin.getLogger().severe("Le bot n'a pas pu démarrer !");

                this.plugin.getPluginLoader().disablePlugin(this.plugin);
                return;
            }

            this.client.on(MessageCreateEvent.class).subscribe(event -> {
                if (!event.getMember().isPresent() || event.getMember().get().isBot()) {
                    return;
                }

                Message message = event.getMessage();

                if (message.getChannelId().asLong() == channelId) {
                    String content = message.getContent().trim();

                    if (content.matches("[A-Za-z0-9_]{3,16}")) {
                        message.addReaction(ReactionEmoji.unicode("⌛")).subscribe();

                        try {
                            URL url = new URL("https://playerdb.co/api/player/minecraft/" + content);
                            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                            connection.setConnectTimeout(5000);
                            connection.setReadTimeout(5000);

                            int statusCode = connection.getResponseCode();
                            if (statusCode == 500) {
                                message.removeSelfReaction(ReactionEmoji.unicode("⌛")).then(message.addReaction(ReactionEmoji.unicode("❌"))).then(message.getChannel().flatMap(messageChannel -> messageChannel.createMessage(messageCreateSpec -> {
                                    messageCreateSpec.setContent("Ce pseudo n'a pas été trouvé !");
                                    messageCreateSpec.setMessageReference(message.getId());
                                }))).subscribe();
                            } else if (statusCode == 200) {
                                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                                String inputLine;
                                StringBuilder data = new StringBuilder();
                                while ((inputLine = in.readLine()) != null) {
                                    data.append(inputLine);
                                }
                                in.close();
                                connection.disconnect();

                                JsonObject jsonObject = new JsonParser().parse(data.toString()).getAsJsonObject();
                                if (jsonObject.has("success") && (jsonObject.has("data") && jsonObject.get("data").getAsJsonObject().has("player"))) {
                                    if (jsonObject.get("success").getAsBoolean()) {
                                        JsonObject dataObject = jsonObject.get("data").getAsJsonObject().get("player").getAsJsonObject();

                                        if (dataObject.has("username") && dataObject.has("id")) {
                                            try {
                                                UUID uuid = UUID.fromString(dataObject.get("id").getAsString());

                                                this.plugin.getServer().getScheduler().runTask(this.plugin, () -> ((CraftServer) this.plugin.getServer()).getHandle().getWhitelist().add(new WhiteListEntry(new GameProfile(uuid, dataObject.get("username").getAsString()))));

                                                message.removeSelfReaction(ReactionEmoji.unicode("⌛")).then(message.addReaction(ReactionEmoji.unicode("✅"))).then(message.getChannel().flatMap(messageChannel -> messageChannel.createMessage(messageCreateSpec -> {
                                                    messageCreateSpec.setContent("Vous avez bien été ajouté à la whitelist !");
                                                    messageCreateSpec.setMessageReference(message.getId());
                                                }))).subscribe();
                                            } catch (Exception e) {
                                                e.printStackTrace();

                                                message.removeSelfReaction(ReactionEmoji.unicode("⌛")).then(message.addReaction(ReactionEmoji.unicode("❌"))).then(message.getChannel().flatMap(messageChannel -> messageChannel.createMessage(messageCreateSpec -> {
                                                    messageCreateSpec.setContent("Une erreur est survenue, contactez un développpeur ! (3)");
                                                    messageCreateSpec.setMessageReference(message.getId());
                                                }))).subscribe();
                                            }
                                        } else {
                                            message.removeSelfReaction(ReactionEmoji.unicode("⌛")).then(message.addReaction(ReactionEmoji.unicode("❌"))).then(message.getChannel().flatMap(messageChannel -> messageChannel.createMessage(messageCreateSpec -> {
                                                messageCreateSpec.setContent("Une erreur est survenue, contactez un développpeur !  (2)");
                                                messageCreateSpec.setMessageReference(message.getId());
                                            }))).subscribe();
                                        }
                                    } else {
                                        message.removeSelfReaction(ReactionEmoji.unicode("⌛")).then(message.addReaction(ReactionEmoji.unicode("❌"))).then(message.getChannel().flatMap(messageChannel -> messageChannel.createMessage(messageCreateSpec -> {
                                            messageCreateSpec.setContent("Ce pseudo n'a pas été trouvé !");
                                            messageCreateSpec.setMessageReference(message.getId());
                                        }))).subscribe();
                                    }
                                } else {
                                    message.removeSelfReaction(ReactionEmoji.unicode("⌛")).then(message.addReaction(ReactionEmoji.unicode("❌"))).then(message.getChannel().flatMap(messageChannel -> messageChannel.createMessage(messageCreateSpec -> {
                                        messageCreateSpec.setContent("Une erreur est survenue, contactez un développpeur ! (1)");
                                        messageCreateSpec.setMessageReference(message.getId());
                                    }))).subscribe();
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else {
                        message.addReaction(ReactionEmoji.unicode("❌")).then(message.getChannel().flatMap(messageChannel -> messageChannel.createMessage(messageCreateSpec -> {
                            messageCreateSpec.setContent("Ce pseudo n'est pas valide !");
                            messageCreateSpec.setMessageReference(message.getId());
                        }))).subscribe();
                    }
                }
            });

            this.client.onDisconnect().block();
        });
        this.thread.start();
    }

    public void stop() {
        if (this.task != null) {
            this.task.cancel();
        }

        if (this.thread != null) {
            if (this.client != null) {
                this.client.logout().block();
            }

            this.thread.interrupt();
        }
    }

}
