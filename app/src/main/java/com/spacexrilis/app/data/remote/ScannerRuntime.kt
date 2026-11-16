package com.spacexrilis.app.data.remote
import com.spacexrilis.app.data.remote.pumpportal.PumpPortalSocket
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen
object ScannerRuntime{
 fun pumpEvents():Flow<String> = PumpPortalSocket().events().retryWhen{_,a->delay((1000L shl a.coerceAtMost(5).toInt()).coerceAtMost(30000L));true}
 const val POLL_INTERVAL_MS=30000L
}