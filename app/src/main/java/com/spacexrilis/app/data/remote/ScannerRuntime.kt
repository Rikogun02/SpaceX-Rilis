package com.spacexrilis.app.data.remote

import com.spacexrilis.app.data.remote.pumpportal.PumpPortalSocket
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen

object ScannerRuntime {
    fun pumpEvents(apiKey: String): Flow<String> =
        PumpPortalSocket(apiKey).events().retryWhen { _, attempt ->
            delay((1000L shl attempt.coerceAtMost(5).toInt()).coerceAtMost(30000L))
            true
        }
    const val POLL_INTERVAL_MS = 30000L
}
