package com.spacexrilis.app.data.repository

import com.spacexrilis.app.data.remote.dexscreener.DexScreenerApi
import com.spacexrilis.app.data.remote.rugcheck.RugCheckApi
import com.spacexrilis.app.domain.model.*
import com.spacexrilis.app.domain.repository.ScannerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class ScannerRepositoryImpl(
    private val dex: DexScreenerApi,
    private val rugCheck: RugCheckApi
) : ScannerRepository {
    private val candidates = MutableStateFlow<List<Token>>(emptyList())
    override fun observeCandidates(): Flow<List<Token>> = candidates.asStateFlow()

    override suspend fun refreshCandidates(): Result<List<Token>> = runCatching {
        val profiles = dex.latestTokenProfiles()
            .filter { it.chainId.equals("solana", true) }
        val tokens = profiles.mapNotNull { profile ->
            val mint = profile.tokenAddress ?: return@mapNotNull null
            Token(
                mintAddress = mint,
                name = profile.description?.take(80) ?: "Unknown token",
                symbol = "—",
                imageUrl = profile.icon,
                category = TokenCategory.NEW
            )
        }.distinctBy { it.mintAddress }
        candidates.value = tokens
        tokens
    }

    override suspend fun getPairs(mintAddresses: List<String>): Result<List<TokenPair>> = runCatching {
        mintAddresses.chunked(30).flatMap { chunk ->
            dex.tokenPairs(chunk.joinToString(",")).pairs.orEmpty().mapNotNull { pair ->
                val mint = pair.baseToken?.address ?: return@mapNotNull null
                TokenPair(
                    pairAddress = pair.pairAddress.orEmpty(),
                    mintAddress = mint,
                    dexId = pair.dexId.orEmpty(),
                    priceUsd = pair.priceUsd?.toDoubleOrNull(),
                    liquidityUsd = pair.liquidity?.get("usd"),
                    volume5m = pair.volume?.get("m5"),
                    volume1h = pair.volume?.get("h1"),
                    volume24h = pair.volume?.get("h24"),
                    change5m = pair.priceChange?.get("m5"),
                    change1h = pair.priceChange?.get("h1"),
                    change24h = pair.priceChange?.get("h24"),
                    marketCap = pair.marketCap,
                    fdv = pair.fdv,
                    buys5m = pair.txns?.get("m5")?.get("buys")?.toInt(),
                    sells5m = pair.txns?.get("m5")?.get("sells")?.toInt(),
                    pairCreatedAt = pair.pairCreatedAt
                )
            }
        }
    }

    override suspend fun getTokenRisk(mintAddress: String): Result<RugCheckAnalysis> = runCatching {
        val json = rugCheck.summary(mintAddress)
        val rawScore = json.get("score")?.takeUnless { it.isJsonNull }?.asDouble
        val riskList = json.getAsJsonArray("risks")?.map { item ->
            val obj = item.asJsonObject
            RugCheckRisk(
                name = obj.get("name")?.asString ?: "Unknown risk",
                description = obj.get("description")?.takeUnless { it.isJsonNull }?.asString,
                level = obj.get("level")?.takeUnless { it.isJsonNull }?.asString,
                score = obj.get("score")?.takeUnless { it.isJsonNull }?.asDouble,
                evidence = obj.get("value")?.takeUnless { it.isJsonNull }?.toString()
            )
        }.orEmpty()
        RugCheckAnalysis(
            mintAddress = mintAddress,
            rawScore = rawScore,
            normalizedRiskScore = null,
            riskLevel = json.get("riskLevel")?.takeUnless { it.isJsonNull }?.asString,
            risks = riskList,
            mintAuthorityRevoked = json.get("mintAuthority")?.isJsonNull,
            freezeAuthorityRevoked = json.get("freezeAuthority")?.isJsonNull,
            lpLocked = null,
            lpBurned = null,
            topHolderConcentration = null,
            insiderNetworks = null,
            totalInsiderHolding = null,
            dataAvailable = true
        )
    }

    override suspend fun getTokenHolders(mintAddress: String): Result<List<Holder>> =
        Result.failure(UnsupportedOperationException("Holder enrichment will be implemented in the on-chain analysis module."))

    override suspend fun getToken(mintAddress: String): Result<Token?> =
        Result.success(candidates.value.firstOrNull { it.mintAddress == mintAddress })
}
