package com.spacexrilis.app.data.remote

import com.spacexrilis.app.BuildConfig
import com.spacexrilis.app.data.remote.dexscreener.DexScreenerApi
import com.spacexrilis.app.data.remote.rugcheck.RugCheckApi
import com.spacexrilis.app.data.remote.solana.SolanaRpcApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiFactory {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private fun retrofit(baseUrl: String) = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val dexScreener: DexScreenerApi by lazy {
        retrofit(BuildConfig.DEXSCREENER_BASE_URL).create(DexScreenerApi::class.java)
    }
    val rugCheck: RugCheckApi by lazy {
        retrofit(BuildConfig.RUGCHECK_BASE_URL).create(RugCheckApi::class.java)
    }
    val solanaRpc: SolanaRpcApi by lazy {
        retrofit(BuildConfig.SOLANA_RPC_URL).create(SolanaRpcApi::class.java)
    }
}
