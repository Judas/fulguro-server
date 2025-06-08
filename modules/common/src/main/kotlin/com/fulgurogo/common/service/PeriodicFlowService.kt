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
    private val flowExceptionHandler = CoroutineExceptionHandler { _, e ->
        log(TAG, "Error during periodic service", e)
        stop()
    }

    fun start() {
        job = CoroutineScope(Dispatchers.IO + flowExceptionHandler).launch {
            flow.collect { onTick() }
        }
    }

    fun stop() {
        job?.cancel()
    }

    abstract fun onTick()
}