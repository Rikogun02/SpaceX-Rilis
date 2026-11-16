package com.spacexrilis.app.data.remote

import com.spacexrilis.app.data.remote.pumpportal.PumpPortalSocket
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.delay

object ScannerRuntime {
    // PumpPortal stream is best-effort; REST polling remains the recovery path.
    fun pumpEvents(): Flow<String> = PumpPortalSocket().events().retryWhen { _, attempt ->
        delay((1_000L shl attempt.coerceAtMost(5).toInt()).coerceAtMost(30_000L))
        true
    }
    const val POLL_INTERVAL_MS = 30_000L
}
