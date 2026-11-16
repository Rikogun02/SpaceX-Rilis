package com.spacexrilis.app.data.remote.pumpportal

import okhttp3.*
import okio.ByteString
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class PumpPortalSocket(private val apiKey: String = "") {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    fun events(): Flow<String> = callbackFlow {
        val encodedKey = URLEncoder.encode(apiKey.trim(), StandardCharsets.UTF_8.name())
        val url = if (apiKey.isBlank()) "wss://pumpportal.fun/api/data"
            else "wss://pumpportal.fun/api/data?api-key=$encodedKey"
        val socket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("{\"method\":\"subscribeNewToken\"}")
                webSocket.send("{\"method\":\"subscribeMigration\"}")
            }
            override fun onMessage(webSocket: WebSocket, text: String) { trySend(text) }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) { trySend(bytes.utf8()) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { close(t) }
        })
        awaitClose { socket.close(1000, "scanner stopped") }
    }
}
