package com.spacexrilis.app.data.remote.rugcheck

import com.google.gson.JsonObject
import retrofit2.http.GET
import retrofit2.http.Path

interface RugCheckApi {
    @GET("v1/tokens/{mint}/report/summary")
    suspend fun summary(@Path("mint") mint: String): JsonObject

    @GET("v1/tokens/{mint}/report")
    suspend fun report(@Path("mint") mint: String): JsonObject
}
