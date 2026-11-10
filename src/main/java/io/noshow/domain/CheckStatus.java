package io.noshow.domain;

/**
 * Lifecycle of a monitored job.
 *
 * <p>Milestone 1 only ever sets {@link #NEW} (just created, no ping yet) and
 * {@link #UP} (a ping has arrived). {@link #LATE}, {@link #DOWN} and
 * {@link #PAUSED} are defined now so the state machine in Milestone 2 has
 * somewhere to go.
 */
public enum CheckStatus {
    /** Created, but no ping has ever been received. */
    NEW,
    /** Last expected ping arrived on time. */
    UP,
    /** Past its expected time but still within the grace period. */
    LATE,
    /** Grace period elapsed with no ping. */
    DOWN,
    /** Monitoring intentionally suspended. */
    PAUSED
}
