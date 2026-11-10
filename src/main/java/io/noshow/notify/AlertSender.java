package io.noshow.notify;

import io.noshow.domain.NotificationOutbox;

/**
 * Delivers a single notification over some channel. Email is the first
 * implementation (M3); Slack and webhooks arrive in M4. If no implementation
 * is configured (e.g. mail is not set up), nothing is delivered and queued
 * notifications simply wait.
 */
public interface AlertSender {

    void send(NotificationOutbox notification);
}
