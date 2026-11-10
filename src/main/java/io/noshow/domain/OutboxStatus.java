package io.noshow.domain;

/** Delivery state of a queued notification. */
public enum OutboxStatus {
    /** Not yet delivered (or awaiting a retry). */
    PENDING,
    /** Delivered successfully. */
    SENT,
    /** Gave up after the retry cap. */
    FAILED
}
