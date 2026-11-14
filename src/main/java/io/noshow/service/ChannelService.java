package io.noshow.service;

import io.noshow.domain.AlertChannel;
import io.noshow.domain.ChannelType;
import io.noshow.notify.ChannelSender;
import io.noshow.repo.AlertChannelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages alert channels: create, list, delete, and verify. Verification
 * sends a test message through the channel's sender and, if it succeeds,
 * marks the channel verified so delivery (M4.3) will use it.
 */
@Service
public class ChannelService {

    private final AlertChannelRepository channels;
    private final Map<ChannelType, ChannelSender> senders;
    private final Clock clock;

    public ChannelService(AlertChannelRepository channels, List<ChannelSender> senderList, Clock clock) {
        this.channels = channels;
        this.senders = new EnumMap<>(ChannelType.class);
        for (ChannelSender sender : senderList) {
            this.senders.put(sender.type(), sender);
        }
        this.clock = clock;
    }

    @Transactional
    public AlertChannel create(ChannelType type, String config) {
        return channels.save(new AlertChannel(UUID.randomUUID(), type, config.trim(), clock.instant()));
    }

    @Transactional(readOnly = true)
    public List<AlertChannel> list() {
        return channels.findAll();
    }

    @Transactional
    public void delete(UUID id) {
        channels.deleteById(id);
    }

    /**
     * Send a test message to the channel; on success, mark it verified.
     *
     * @throws IllegalArgumentException if the channel does not exist
     * @throws IllegalStateException    if no sender is configured for its type
     */
    @Transactional
    public void verify(UUID id) {
        AlertChannel channel = channels.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No such channel: " + id));
        ChannelSender sender = senders.get(channel.getType());
        if (sender == null) {
            throw new IllegalStateException("No sender configured for channel type " + channel.getType());
        }
        sender.send(channel, "NoShow: test message", "This is a test from NoShow. Your channel is working.");
        channel.markVerified();
        channels.save(channel);
    }
}
