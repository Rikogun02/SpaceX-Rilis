package com.spacexrilis.app.domain.repository

import com.spacexrilis.app.domain.model.*
import kotlinx.coroutines.flow.Flow

interface ScannerRepository {
    fun observeCandidates(): Flow<List<Token>>
    suspend fun refreshCandidates(): Result<List<Token>>
    suspend fun getPairs(mintAddresses: List<String>): Result<List<TokenPair>>
    suspend fun getTokenRisk(mintAddress: String): Result<RugCheckAnalysis>
    suspend fun getTokenHolders(mintAddress: String): Result<List<Holder>>
    suspend fun getToken(mintAddress: String): Result<Token?>
}
