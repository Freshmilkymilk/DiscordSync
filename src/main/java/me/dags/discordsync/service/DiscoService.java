package me.dags.discordsync.service;

import me.dags.discordsync.config.DiscordChannel;
import me.dags.discordsync.event.MessageEvent;

public interface DiscoService {

    void shutdown();

    void sendMessage(DiscordChannel channel, MessageEvent message);

    void sendMessageSync(DiscordChannel channel, MessageEvent message);
}
