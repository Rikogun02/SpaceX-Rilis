package com.spacexrilis.app.domain.scoring

import com.spacexrilis.app.domain.model.TokenPair

data class ScoreWeights(
    val liquidity:Int=25,
    val volume:Int=20,
    val buyPressure:Int=20,
    val momentum:Int=15,
    val safety:Int=20
) {
    fun normalized(): ScoreWeights {
        val values=listOf(liquidity,volume,buyPressure,momentum,safety).map{it.coerceAtLeast(0)}
        val total=values.sum().coerceAtLeast(1)
        val scaled=values.map{(it*100.0/total).toInt()}
        return copy(liquidity=scaled[0],volume=scaled[1],buyPressure=scaled[2],momentum=scaled[3],safety=(100-scaled.take(4).sum()).coerceAtLeast(0))
    }
}

data class ScoreResult(val score:Int,val label:String,val reasons:List<String>)

object TokenScorer {
    fun score(p:TokenPair,riskAvailable:Boolean,weights:ScoreWeights=ScoreWeights()):ScoreResult {
        val w=weights.normalized()
        val l=p.liquidityUsd?:0.0
        val v=p.volume1h?:0.0
        val b=p.buys5m?:0
        val sell=p.sells5m?:0
        val c=p.change5m?:0.0
        val liquidity=when { l>=25000->100; l>=8000->70; l>=3000->40; else->10 }
        val volume=when { v>=20000->100; v>=5000->60; v>0->25; else->0 }
        val pressure=if(b+sell>0) (b.toDouble()/(b+sell)*100).toInt() else 0
        val momentum=when { c in 0.0..8.0->100; c>8->35; c>=-12->55; else->10 }
        val safety=if(riskAvailable)100 else 20
        val score=((liquidity*w.liquidity+volume*w.volume+pressure*w.buyPressure+momentum*w.momentum+safety*w.safety)/100.0).toInt().coerceIn(0,100)
        val reasons=buildList {
            add(if(l>=8000)"Likuiditas memadai" else "Likuiditas rendah")
            add(if(v>=5000)"Volume aktif" else "Volume rendah")
            if(b+sell>0)add(if(pressure>=60)"Tekanan beli dominan" else "Tekanan beli lemah")
            add(if(c in 0.0..8.0)"Momentum terkendali" else "Momentum perlu diwaspadai")
            add(if(riskAvailable)"Data keamanan tersedia" else "Belum diverifikasi RugCheck")
        }
        val label=when { !riskAvailable->"BELUM TERVERIFIKASI"; score>=75->"PANTAU KETAT"; score>=55->"MENENGAH"; else->"RISIKO TINGGI" }
        return ScoreResult(score,label,reasons)
    }
}
