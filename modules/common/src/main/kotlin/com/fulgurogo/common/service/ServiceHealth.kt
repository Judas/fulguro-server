package com.fulgurogo.common.service

import java.util.concurrent.CopyOnWriteArrayList

/** A point-in-time view of one background service, for the health endpoint. */
data class ServiceHealth(
    val name: String,
    /** False means the service's coroutine is gone and it will never tick again without a restart. */
    val running: Boolean,
    val intervalSeconds: Long,
    /** Null when the service has not completed a tick yet, e.g. still inside its initial delay. */
    val secondsSinceLastSuccess: Long?,
    /** Null before [PeriodicFlowService.start]. */
    val secondsSinceStart: Long?,
    val staleAfterSeconds: Long,
    val consecutiveFailures: Int,
    val lastFailure: String?
) {
    /**
     * Falls back to time since start for a service that has never had a successful tick — otherwise one that fails on
     * every single tick would report healthy forever, since it would never set a success time to age.
     */
    private val secondsWithoutSuccess: Long? = secondsSinceLastSuccess ?: secondsSinceStart

    /**
     * Succeeding is cheap — a tick with no work to do still counts — so going this long without one means the service
     * is either failing every time or wedged inside a blocking call.
     */
    val stale: Boolean = secondsWithoutSuccess != null && secondsWithoutSuccess > staleAfterSeconds

    val healthy: Boolean = running && !stale
}

/**
 * Every service that has been started, so something can report on them.
 *
 * Services register themselves in [PeriodicFlowService.start]; nothing has to keep a list by hand.
 */
object ServiceRegistry {
    private val services = CopyOnWriteArrayList<PeriodicFlowService>()

    internal fun register(service: PeriodicFlowService) {
        if (service !in services) services.add(service)
    }

    fun health(): List<ServiceHealth> = services.map { it.health() }
}
