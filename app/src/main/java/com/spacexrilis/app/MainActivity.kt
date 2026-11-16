package com.spacexrilis.app

import android.os.Bundle
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.spacexrilis.app.data.remote.ApiFactory
import com.spacexrilis.app.data.remote.ScannerRuntime
import com.spacexrilis.app.data.remote.pumpportal.PumpPortalKeyStore
import com.spacexrilis.app.data.simulation.SimulationStore
import com.spacexrilis.app.data.simulation.SimPosition
import com.spacexrilis.app.data.simulation.SimTrade
import com.spacexrilis.app.domain.model.TokenPair
import com.spacexrilis.app.domain.scoring.TokenScorer
import com.spacexrilis.app.domain.scoring.ScoreWeights
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class PumpPortalToken(val mint:String,val name:String,val symbol:String,val migrated:Boolean)
private enum class TokenStage { NEW, BONDING, MIGRATED }
private enum class AppSection(val title:String, val subtitle:String) {
    FILTER("Filter", "New, bonding curve, dan migrasi"),
    POSITIONS("Posisi", "Posisi terbuka dan PnL"),
    STATS("Statistik", "Performa trade dan riwayat"),
    ADVANCED("Advance", "Pengaturan simulasi dan risiko")
}
private data class ScannerRow(
    val mint:String, val name:String, val symbol:String, val pair:TokenPair,
    val score:Int, val label:String, val reasons:List<String>, val risk:String,
    val stage:TokenStage = TokenStage.NEW
)

private fun parsePumpTokenEvent(raw:String):PumpPortalToken? = runCatching {
    val j=JsonParser.parseString(raw).asJsonObject
    val mint=j.get("mint")?.takeUnless { it.isJsonNull }?.asString ?: return null
    PumpPortalToken(mint,j.get("name")?.takeUnless { it.isJsonNull }?.asString ?: "New Pump.fun token",
        j.get("symbol")?.takeUnless { it.isJsonNull }?.asString ?: "—", j.has("pool") || j.has("migration"))
}.getOrNull()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); setContent { SpaceXRilisApp(SimulationStore(applicationContext)) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpaceXRilisApp(sim:SimulationStore) {
    val scope= rememberCoroutineScope()
    val context= LocalContext.current
    val clipboard= LocalClipboardManager.current
    val keyStore= remember { PumpPortalKeyStore(context) }
    var pumpApiKey by remember { mutableStateOf(keyStore.read()) }
    var keyDraft by remember { mutableStateOf("") }
    var showKeyDialog by remember { mutableStateOf(false) }
    val drawerState= rememberDrawerState(DrawerValue.Closed)
    var section by remember { mutableStateOf(AppSection.FILTER) }
    var stage by remember { mutableStateOf(TokenStage.NEW) }
    var rows by remember { mutableStateOf<List<ScannerRow>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var scannerEnabled by remember { mutableStateOf(sim.scannerEnabled()) }
    var status by remember { mutableStateOf("Menunggu pemindaian") }
    var updated by remember { mutableStateOf("—") }
    var stream by remember { mutableStateOf("Menghubungkan PumpPortal…") }
    var pumpTokens by remember { mutableStateOf<Map<String,PumpPortalToken>>(emptyMap()) }
    var balance by remember { mutableDoubleStateOf(sim.balance()) }
    var positions by remember { mutableStateOf(sim.positions()) }
    var history by remember { mutableStateOf(sim.history()) }
    var amount by remember { mutableStateOf(sim.lot().toString()) }
    var autoBuy by remember { mutableStateOf(sim.autoBuy()) }
    var stopLoss by remember { mutableStateOf(sim.stopLoss().toString()) }
    var takeProfit by remember { mutableStateOf(sim.takeProfit().toString()) }
    var minScore by remember { mutableStateOf(sim.minScore().toString()) }
    var onlyLiquid by remember { mutableStateOf(false) }
    var minMc by remember { mutableStateOf("0") }
    var maxMc by remember { mutableStateOf("1000000000") }
    var minVol by remember { mutableStateOf("0") }
    var minLp by remember { mutableStateOf("0") }
    var minBuyCount by remember { mutableStateOf("0") }
    var maxSellCount by remember { mutableStateOf("999999") }
    var filterScore by remember { mutableStateOf("0") }
    var wLiquidity by remember { mutableFloatStateOf(sim.scoreWeight("liquidity",25).toFloat()) }
    var wVolume by remember { mutableFloatStateOf(sim.scoreWeight("volume",20).toFloat()) }
    var wBuy by remember { mutableFloatStateOf(sim.scoreWeight("buy",20).toFloat()) }
    var wMomentum by remember { mutableFloatStateOf(sim.scoreWeight("momentum",15).toFloat()) }
    var wSafety by remember { mutableFloatStateOf(sim.scoreWeight("safety",20).toFloat()) }
    val scoreWeights=ScoreWeights(wLiquidity.toInt(),wVolume.toInt(),wBuy.toInt(),wMomentum.toInt(),wSafety.toInt())

    fun syncPortfolio() { balance=sim.balance(); positions=sim.positions(); history=sim.history() }
    fun updateScannerNotification(enabled:Boolean) {
        val manager=context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId="spacex_scanner"
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) manager.createNotificationChannel(
            NotificationChannel(channelId,"SpaceX Rilis Scanner",NotificationManager.IMPORTANCE_LOW).apply { description="Status scanner Pump.fun" }
        )
        if(!enabled) { manager.cancel(7101); return }
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return
        val notification=NotificationCompat.Builder(context,channelId)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("SpaceX Rilis · Scanner aktif")
            .setContentText("Memantau token Pump.fun · polling 30 detik")
            .setOngoing(true).setOnlyAlertOnce(true).setPriority(NotificationCompat.PRIORITY_LOW).build()
        manager.notify(7101,notification)
    }
    val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted && scannerEnabled) updateScannerNotification(true)
    }
    fun currentPrice(mint:String)=rows.firstOrNull{it.mint==mint}?.pair?.priceUsd
    fun pnl(pos:SimPosition, price:Double?):Double? = if(price==null || pos.entryPrice<=0) null else pos.amountUsd*(price/pos.entryPrice-1.0)

    suspend fun refresh() {
        if(loading) return
        loading=true; status="Mengambil kandidat token Solana…"
        try {
            val found=withContext(Dispatchers.IO) {
                val profiles=ApiFactory.dexScreener.latestTokenProfiles().filter{it.chainId.equals("solana",true)&&!it.tokenAddress.isNullOrBlank()}.distinctBy{it.tokenAddress}.take(60)
                val pairs=profiles.mapNotNull{it.tokenAddress}.chunked(25).flatMap{ApiFactory.dexScreener.tokenPairs(it.joinToString(",")).pairs.orEmpty()}.filter{it.chainId.equals("solana",true)}
                profiles.mapNotNull { p ->
                    val mint=p.tokenAddress?:return@mapNotNull null
                    val dto=pairs.filter{it.baseToken?.address==mint}.maxByOrNull{it.liquidity?.get("usd")?:0.0}?:return@mapNotNull null
                    val pair=TokenPair(dto.pairAddress.orEmpty(),mint,dto.dexId.orEmpty(),dto.priceUsd?.toDoubleOrNull(),dto.liquidity?.get("usd"),
                        dto.volume?.get("m5"),dto.volume?.get("h1"),dto.volume?.get("h24"),dto.priceChange?.get("m5"),dto.priceChange?.get("h1"),
                        dto.priceChange?.get("h24"),dto.marketCap,dto.fdv,dto.txns?.get("m5")?.get("buys")?.toInt(),dto.txns?.get("m5")?.get("sells")?.toInt(),dto.pairCreatedAt)
                    val scored=TokenScorer.score(pair,false,scoreWeights)
                    val ev=pumpTokens[mint]
                    ScannerRow(mint,dto.baseToken?.name?:p.description?:"Unknown token",dto.baseToken?.symbol?:"—",pair,scored.score,scored.label,
                        scored.reasons,"Belum diperiksa RugCheck",when { ev?.migrated==true->TokenStage.MIGRATED; ev!=null->TokenStage.BONDING; else->TokenStage.NEW })
                }.sortedByDescending{it.score}
            }
            val liveOnly=rows.filter{old->pumpTokens.containsKey(old.mint)&&found.none{it.mint==old.mint}}
            rows=(found+liveOnly).distinctBy{it.mint}
            updated=SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(Date())
            // Stop-loss / take-profit are applied to virtual positions only.
            positions=sim.positions()
            positions.forEach { pos ->
                val p=currentPrice(pos.mint) ?: return@forEach
                val change=(p/pos.entryPrice-1.0)*100.0
                if ((sim.stopLoss()>0 && change<=-sim.stopLoss()) || (sim.takeProfit()>0 && change>=sim.takeProfit())) {
                    sim.close(pos.mint,p)
                    status="Posisi ${pos.symbol} ditutup otomatis oleh SL/TP simulasi"
                }
            }
            if(sim.autoBuy()) {
                val candidate=rows.firstOrNull{it.score>=sim.minScore() && it.stage!=TokenStage.MIGRATED && (it.pair.priceUsd?:0.0)>0 && sim.positions().none{p->p.mint==it.mint}}
                if(candidate!=null && sim.open(candidate.mint,candidate.symbol,candidate.pair.priceUsd!!,sim.lot()))
                    status="Auto Buy simulasi: ${candidate.symbol} · skor ${candidate.score}"
            }
            syncPortfolio()
            if(status.startsWith("Mengambil")) status="Pemindaian selesai · ${found.size} token"
        } catch(e:Exception) { status="Gagal memuat data: ${e.message?:"periksa koneksi"}" }
        finally { loading=false }
    }

    LaunchedEffect(pumpApiKey,scannerEnabled) {
        if(!scannerEnabled) { stream="Scanner nonaktif"; updateScannerNotification(false); return@LaunchedEffect }
        updateScannerNotification(true)
        launch {
            ScannerRuntime.pumpEvents(pumpApiKey).catch{stream="Stream tidak tersedia · REST polling tetap aktif"}.collect { raw ->
                stream="PumpPortal aktif · event diterima"
                val event=parsePumpTokenEvent(raw)
                if(event!=null) {
                    pumpTokens=pumpTokens+(event.mint to event)
                    val dto=withContext(Dispatchers.IO){runCatching{ApiFactory.dexScreener.tokenPairs(event.mint).pairs.orEmpty().filter{it.chainId.equals("solana",true)}.maxByOrNull{it.liquidity?.get("usd")?:0.0}}.getOrNull()}
                    val pair=if(dto==null) TokenPair("",event.mint,if(event.migrated)"pump-swap" else "pump.fun",null,null,null,null,null,null,null,null,null,null,null,null,null)
                    else TokenPair(dto.pairAddress.orEmpty(),event.mint,dto.dexId.orEmpty(),dto.priceUsd?.toDoubleOrNull(),dto.liquidity?.get("usd"),dto.volume?.get("m5"),dto.volume?.get("h1"),dto.volume?.get("h24"),dto.priceChange?.get("m5"),dto.priceChange?.get("h1"),dto.priceChange?.get("h24"),dto.marketCap,dto.fdv,dto.txns?.get("m5")?.get("buys")?.toInt(),dto.txns?.get("m5")?.get("sells")?.toInt(),dto.pairCreatedAt)
                    val score=TokenScorer.score(pair,false,scoreWeights)
                    val live=ScannerRow(event.mint,dto?.baseToken?.name?:event.name,dto?.baseToken?.symbol?:event.symbol,pair,score.score,
                        if(event.migrated)"MIGRATED" else "PUMP.FUN LIVE",score.reasons,"Belum diperiksa RugCheck",if(event.migrated)TokenStage.MIGRATED else TokenStage.BONDING)
                    rows=(listOf(live)+rows.filterNot{it.mint==event.mint}).take(120)
                }
            }
        }
        while(true){refresh();delay(ScannerRuntime.POLL_INTERVAL_MS)}
    }

    MaterialTheme(colorScheme=darkColorScheme()) {
        ModalNavigationDrawer(
            drawerState=drawerState,
            gesturesEnabled=false,
            drawerContent={
                ModalDrawerSheet {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text("SPACEX RILIS",style=MaterialTheme.typography.titleLarge)
                        Text("SOLANA TRADING DESK",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                    }
                    HorizontalDivider()
                    AppSection.entries.forEach { item ->
                        NavigationDrawerItem(label={Text(item.title)},selected=section==item,
                            onClick={section=item;scope.launch{drawerState.close()}},modifier=Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                            badge={Text(if(item==AppSection.POSITIONS) positions.size.toString() else "")})
                        Text(item.subtitle,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(start=30.dp,bottom=6.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Saldo virtual  $"+"%.2f".format(Locale.US,balance),modifier=Modifier.padding(16.dp),style=MaterialTheme.typography.bodyMedium)
                }
            }
        ) {
            Scaffold(bottomBar={ NavigationBar { AppSection.entries.forEach { item -> NavigationBarItem(selected=section==item,onClick={section=item},icon={Text(item.title.take(1))},label={Text(item.title)}) } } }) { pad ->
                Column(Modifier.fillMaxSize().padding(pad).padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment= androidx.compose.ui.Alignment.CenterVertically) {

                        Column(Modifier.weight(1f)) {
                            Text(section.title.uppercase(),style=MaterialTheme.typography.titleLarge)
                            Text(section.subtitle,style=MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick={keyDraft=pumpApiKey;showKeyDialog=true}){Text("API")}
                    }
                    Card { Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("SCANNER PUMP.FUN",style=MaterialTheme.typography.labelMedium)
                                Text(if(scannerEnabled)"Scanner berjalan" else "Scanner dihentikan",style=MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked=scannerEnabled,onCheckedChange={enabled->
                                scannerEnabled=enabled;sim.setScannerEnabled(enabled)
                                if(enabled && Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                                    notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                else updateScannerNotification(enabled)
                            })
                        }
                        val unrealized=positions.sumOf{pos->pnl(pos,currentPrice(pos.mint))?:0.0}
                        val realized=history.sumOf{it.pnlUsd}
                        val allPnl=realized+unrealized
                        Text("PnL ALL: $"+ "%.2f".format(Locale.US,allPnl)+" · Realized $"+ "%.2f".format(Locale.US,realized)+" · Floating $"+ "%.2f".format(Locale.US,unrealized),
                            color=if(allPnl>=0)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        Text("Saldo virtual: $"+ "%.2f".format(Locale.US,balance)+" · Posisi: ${positions.size}")
                        Text("PumpPortal: $stream",style=MaterialTheme.typography.bodySmall)
                        Text("Update: $updated · Polling 30 detik",style=MaterialTheme.typography.bodySmall)
                    }}
                    when(section) {
                        AppSection.FILTER -> {
                            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                TokenStage.entries.forEach { s ->
                                    FilterChip(selected=stage==s,onClick={stage=s},label={Text(when(s){TokenStage.NEW->"New";TokenStage.BONDING->"Bonding";TokenStage.MIGRATED->"Migrasi"})})
                                }
                            }
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                Button(onClick={scope.launch{refresh()}},enabled=!loading){Text(if(loading)"Memindai…" else "Scan sekarang")}
                                OutlinedButton(onClick={onlyLiquid=!onlyLiquid}){Text(if(onlyLiquid)"Semua LP" else "LP ≥ $3k")}
                            }
                            Card { Column(Modifier.fillMaxWidth().padding(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text("FILTER LENGKAP",style=MaterialTheme.typography.titleSmall)
                                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                    OutlinedTextField(minMc,{minMc=it.filter{ch->ch.isDigit()}},label={Text("MC min $")},modifier=Modifier.weight(1f),singleLine=true)
                                    OutlinedTextField(maxMc,{maxMc=it.filter{ch->ch.isDigit()}},label={Text("MC max $")},modifier=Modifier.weight(1f),singleLine=true)
                                }
                                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                    OutlinedTextField(minLp,{minLp=it.filter{ch->ch.isDigit()}},label={Text("LP min $")},modifier=Modifier.weight(1f),singleLine=true)
                                    OutlinedTextField(minVol,{minVol=it.filter{ch->ch.isDigit()}},label={Text("Vol 1j min $")},modifier=Modifier.weight(1f),singleLine=true)
                                }
                                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                    OutlinedTextField(minBuyCount,{minBuyCount=it.filter{ch->ch.isDigit()}},label={Text("Buy min")},modifier=Modifier.weight(1f),singleLine=true)
                                    OutlinedTextField(maxSellCount,{maxSellCount=it.filter{ch->ch.isDigit()}},label={Text("Sell max")},modifier=Modifier.weight(1f),singleLine=true)
                                    OutlinedTextField(filterScore,{filterScore=it.filter{ch->ch.isDigit()}},label={Text("Skor min")},modifier=Modifier.weight(1f),singleLine=true)
                                }
                            }}
                            Text(status,style=MaterialTheme.typography.bodySmall)
                            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                items(rows.filter{ row ->
                                    val mc=row.pair.marketCap?:row.pair.fdv?:0.0
                                    row.stage==stage && (!onlyLiquid || (row.pair.liquidityUsd?:0.0)>=3000.0) &&
                                    mc >= (minMc.toDoubleOrNull()?:0.0) && mc <= (maxMc.toDoubleOrNull()?:Double.MAX_VALUE) &&
                                    (row.pair.liquidityUsd?:0.0)>=(minLp.toDoubleOrNull()?:0.0) &&
                                    (row.pair.volume1h?:0.0)>=(minVol.toDoubleOrNull()?:0.0) &&
                                    (row.pair.buys5m?:0)>=(minBuyCount.toIntOrNull()?:0) &&
                                    (row.pair.sells5m?:0)<=(maxSellCount.toIntOrNull()?:Int.MAX_VALUE) &&
                                    row.score>=(filterScore.toIntOrNull()?:0)
                                },key={it.mint}) { row ->
                                    Card { Column(Modifier.fillMaxWidth().padding(11.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                            Text("${row.name} (${row.symbol})",style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                                            Text("${row.score}/100",color=MaterialTheme.colorScheme.primary)
                                        }
                                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                            Text(row.mint,style=MaterialTheme.typography.labelSmall,modifier=Modifier.weight(1f))
                                            TextButton(onClick={clipboard.setText(AnnotatedString(row.mint))}){Text("Salin mint")}
                                        }
                                        Text("MC "+(row.pair.marketCap?.let{"$"+"%,.0f".format(Locale.US,it)} ?: row.pair.fdv?.let{"FDV estimasi $"+"%,.0f".format(Locale.US,it)} ?: "belum tersedia")+" · Harga $"+(row.pair.priceUsd?:0.0)+" · LP $"+(row.pair.liquidityUsd?:0.0))
                                        Text("Vol 1j $"+(row.pair.volume1h?:0.0)+" · 5m ${row.pair.change5m?:0.0}%",style=MaterialTheme.typography.bodySmall)
                                        Text("${row.label} · ${row.reasons.take(2).joinToString(" · ")}",style=MaterialTheme.typography.bodySmall)
                                        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                            OutlinedButton(onClick={scope.launch{
                                                val report:JsonObject?=withContext(Dispatchers.IO){runCatching{ApiFactory.rugCheck.summary(row.mint)}.getOrNull()}
                                                val msg=if(report==null)"RugCheck tidak tersedia" else {
                                                    val raw=report.get("score")?.takeUnless{it.isJsonNull}?.toString()?:"N/A"
                                                    val risk=report.getAsJsonArray("risks")?.take(2)?.joinToString{it.asJsonObject.get("name")?.asString?:"Risk"}?:"Tidak ada rincian"
                                                    "RugCheck score $raw · $risk"
                                                }
                                                rows=rows.map{if(it.mint==row.mint)it.copy(risk=msg)else it}
                                            }}){Text("Cek risiko")}
                                            OutlinedButton(onClick={
                                                val ok=sim.open(row.mint,row.symbol,row.pair.priceUsd?:0.0,sim.lot())
                                                syncPortfolio();status=if(ok)"Posisi simulasi dibuka: ${row.symbol}" else "Gagal buka posisi: saldo/harga/duplikat"
                                            }){Text("Beli simulasi")}
                                        }
                                        Text(row.risk,style=MaterialTheme.typography.labelSmall)
                                    }}
                                }
                            }
                        }
                        AppSection.POSITIONS -> {
                            Text("POSISI TERBUKA · PnL BELUM DIREALISASI",style=MaterialTheme.typography.labelMedium)
                            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                items(positions,key={it.mint}) { pos ->
                                    val price=currentPrice(pos.mint)
                                    val value=pnl(pos,price)
                                    Card { Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                        Text(pos.symbol,style=MaterialTheme.typography.titleMedium)
                                        Text("Entry $"+pos.entryPrice+" · Lot $"+pos.amountUsd)
                                        Text("Harga sekarang "+(price?.let{"$"+it}?: "belum tersedia"))
                                        Text("PnL "+(value?.let{"$"+ "%.4f".format(Locale.US,it)+" ("+"%.2f".format(Locale.US,it/pos.amountUsd*100)+"%)"}?:"menunggu harga"),
                                            color=if((value?:0.0)>=0)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                        OutlinedButton(onClick={if(price!=null){sim.close(pos.mint,price);syncPortfolio();status="Posisi simulasi ditutup"}else status="Harga token belum tersedia"}){Text("Tutup posisi")}
                                    }}
                                }
                                if(positions.isEmpty()) item { Text("Belum ada posisi terbuka.") }
                            }
                        }
                        AppSection.STATS -> {
                            val wins=history.count{it.pnlUsd>0}
                            val total=history.sumOf{it.pnlUsd}
                            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    StatCard("Total trade",history.size.toString(),Modifier.weight(1f))
                                    StatCard("Win rate",if(history.isEmpty())"—" else "%.1f%%".format(Locale.US,wins*100.0/history.size),Modifier.weight(1f))
                                }
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    StatCard("Realized PnL","$"+"%.2f".format(Locale.US,total),Modifier.weight(1f))
                                    StatCard("Win / Loss","${wins} / ${history.size-wins}",Modifier.weight(1f))
                                }
                            }
                            Text("RIWAYAT TRADE",style=MaterialTheme.typography.labelMedium)
                            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                items(history.reversed(),key={it.id}) { trade ->
                                    Card { Row(Modifier.fillMaxWidth().padding(10.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                                        Column(Modifier.weight(1f)) {
                                            Text(trade.symbol,style=MaterialTheme.typography.titleSmall)
                                            Text(SimpleDateFormat("dd MMM HH:mm",Locale.getDefault()).format(Date(trade.closedAt)),style=MaterialTheme.typography.labelSmall)
                                        }
                                        Text("$"+"%.3f".format(Locale.US,trade.pnlUsd),color=if(trade.pnlUsd>=0)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                    }}
                                }
                                if(history.isEmpty()) item { Text("Riwayat masih kosong. Trade simulasi yang ditutup akan muncul di sini.") }
                            }
                        }
                        AppSection.ADVANCED -> {
                            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                item {
                                    Card { Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                        Text("AUTO BUY · SIMULASI SAJA",style=MaterialTheme.typography.titleMedium)
                                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                            Text(if(autoBuy)"Auto Buy aktif" else "Auto Buy nonaktif")
                                            Switch(checked=autoBuy,onCheckedChange={autoBuy=it;sim.setAutoBuy(it)})
                                        }
                                        Text("Jika aktif, sistem membuka posisi virtual untuk kandidat skor minimum yang dipilih. Tidak ada transaksi blockchain.",style=MaterialTheme.typography.bodySmall)
                                        OutlinedTextField(value=amount,onValueChange={amount=it.filter{c->c.isDigit()||c=='.'};amount.toDoubleOrNull()?.let{sim.setLot(it)}},label={Text("Lot / nominal per trade (USD)")},singleLine=true)
                                        OutlinedTextField(value=minScore,onValueChange={minScore=it.filter{c->c.isDigit()}.take(3);minScore.toIntOrNull()?.let{sim.setMinScore(it.coerceIn(0,100))}},label={Text("Skor minimum Auto Buy (0–100)")},singleLine=true)
                                        OutlinedTextField(value=stopLoss,onValueChange={stopLoss=it.filter{c->c.isDigit()||c=='.'};stopLoss.toDoubleOrNull()?.let{sim.setStopLoss(it)}},label={Text("Stop Loss (%) · 0 = off")},singleLine=true)
                                        OutlinedTextField(value=takeProfit,onValueChange={takeProfit=it.filter{c->c.isDigit()||c=='.'};takeProfit.toDoubleOrNull()?.let{sim.setTakeProfit(it)}},label={Text("Take Profit (%) · 0 = off")},singleLine=true)
                                        Text("SL/TP diperiksa setiap siklus scan 30 detik. Semua order hanya simulasi.",style=MaterialTheme.typography.bodySmall)
                                    }}
                                }
                                item { Card { Column(Modifier.fillMaxWidth().padding(12.dp)) { Text("MANUAL SCORING · BOBOT"); Text("Likuiditas: "+wLiquidity.toInt()+"%"); Slider(value=wLiquidity,onValueChange={wLiquidity=it;sim.setScoreWeight("liquidity",it.toInt())},valueRange=0f..100f); Text("Volume: "+wVolume.toInt()+"%"); Slider(value=wVolume,onValueChange={wVolume=it;sim.setScoreWeight("volume",it.toInt())},valueRange=0f..100f); Text("Tekanan beli: "+wBuy.toInt()+"%"); Slider(value=wBuy,onValueChange={wBuy=it;sim.setScoreWeight("buy",it.toInt())},valueRange=0f..100f); Text("Momentum: "+wMomentum.toInt()+"%"); Slider(value=wMomentum,onValueChange={wMomentum=it;sim.setScoreWeight("momentum",it.toInt())},valueRange=0f..100f); Text("Keamanan: "+wSafety.toInt()+"%"); Slider(value=wSafety,onValueChange={wSafety=it;sim.setScoreWeight("safety",it.toInt())},valueRange=0f..100f) } } }
                                item {
                                    OutlinedButton(onClick={keyDraft=pumpApiKey;showKeyDialog=true}){Text("Atur API Key PumpPortal")}
                                }
                            }
                        }
                    }
                    Text("Mode simulasi. Tidak ada transaksi nyata. Jangan masukkan seed phrase/private key.",style=MaterialTheme.typography.labelSmall)
                }
                if(showKeyDialog) AlertDialog(
                    onDismissRequest={showKeyDialog=false},title={Text("API Key PumpPortal")},
                    text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                        Text("Key disimpan terenkripsi di perangkat. Jangan kirim key ke chat atau commit ke GitHub.")
                        OutlinedTextField(value=keyDraft,onValueChange={keyDraft=it},label={Text("API key")},visualTransformation=PasswordVisualTransformation(),singleLine=true)
                    }},
                    confirmButton={TextButton(onClick={keyStore.save(keyDraft);pumpApiKey=keyDraft.trim();showKeyDialog=false}){Text("Simpan")}},
                    dismissButton={Row{
                        TextButton(onClick={keyStore.clear();pumpApiKey="";keyDraft="";showKeyDialog=false}){Text("Hapus")}
                        TextButton(onClick={showKeyDialog=false}){Text("Batal")}
                    }}
                )
            }
        }
    }
}

@Composable
private fun StatCard(label:String,value:String,modifier:Modifier=Modifier) {
    Card(modifier) { Column(Modifier.fillMaxWidth().padding(12.dp)) {
        Text(label,style=MaterialTheme.typography.labelSmall)
        Text(value,style=MaterialTheme.typography.titleLarge)
    }}
}
