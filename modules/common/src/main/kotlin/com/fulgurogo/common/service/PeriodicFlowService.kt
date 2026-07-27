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

    /**
     * Only reached when the flow itself fails, or when a tick throws an [Error] rather than an [Exception]: ordinary
     * tick failures are handled in [start] and never get here.
     */
    private val flowExceptionHandler = CoroutineExceptionHandler { _, e ->
        log(TAG, "${serviceName()} DIED and will not tick again", e)
        stop()
    }

    fun start() {
        job = CoroutineScope(Dispatchers.IO + flowExceptionHandler).launch {
            flow.collect {
                // A failing tick must not take the service down with it. Every onTick() reads the database before it
                // reaches its own try/catch, so until this guard existed one unreachable-database moment stopped the
                // service for the rest of the process lifetime, silently.
                try {
                    onTick()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log(TAG, "${serviceName()} tick FAILURE, retrying in ${intervalInSeconds}s", e)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    private fun serviceName(): String = this::class.simpleName ?: "PeriodicFlowService"

    abstract fun onTick()
}