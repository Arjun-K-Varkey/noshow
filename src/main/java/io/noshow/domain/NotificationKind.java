package io.noshow.domain;

/** What a queued notification is about. */
public enum NotificationKind {
    /** A check went DOWN. */
    DOWN,
    /** A DOWN/LATE check recovered to UP. */
    RECOVERY
}
