package com.spacexrilis.app.domain.scoring

import com.spacexrilis.app.domain.model.TokenPair

data class ScoreResult(val score: Int, val label: String, val reasons: List<String>)

object TokenScorer {
    fun score(pair: TokenPair, riskAvailable: Boolean): ScoreResult {
        var score = 0
        val reasons = mutableListOf<String>()
        val liq = pair.liquidityUsd ?: 0.0
        val vol = pair.volume1h ?: 0.0
        val buys = pair.buys5m ?: 0
        val sells = pair.sells5m ?: 0
        val change = pair.change5m ?: 0.0
        if (liq >= 25_000) { score += 25; reasons += "Likuiditas kuat" }
        else if (liq >= 8_000) { score += 16; reasons += "Likuiditas cukup" }
        else if (liq >= 3_000) { score += 7; reasons += "Likuiditas rendah" }
        else { reasons += "Likuiditas sangat rendah" }
        if (vol >= 20_000) { score += 20; reasons += "Volume 1 jam aktif" }
        else if (vol >= 5_000) { score += 12; reasons += "Volume 1 jam sedang" }
        else if (vol > 0) score += 4
        val total = buys + sells
        if (total > 0) {
            val ratio = buys.toDouble() / total
            score += (ratio * 20).toInt()
            if (ratio >= .60) reasons += "Tekanan beli dominan"
            else if (ratio < .40) reasons += "Tekanan jual dominan"
        }
        if (change in 0.0..8.0) { score += 10; reasons += "Pergerakan 5 menit terkendali" }
        else if (change > 8.0) { score += 3; reasons += "Harga bergerak cepat; risiko mengejar harga" }
        else if (change < -12.0) reasons += "Penurunan tajam"
        if (riskAvailable) { score += 10; reasons += "Data risiko RugCheck tersedia" }
        else reasons += "Risiko belum diverifikasi RugCheck"
        val finalScore = score.coerceIn(0, 100)
        val label = when {
            !riskAvailable -> "BELUM TERVERIFIKASI"
            finalScore >= 75 -> "PANTAU KETAT"
            finalScore >= 55 -> "MENENGAH"
            else -> "RISIKO TINGGI"
        }
        return ScoreResult(finalScore, label, reasons)
    }
}
