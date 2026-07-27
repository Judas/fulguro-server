package com.fulgurogo.common.service

import com.fulgurogo.common.logger.log

/**
 * A [PeriodicFlowService] that refreshes exactly one row per tick, stalest first.
 *
 * This is the shape every aggregator follows: take the row that has gone longest without an update, refresh it from its
 * source, and stamp `updated` either way so the queue keeps rotating. A row that cannot be refreshed is marked as
 * errored rather than retried immediately, which sends it to the back of the queue instead of letting it block.
 *
 * Subclasses implement the three steps and nothing else. In particular they must not re-add a re-entry guard: ticks are
 * already strictly sequential, because the flow's `emit` suspends until the collector returns.
 */
abstract class StalestFirstService<T>(
    initialDelayInSeconds: Long,
    intervalInSeconds: Long,
    private val tag: String
) : PeriodicFlowService(initialDelayInSeconds, intervalInSeconds) {

    /** The row that has gone longest without a refresh, or null when there is nothing to do. */
    protected abstract fun stalest(): T?

    /** Refreshes [stale] from its source. Throwing hands it to [markAsError]. */
    protected abstract suspend fun refresh(stale: T)

    /** Stamps `updated` and sets `error = 1`, so a row that keeps failing does not block the queue. */
    protected abstract fun markAsError(stale: T)

    final override suspend fun onTick() {
        val stale = stalest() ?: return
        try {
            refresh(stale)
        } catch (e: Exception) {
            log(tag, "onTick FAILURE ${e.message}")
            markAsError(stale)
        }
    }
}
