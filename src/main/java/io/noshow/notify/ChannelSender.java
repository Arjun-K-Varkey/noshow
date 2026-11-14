package io.noshow.notify;

import io.noshow.domain.AlertChannel;
import io.noshow.domain.ChannelType;

/**
 * Delivers a message to a specific {@link AlertChannel}. One implementation
 * per {@link ChannelType}. Used for verification test messages (M4.2) and, in
 * M4.3, for fanning a notification out to each channel.
 */
public interface ChannelSender {

    ChannelType type();

    void send(AlertChannel channel, String subject, String body);
}
