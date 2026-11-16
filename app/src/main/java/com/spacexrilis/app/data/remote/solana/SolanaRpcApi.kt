package com.spacexrilis.app.data.remote.solana

import com.google.gson.JsonObject
import retrofit2.http.Body
import retrofit2.http.POST

data class SolanaRpcRequest(
    val jsonrpc: String = "2.0",
    val id: Int = 1,
    val method: String,
    val params: List<Any>
)
data class SolanaRpcResponse<T>(val jsonrpc: String?, val id: Int?, val result: T?, val error: JsonObject?)
interface SolanaRpcApi {
    @POST("/")
    suspend fun call(@Body request: SolanaRpcRequest): SolanaRpcResponse<JsonObject>
}
