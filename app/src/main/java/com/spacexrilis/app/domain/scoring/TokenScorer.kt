package com.spacexrilis.app.domain.scoring
import com.spacexrilis.app.domain.model.TokenPair
data class ScoreResult(val score:Int,val label:String,val reasons:List<String>)
object TokenScorer {
 fun score(p:TokenPair,riskAvailable:Boolean):ScoreResult {
  var s=0; val r=mutableListOf<String>(); val l=p.liquidityUsd?:0.0; val v=p.volume1h?:0.0; val b=p.buys5m?:0; val sell=p.sells5m?:0; val c=p.change5m?:0.0
  if(l>=25000){s+=25;r+="Likuiditas kuat"}else if(l>=8000){s+=16;r+="Likuiditas cukup"}else if(l>=3000){s+=7;r+="Likuiditas rendah"}else r+="Likuiditas sangat rendah"
  if(v>=20000){s+=20;r+="Volume 1 jam aktif"}else if(v>=5000){s+=12;r+="Volume 1 jam sedang"}else if(v>0)s+=4
  if(b+sell>0){val q=b.toDouble()/(b+sell);s+=(q*20).toInt();if(q>=.6)r+="Tekanan beli dominan" else if(q<.4)r+="Tekanan jual dominan"}
  if(c in 0.0..8.0){s+=10;r+="Pergerakan 5 menit terkendali"}else if(c>8){s+=3;r+="Risiko mengejar harga"}else if(c< -12)r+="Penurunan tajam"
  if(riskAvailable){s+=10;r+="Data RugCheck tersedia"}else r+="Risiko belum diverifikasi RugCheck"
  val n=s.coerceIn(0,100);val label=when{!riskAvailable->"BELUM TERVERIFIKASI";n>=75->"PANTAU KETAT";n>=55->"MENENGAH";else->"RISIKO TINGGI"}
  return ScoreResult(n,label,r)
 }
}