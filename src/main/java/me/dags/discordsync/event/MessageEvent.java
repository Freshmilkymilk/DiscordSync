package me.dags.discordsync.event;

import me.dags.discordsync.PluginHelper;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.impl.AbstractEvent;

public class MessageEvent extends AbstractEvent {

    private final String author;
    private final String avatar;
    private final String content;
    private final String channel;
    private final String guild;

    public MessageEvent(String guild, String author, String avatar, String content) {
        this(guild, "", author, avatar, content);
    }

    public MessageEvent(String guild, String channel, String author, String avatar, String content) {
        this.author = author;
        this.avatar = avatar;
        this.content = content;
        this.channel = channel;
        this.guild = guild;
    }

    public String getAuthor() {
        return author;
    }

    public String getAvatar() {
        return avatar;
    }

    @Override
    public Cause getCause() {
        return PluginHelper.getDefaultCause();
    }

    public String getChannel() {
        return channel;
    }

    public String getContent() {
        return content;
    }

    public String getGuild() {
        return guild;
    }
}
