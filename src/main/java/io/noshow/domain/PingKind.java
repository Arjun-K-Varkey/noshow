package io.noshow.domain;

/**
 * The kind of signal a job sends to its ping URL.
 */
public enum PingKind {
    /** Job has started running (optional; lets us measure run time later). */
    START,
    /** Job finished successfully. This is the ping that keeps a check UP. */
    SUCCESS,
    /** Job ran but failed. Lets us alert immediately instead of waiting. */
    FAIL
}
