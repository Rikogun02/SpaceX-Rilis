package com.spacexrilis.app.data.remote.dexscreener

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class DexTokenProfileDto(
    val url: String?,
    val chainId: String?,
    val tokenAddress: String?,
    val icon: String?,
    val header: String?,
    val description: String?,
    val links: List<Map<String, String>>?
)
data class DexSearchResponse(val schemaVersion: String?, val pairs: List<DexPairDto>?)
data class DexPairResponse(val schemaVersion: String?, val pairs: List<DexPairDto>?)
data class DexPairDto(
    val chainId: String?,
    val dexId: String?,
    val url: String?,
    val pairAddress: String?,
    val baseToken: DexTokenDto?,
    val quoteToken: DexTokenDto?,
    val priceUsd: String?,
    val txns: Map<String, Map<String, Double>>?,
    val volume: Map<String, Double>?,
    val priceChange: Map<String, Double>?,
    val liquidity: Map<String, Double>?,
    val fdv: Double?,
    val marketCap: Double?,
    val pairCreatedAt: Long?
)
data class DexTokenDto(val address: String?, val name: String?, val symbol: String?)

interface DexScreenerApi {
    @GET("token-profiles/latest/v1")
    suspend fun latestTokenProfiles(): List<DexTokenProfileDto>

    @GET("latest/dex/tokens/{tokenAddresses}")
    suspend fun tokenPairs(@Path("tokenAddresses") addresses: String): DexPairResponse

    @GET("latest/dex/pairs/{chainId}/{pairId}")
    suspend fun pair(@Path("chainId") chainId: String = "solana", @Path("pairId") pairId: String): DexPairResponse

    @GET("latest/dex/search")
    suspend fun search(@Query("q") query: String): DexSearchResponse
}
