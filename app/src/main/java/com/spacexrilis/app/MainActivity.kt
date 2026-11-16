package com.spacexrilis.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.spacexrilis.app.data.remote.ApiFactory
import com.spacexrilis.app.data.remote.ScannerRuntime
import com.spacexrilis.app.data.simulation.SimulationStore
import com.spacexrilis.app.domain.model.TokenPair
import com.spacexrilis.app.domain.scoring.TokenScorer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class PumpPortalToken(val mint:String,val name:String,val symbol:String,val migrated:Boolean)

private fun parsePumpTokenEvent(raw:String):PumpPortalToken? = runCatching {
    val j=JsonParser.parseString(raw).asJsonObject
    val mint=j.get("mint")?.takeUnless { it.isJsonNull }?.asString ?: return null
    PumpPortalToken(mint,j.get("name")?.takeUnless { it.isJsonNull }?.asString ?: "New Pump.fun token",j.get("symbol")?.takeUnless { it.isJsonNull }?.asString ?: "—",j.has("pool") || j.has("migration"))
}.getOrNull()

private data class ScannerRow(
    val mint: String, val name: String, val symbol: String, val pair: TokenPair,
    val score: Int, val label: String, val reasons: List<String>, val risk: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SpaceXRilisApp(SimulationStore(applicationContext)) }
    }
}

@Composable
private fun SpaceXRilisApp(sim: SimulationStore) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<ScannerRow>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Menunggu pemindaian") }
    var updated by remember { mutableStateOf("—") }
    var stream by remember { mutableStateOf("Menghubungkan PumpPortal…") }
    var pumpTokens by remember { mutableStateOf<Map<String, PumpPortalToken>>(emptyMap()) }
    var balance by remember { mutableDoubleStateOf(sim.balance()) }
    var positionCount by remember { mutableIntStateOf(sim.positions().size) }
    var amount by remember { mutableStateOf("10") }
    var onlyLiquid by remember { mutableStateOf(false) }

    suspend fun refresh() {
        if (loading) return
        loading = true
        status = "Mengambil kandidat token Solana…"
        try {
            val found = withContext(Dispatchers.IO) {
                val profiles = ApiFactory.dexScreener.latestTokenProfiles()
                    .filter { it.chainId.equals("solana", true) && !it.tokenAddress.isNullOrBlank() }
                    .distinctBy { it.tokenAddress }.take(60)
                val pairDtos = profiles.mapNotNull { it.tokenAddress }.chunked(25).flatMap { chunk ->
                    ApiFactory.dexScreener.tokenPairs(chunk.joinToString(",")).pairs.orEmpty()
                }.filter { it.chainId.equals("solana", true) }
                profiles.mapNotNull { profile ->
                    val mint = profile.tokenAddress ?: return@mapNotNull null
                    val dto = pairDtos.filter { it.baseToken?.address == mint }
                        .maxByOrNull { it.liquidity?.get("usd") ?: 0.0 } ?: return@mapNotNull null
                    val pair = TokenPair(
                        pairAddress = dto.pairAddress.orEmpty(), mintAddress = mint, dexId = dto.dexId.orEmpty(),
                        priceUsd = dto.priceUsd?.toDoubleOrNull(), liquidityUsd = dto.liquidity?.get("usd"),
                        volume5m = dto.volume?.get("m5"), volume1h = dto.volume?.get("h1"),
                        volume24h = dto.volume?.get("h24"), change5m = dto.priceChange?.get("m5"),
                        change1h = dto.priceChange?.get("h1"), change24h = dto.priceChange?.get("h24"),
                        marketCap = dto.marketCap, fdv = dto.fdv,
                        buys5m = dto.txns?.get("m5")?.get("buys")?.toInt(),
                        sells5m = dto.txns?.get("m5")?.get("sells")?.toInt(),
                        pairCreatedAt = dto.pairCreatedAt
                    )
                    val score = TokenScorer.score(pair, false)
                    ScannerRow(mint, dto.baseToken?.name ?: profile.description ?: "Unknown token",
                        dto.baseToken?.symbol ?: "—", pair, score.score, score.label, score.reasons,
                        "Belum diperiksa RugCheck")
                }.sortedByDescending { it.score }
            }
            val liveOnly = rows.filter { old -> pumpTokens.containsKey(old.mint) && found.none { it.mint == old.mint } }
            rows = (found + liveOnly).distinctBy { it.mint }
            updated = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            status = "Pemindaian selesai · ${found.size} token dengan pair pasar"
        } catch (e: Exception) {
            status = "Gagal memuat data: ${e.message ?: "periksa koneksi internet"}"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        launch {
            ScannerRuntime.pumpEvents()
                .catch { stream = "Stream tidak tersedia · REST polling tetap aktif" }
                .collect { raw ->
                    stream = "PumpPortal aktif · event diterima"
                    val event = parsePumpTokenEvent(raw)
                    if (event != null) {
                        pumpTokens = pumpTokens + (event.mint to event)
                        val dto = withContext(Dispatchers.IO) {
                            runCatching {
                                ApiFactory.dexScreener.tokenPairs(event.mint).pairs.orEmpty()
                                    .filter { it.chainId.equals("solana", true) }
                                    .maxByOrNull { it.liquidity?.get("usd") ?: 0.0 }
                            }.getOrNull()
                        }
                        val eventPair = if (dto == null) TokenPair(
                            pairAddress = "", mintAddress = event.mint, dexId = if (event.migrated) "pump-swap" else "pump.fun",
                            priceUsd = null, liquidityUsd = null, volume5m = null, volume1h = null, volume24h = null,
                            change5m = null, change1h = null, change24h = null, marketCap = null, fdv = null,
                            buys5m = null, sells5m = null, pairCreatedAt = null
                        ) else TokenPair(
                            pairAddress = dto.pairAddress.orEmpty(), mintAddress = event.mint, dexId = dto.dexId.orEmpty(),
                            priceUsd = dto.priceUsd?.toDoubleOrNull(), liquidityUsd = dto.liquidity?.get("usd"),
                            volume5m = dto.volume?.get("m5"), volume1h = dto.volume?.get("h1"), volume24h = dto.volume?.get("h24"),
                            change5m = dto.priceChange?.get("m5"), change1h = dto.priceChange?.get("h1"),
                            change24h = dto.priceChange?.get("h24"), marketCap = dto.marketCap, fdv = dto.fdv,
                            buys5m = dto.txns?.get("m5")?.get("buys")?.toInt(), sells5m = dto.txns?.get("m5")?.get("sells")?.toInt(),
                            pairCreatedAt = dto.pairCreatedAt
                        )
                        val scored = TokenScorer.score(eventPair, false)
                        val liveRow = ScannerRow(event.mint, dto?.baseToken?.name ?: event.name,
                            dto?.baseToken?.symbol ?: event.symbol, eventPair, scored.score,
                            if (dto == null) "PAIR BELUM TERSEDIA" else if (event.migrated) "MIGRATED" else "PUMP.FUN LIVE",
                            scored.reasons, "Belum diperiksa RugCheck")
                        rows = (listOf(liveRow) + rows.filterNot { it.mint == event.mint }).take(120)
                    }
                }
        }
        while (true) {
            refresh()
            delay(ScannerRuntime.POLL_INTERVAL_MS)
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Scaffold { insets ->
            Column(
                Modifier.fillMaxSize().padding(insets).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text("SPACEX RILIS", style = MaterialTheme.typography.headlineMedium)
                Text("SOLANA MEMECOIN SCANNER", style = MaterialTheme.typography.labelMedium)
                Card {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("AUTO BUY · SIMULATION ONLY", color = MaterialTheme.colorScheme.primary)
                        Text("Saldo virtual: $${String.format(Locale.US, "%.2f", balance)}  |  Posisi: $positionCount")
                        Text("Polling 30 detik · $stream", style = MaterialTheme.typography.bodySmall)
                        Text("Update: $updated", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { scope.launch { refresh() } }, enabled = !loading) {
                        Text(if (loading) "Memindai…" else "Scan sekarang")
                    }
                    OutlinedButton(onClick = { onlyLiquid = !onlyLiquid }) {
                        Text(if (onlyLiquid) "Tampilkan semua" else "Likuiditas ≥ $3k")
                    }
                }
                Text(status, style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(rows.filter { !onlyLiquid || (it.pair.liquidityUsd ?: 0.0) >= 3000.0 }, key = { it.mint }) { row ->
                        Card {
                            Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${row.name} (${row.symbol})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    Text("${row.score}/100", color = MaterialTheme.colorScheme.primary)
                                }
                                Text(row.mint, style = MaterialTheme.typography.labelSmall)
                                Text("Harga $${row.pair.priceUsd ?: 0.0} · LP $${row.pair.liquidityUsd ?: 0.0}")
                                Text("Vol 1j $${row.pair.volume1h ?: 0.0} · 5m ${row.pair.change5m ?: 0.0}%")
                                Text("${row.label} · ${row.reasons.take(2).joinToString(" · ")}", style = MaterialTheme.typography.bodySmall)
                                Text(row.risk, style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(onClick = {
                                        scope.launch {
                                            val report: JsonObject? = withContext(Dispatchers.IO) {
                                                runCatching { ApiFactory.rugCheck.summary(row.mint) }.getOrNull()
                                            }
                                            val message = if (report == null) "RugCheck tidak tersedia · status belum terverifikasi"
                                            else {
                                                val raw = report.get("score")?.takeUnless { it.isJsonNull }?.toString() ?: "tidak tersedia"
                                                val normalized = report.get("score_normalised")?.takeUnless { it.isJsonNull }?.toString() ?: "tidak tersedia"
                                                val lpLocked = report.get("lpLockedPct")?.takeUnless { it.isJsonNull }?.toString() ?: "tidak tersedia"
                                                val names = report.getAsJsonArray("risks")?.take(2)?.joinToString { it.asJsonObject.get("name")?.asString ?: "Risk" } ?: "Daftar risiko tidak tersedia"
                                                "RugCheck raw: $raw · normalized: $normalized · LP locked: $lpLocked% · $names"
                                            }
                                            rows = rows.map { if (it.mint == row.mint) it.copy(risk = message) else it }
                                        }
                                    }) { Text("Cek risiko") }
                                    OutlinedButton(onClick = {
                                        val usd = amount.toDoubleOrNull() ?: 10.0
                                        val ok = sim.open(row.mint, row.symbol, row.pair.priceUsd ?: 0.0, usd)
                                        if (ok) {
                                            balance = sim.balance()
                                            positionCount = sim.positions().size
                                            status = "Posisi simulasi dibuka untuk ${row.symbol}"
                                        } else status = "Simulasi gagal: cek saldo, harga, atau posisi duplikat"
                                    }) { Text("Beli simulasi") }
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amount, onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == '.' }.take(9) },
                        label = { Text("Nominal USD") }, modifier = Modifier.weight(1f), singleLine = true
                    )
                    OutlinedButton(onClick = {
                        val pos = sim.positions().firstOrNull()
                        val price = rows.firstOrNull { it.mint == pos?.mint }?.pair?.priceUsd
                        if (pos != null && price != null) {
                            sim.close(pos.mint, price)
                            balance = sim.balance()
                            positionCount = sim.positions().size
                            status = "Posisi simulasi ditutup"
                        } else status = "Tidak ada posisi dengan harga pasar tersedia"
                    }) { Text("Tutup posisi") }
                }
                Text("Tidak ada transaksi nyata. Jangan pernah memasukkan seed phrase/private key. Skor bukan saran investasi.", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
