package com.spacexrilis.app.data.remote

data class ScannerPollingConfig(
    val globalIntervalSeconds: Long = 30,
    val dexScreenerIntervalSeconds: Long = 30,
    val solanaRpcIntervalSeconds: Long = 30,
    val rugCheckOnDiscovery: Boolean = true,
    val rugCheckRefreshMinutes: Long = 15,
    val websocketEnabled: Boolean = true
) {
    init {
        require(globalIntervalSeconds >= 5)
        require(dexScreenerIntervalSeconds >= 5)
        require(solanaRpcIntervalSeconds >= 5)
        require(rugCheckRefreshMinutes >= 1)
    }
}
