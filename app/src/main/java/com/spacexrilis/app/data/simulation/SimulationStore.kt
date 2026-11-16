package com.spacexrilis.app.data.simulation

import android.content.Context

data class SimPosition(val mint: String, val symbol: String, val entryPrice: Double, val amountUsd: Double, val openedAt: Long)

class SimulationStore(context: Context) {
    private val prefs = context.getSharedPreferences("spacex_simulation", Context.MODE_PRIVATE)
    fun balance(): Double = prefs.getFloat("balance", 1000f).toDouble()
    fun positions(): List<SimPosition> = prefs.getStringSet("positions", emptySet()).orEmpty().mapNotNull {
        val p = it.split("|")
        if (p.size != 5) null else runCatching { SimPosition(p[0], p[1], p[2].toDouble(), p[3].toDouble(), p[4].toLong()) }.getOrNull()
    }
    @Synchronized fun open(mint: String, symbol: String, price: Double, amountUsd: Double): Boolean {
        if (price <= 0 || amountUsd <= 0 || amountUsd > balance() || positions().any { it.mint == mint }) return false
        val rows = positions().toMutableList() + SimPosition(mint, symbol, price, amountUsd, System.currentTimeMillis())
        prefs.edit().putFloat("balance", (balance() - amountUsd).toFloat())
            .putStringSet("positions", rows.map { "${it.mint}|${it.symbol}|${it.entryPrice}|${it.amountUsd}|${it.openedAt}" }.toSet()).apply()
        return true
    }
    @Synchronized fun close(mint: String, currentPrice: Double): Double? {
        val rows = positions()
        val p = rows.firstOrNull { it.mint == mint } ?: return null
        if (currentPrice <= 0) return null
        val proceeds = p.amountUsd * currentPrice / p.entryPrice
        val remaining = rows.filterNot { it.mint == mint }
        prefs.edit().putFloat("balance", (balance() + proceeds).toFloat())
            .putStringSet("positions", remaining.map { "${it.mint}|${it.symbol}|${it.entryPrice}|${it.amountUsd}|${it.openedAt}" }.toSet()).apply()
        return proceeds - p.amountUsd
    }
}
