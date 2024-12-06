package com.fulgurogo.common.service

import com.fulgurogo.common.logger.Logger.Level.ERROR
import com.fulgurogo.common.logger.Logger.Level.INFO
import com.fulgurogo.common.logger.log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

abstract class PeriodicFlowService(
    val initialDelayInSeconds: Long,
    val intervalInSeconds: Long
) {
    private val flow: Flow<Boolean> = flow {
        delay(initialDelayInSeconds * 1000)
        while (true) {
            emit(true)
            delay(intervalInSeconds * 1000)
        }
    }
    private var job: Job? = null
    private val flowExceptionHandler = CoroutineExceptionHandler { _, e ->
        log(ERROR, "Error during periodic service", e)
        stop()
        start()
    }

    fun start() {
        log(INFO, "start")

        job = CoroutineScope(Dispatchers.IO + flowExceptionHandler).launch {
            log(INFO, "starting periodic job")
            flow.collect { tick() }
        }
    }

    fun stop() {
        log(INFO, "stop")
        job?.let {
            log(INFO, "stopping periodic job")
            it.cancel()
        }
    }

    abstract fun tick()
}