package com.fulgurogo.common.service

import com.fulgurogo.common.CommonModule.TAG
import com.fulgurogo.common.logger.log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

abstract class PeriodicFlowService(
    val initialDelayInSeconds: Long = 0,
    val intervalInSeconds: Long = 2
) {
    private val flow: Flow<Boolean> = flow {
        delay(initialDelayInSeconds * 1000)
        while (true) {
            emit(true)
            delay(intervalInSeconds * 1000)
        }
    }
    private var job: Job? = null

    // Reported by the health endpoint. Elapsed time comes from nanoTime so a clock adjustment cannot make a healthy
    // service look stale.
    @Volatile
    private var startedNanos: Long? = null

    @Volatile
    private var lastSuccessNanos: Long? = null

    @Volatile
    private var consecutiveFailures: Int = 0

    @Volatile
    private var lastFailure: String? = null

    /**
     * Only reached when the flow itself fails, or when a tick throws an [Error] rather than an [Exception]: ordinary
     * tick failures are handled in [start] and never get here.
     */
    private val flowExceptionHandler = CoroutineExceptionHandler { _, e ->
        log(TAG, "${serviceName()} DIED and will not tick again", e)
        stop()
    }

    fun start() {
        ServiceRegistry.register(this)
        startedNanos = System.nanoTime()
        job = CoroutineScope(Dispatchers.IO + flowExceptionHandler).launch {
            flow.collect {
                // A failing tick must not take the service down with it. Every onTick() reads the database before it
                // reaches its own try/catch, so until this guard existed one unreachable-database moment stopped the
                // service for the rest of the process lifetime, silently.
                try {
                    onTick()
                    lastSuccessNanos = System.nanoTime()
                    consecutiveFailures = 0
                    lastFailure = null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    consecutiveFailures++
                    lastFailure = e.message ?: e::class.simpleName
                    log(TAG, "${serviceName()} tick FAILURE, retrying in ${intervalInSeconds}s", e)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    fun health(): ServiceHealth = ServiceHealth(
        name = serviceName(),
        running = job?.isActive == true,
        intervalSeconds = intervalInSeconds,
        secondsSinceLastSuccess = lastSuccessNanos?.let { elapsedSeconds(it) },
        secondsSinceStart = startedNanos?.let { elapsedSeconds(it) },
        staleAfterSeconds = staleAfterSeconds(),
        consecutiveFailures = consecutiveFailures,
        lastFailure = lastFailure
    )

    private fun elapsedSeconds(sinceNanos: Long): Long = (System.nanoTime() - sinceNanos) / 1_000_000_000

    /**
     * Generous multiple of the tick interval, so a couple of slow or failed ticks do not raise an alarm. The initial
     * delay is added on so a service is never called stale before it has had the chance to tick once.
     */
    private fun staleAfterSeconds(): Long = maxOf(intervalInSeconds * 5, 60) + initialDelayInSeconds

    private fun serviceName(): String = this::class.simpleName ?: "PeriodicFlowService"

    /**
     * Suspending so a tick can use `delay` for its own throttles instead of `Thread.sleep`. Blocking calls are still
     * fine — this runs on [Dispatchers.IO] — but a suspended tick can be cancelled, so [stop] no longer has to wait
     * for a sleeping tick to finish.
     */
    abstract suspend fun onTick()
}