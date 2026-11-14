package io.noshow.domain;

/** The delivery channel kind. Each maps to an AlertSender implementation. */
public enum ChannelType {
    EMAIL,
    SLACK,
    WEBHOOK
}
