package com.example.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

object P2PClient {
    private const val TAG = "P2PClient"

    suspend fun sendMessage(
        host: String,
        port: Int,
        jsonMessage: String
    ): Boolean = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        try {
            socket = Socket()
            socket.connect(InetSocketAddress(host, port), 4000) // 4 seconds timeout
            val writer = OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)
            writer.write(jsonMessage)
            writer.flush()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Transmission failure to $host:$port -> ${e.localizedMessage}")
            false
        } finally {
            try {
                socket?.close()
            } catch (ex: Exception) {
                // ignore
            }
        }
    }
}
