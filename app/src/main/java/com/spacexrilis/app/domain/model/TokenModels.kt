package com.spacexrilis.app.domain.model

enum class TokenCategory { NEW, BONDING, MIGRATED }

data class Token(
    val mintAddress: String,
    val name: String,
    val symbol: String,
    val imageUrl: String? = null,
    val creatorAddress: String? = null,
    val createdAt: Long? = null,
    val category: TokenCategory = TokenCategory.NEW,
    val decimals: Int? = null,
    val totalSupply: Double? = null,
    val mintAuthority: String? = null,
    val freezeAuthority: String? = null,
    val metadataUri: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)

data class TokenPair(
    val pairAddress: String,
    val mintAddress: String,
    val dexId: String,
    val priceUsd: Double?,
    val liquidityUsd: Double?,
    val volume5m: Double?,
    val volume1h: Double?,
    val volume24h: Double?,
    val change5m: Double?,
    val change1h: Double?,
    val change24h: Double?,
    val marketCap: Double?,
    val fdv: Double?,
    val buys5m: Int?,
    val sells5m: Int?,
    val pairCreatedAt: Long?,
    val updatedAt: Long = System.currentTimeMillis()
)

data class Holder(
    val walletAddress: String,
    val mintAddress: String,
    val balance: Double,
    val percentage: Double,
    val isCreator: Boolean = false,
    val isLiquidityPool: Boolean = false,
    val isKnownBurnAddress: Boolean = false
)

data class DevInfo(
    val walletAddress: String,
    val initialHolding: Double?,
    val currentHolding: Double?,
    val holdingPercentage: Double?,
    val previousTokens: Int?,
    val previousRugCount: Int?,
    val sellTransactions: Int?,
    val suspectedDevDump: Boolean,
    val confidence: Double
)

data class BundleAnalysis(
    val mintAddress: String,
    val suspectedWallets: List<String>,
    val suspectedBundleCount: Int,
    val coordinatedBuyPercentage: Double,
    val controlledSupplyPercentage: Double,
    val repeatedBuyPattern: Boolean,
    val riskScore: Int,
    val confidence: Double
)

data class RugCheckRisk(
    val name: String,
    val description: String?,
    val level: String?,
    val score: Double?,
    val evidence: String?
)

data class RugCheckAnalysis(
    val mintAddress: String,
    val rawScore: Double?,
    val normalizedRiskScore: Int?,
    val riskLevel: String?,
    val risks: List<RugCheckRisk>,
    val mintAuthorityRevoked: Boolean?,
    val freezeAuthorityRevoked: Boolean?,
    val lpLocked: Boolean?,
    val lpBurned: Boolean?,
    val topHolderConcentration: Double?,
    val insiderNetworks: Int?,
    val totalInsiderHolding: Double?,
    val dataAvailable: Boolean,
    val detectedAt: Long = System.currentTimeMillis()
)
