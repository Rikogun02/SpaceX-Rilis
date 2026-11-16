package com.spacexrilis.app.domain.usecase

import com.spacexrilis.app.domain.repository.ScannerRepository

class RefreshScannerUseCase(private val repository: ScannerRepository) {
    suspend operator fun invoke() = repository.refreshCandidates()
}

class GetTokenRiskUseCase(private val repository: ScannerRepository) {
    suspend operator fun invoke(mintAddress: String) = repository.getTokenRisk(mintAddress)
}

class GetTokenMarketDataUseCase(private val repository: ScannerRepository) {
    suspend operator fun invoke(mints: List<String>) = repository.getPairs(mints)
}
