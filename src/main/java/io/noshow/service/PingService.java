package io.noshow.service;

import io.noshow.domain.PingKind;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * Records pings. A concurrent evaluator sweep can make a ping's write lose the
 * optimistic-lock race (the sweep commits its transition first). The ping is
 * the source of truth — a job really did check in — so rather than failing the
 * request we retry against the reloaded check until it sticks. Combined with
 * the evaluator skipping on its own conflict, the ping always wins.
 */
@Service
public class PingService {

    private static final int MAX_ATTEMPTS = 3;

    private final PingWriter writer;

    public PingService(PingWriter writer) {
        this.writer = writer;
    }

    /**
     * @return true if the token matched a check, false otherwise.
     */
    public boolean recordPing(String token, PingKind kind, String sourceIp, String userAgent) {
        OptimisticLockingFailureException lastConflict = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                return writer.write(token, kind, sourceIp, userAgent) == PingWriter.Result.RECORDED;
            } catch (OptimisticLockingFailureException conflict) {
                // A sweep updated the check mid-write; reload (next attempt) and retry.
                lastConflict = conflict;
            }
        }
        throw lastConflict;
    }
}
