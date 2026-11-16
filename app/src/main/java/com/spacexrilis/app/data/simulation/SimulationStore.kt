package com.spacexrilis.app.data.simulation
import android.content.Context
data class SimPosition(val mint:String,val symbol:String,val entryPrice:Double,val amountUsd:Double,val openedAt:Long)
class SimulationStore(ctx:Context){
 private val p=ctx.getSharedPreferences("spacex_simulation",Context.MODE_PRIVATE)
 fun balance()=p.getFloat("balance",1000f).toDouble()
 fun positions():List<SimPosition> = p.getStringSet("positions",emptySet()).orEmpty().mapNotNull{v->val a=v.split("|");if(a.size!=5)null else runCatching{SimPosition(a[0],a[1],a[2].toDouble(),a[3].toDouble(),a[4].toLong())}.getOrNull()}
 @Synchronized fun open(m:String,s:String,price:Double,usd:Double):Boolean{if(price<=0||usd<=0||usd>balance()||positions().any{it.mint==m})return false;val all=positions()+SimPosition(m,s,price,usd,System.currentTimeMillis());p.edit().putFloat("balance",(balance()-usd).toFloat()).putStringSet("positions",all.map{"${it.mint}|${it.symbol}|${it.entryPrice}|${it.amountUsd}|${it.openedAt}"}.toSet()).apply();return true}
 @Synchronized fun close(m:String,price:Double):Double?{val all=positions();val x=all.firstOrNull{it.mint==m}?:return null;if(price<=0)return null;val proceeds=x.amountUsd*price/x.entryPrice;p.edit().putFloat("balance",(balance()+proceeds).toFloat()).putStringSet("positions",all.filterNot{it.mint==m}.map{"${it.mint}|${it.symbol}|${it.entryPrice}|${it.amountUsd}|${it.openedAt}"}.toSet()).apply();return proceeds-x.amountUsd}
}