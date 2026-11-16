package com.spacexrilis.app.data.simulation

import android.content.Context

data class SimPosition(val mint:String,val symbol:String,val entryPrice:Double,val amountUsd:Double,val openedAt:Long)
data class SimTrade(val id:String,val mint:String,val symbol:String,val entryPrice:Double,val exitPrice:Double,val amountUsd:Double,val pnlUsd:Double,val closedAt:Long)

class SimulationStore(ctx:Context) {
    private val p=ctx.getSharedPreferences("spacex_simulation",Context.MODE_PRIVATE)
    fun balance()=p.getFloat("balance",1000f).toDouble()
    fun positions():List<SimPosition> = p.getStringSet("positions",emptySet()).orEmpty().mapNotNull { v ->
        val a=v.split("|"); if(a.size!=5) null else runCatching { SimPosition(a[0],a[1],a[2].toDouble(),a[3].toDouble(),a[4].toLong()) }.getOrNull()
    }
    fun history():List<SimTrade> = p.getStringSet("history",emptySet()).orEmpty().mapNotNull { v ->
        val a=v.split("|"); if(a.size!=8) null else runCatching { SimTrade(a[0],a[1],a[2],a[3].toDouble(),a[4].toDouble(),a[5].toDouble(),a[6].toDouble(),a[7].toLong()) }.getOrNull()
    }.sortedBy { it.closedAt }
    fun scannerEnabled()=p.getBoolean("scanner_enabled",true)
    fun setScannerEnabled(v:Boolean)=p.edit().putBoolean("scanner_enabled",v).apply()
    fun autoBuy()=p.getBoolean("auto_buy",false)
    fun setAutoBuy(v:Boolean)=p.edit().putBoolean("auto_buy",v).apply()
    fun lot()=p.getFloat("lot",10f).toDouble()
    fun setLot(v:Double)=p.edit().putFloat("lot",v.coerceIn(1.0,100000.0).toFloat()).apply()
    fun stopLoss()=p.getFloat("stop_loss",20f).toDouble()
    fun setStopLoss(v:Double)=p.edit().putFloat("stop_loss",v.coerceIn(0.0,100.0).toFloat()).apply()
    fun takeProfit()=p.getFloat("take_profit",40f).toDouble()
    fun setTakeProfit(v:Double)=p.edit().putFloat("take_profit",v.coerceIn(0.0,10000.0).toFloat()).apply()
    fun minScore()=p.getInt("min_score",80)
    fun setMinScore(v:Int)=p.edit().putInt("min_score",v.coerceIn(0,100)).apply()
    fun scoreWeight(key:String,default:Int)=p.getInt("score_$key",default)
    fun setScoreWeight(key:String,value:Int)=p.edit().putInt("score_$key",value.coerceIn(0,100)).apply()
    fun filterConfig(stage:String):String?=p.getString("filter_$stage",null)
    fun setFilterConfig(stage:String,value:String)=p.edit().putString("filter_$stage",value).apply()

    @Synchronized fun open(m:String,s:String,price:Double,usd:Double):Boolean {
        if(price<=0||usd<=0||usd>balance()||positions().any{it.mint==m}) return false
        val all=positions()+SimPosition(m,s,price,usd,System.currentTimeMillis())
        p.edit().putFloat("balance",(balance()-usd).toFloat()).putStringSet("positions",all.map{"${it.mint}|${it.symbol}|${it.entryPrice}|${it.amountUsd}|${it.openedAt}"}.toSet()).apply()
        return true
    }
    @Synchronized fun close(m:String,price:Double):Double? {
        val all=positions(); val x=all.firstOrNull{it.mint==m}?:return null
        if(price<=0)return null
        val proceeds=x.amountUsd*price/x.entryPrice
        val pnl=proceeds-x.amountUsd
        val trade=SimTrade("${m}_${System.currentTimeMillis()}",m,x.symbol,x.entryPrice,price,x.amountUsd,pnl,System.currentTimeMillis())
        val updated=history()+trade
        p.edit().putFloat("balance",(balance()+proceeds).toFloat())
            .putStringSet("positions",all.filterNot{it.mint==m}.map{"${it.mint}|${it.symbol}|${it.entryPrice}|${it.amountUsd}|${it.openedAt}"}.toSet())
            .putStringSet("history",updated.map{"${it.id}|${it.mint}|${it.symbol}|${it.entryPrice}|${it.exitPrice}|${it.amountUsd}|${it.pnlUsd}|${it.closedAt}"}.toSet()).apply()
        return pnl
    }
}
