package com.example.network

import android.util.Log
import com.example.data.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket

class P2PServer(
    private val repository: ChatRepository,
    private val scope: CoroutineScope
) {
    private var serverSocket: ServerSocket? = null
    private var listeningJob: Job? = null
    private var activePort: Int = -1

    val isRunning: Boolean
        get() = serverSocket != null && !serverSocket!!.isClosed

    @Synchronized
    fun start(port: Int) {
        if (serverSocket != null && activePort == port) {
            // Already listening on this port
            return
        }
        stop()

        activePort = port
        listeningJob = scope.launch(Dispatchers.IO) {
            try {
                val ss = ServerSocket(port)
                serverSocket = ss
                repository.log("Standalone P2P Server listening on Local Port $port. Stand-by.", "SUCCESS")

                while (!ss.isClosed) {
                    val socket = ss.accept()
                    val clientIp = socket.inetAddress?.hostAddress ?: "Unknown"
                    scope.launch(Dispatchers.IO) {
                        handleConnection(socket, clientIp)
                    }
                }
            } catch (e: Exception) {
                if (serverSocket != null && !serverSocket!!.isClosed) {
                    repository.log("P2P Server encountered a socket error on port $port: ${e.localizedMessage}", "ERROR")
                    Log.e("P2PServer", "Server socket exception", e)
                }
                stop()
            }
        }
    }

    @Synchronized
    fun stop() {
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // ignored
        }
        serverSocket = null
        listeningJob?.cancel()
        listeningJob = null
        activePort = -1
    }

    private suspend fun handleConnection(socket: Socket, clientIp: String) {
        var reader: BufferedReader? = null
        try {
            reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val stringBuilder = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                stringBuilder.append(line).append("\n")
            }
            val jsonString = stringBuilder.toString().trim()
            if (jsonString.isNotEmpty()) {
                if (jsonString == "IDENTIFY") {
                    val identity = repository.getIdentity()
                    if (identity != null) {
                        try {
                            val resp = org.json.JSONObject().apply {
                                put("alias", identity.alias)
                                put("port", identity.serverPort)
                                put("publicKey", identity.publicKey)
                                put("fingerprint", identity.fingerprint)
                            }.toString()
                            val writer = java.io.OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)
                            writer.write(resp + "\n")
                            writer.flush()
                        } catch (e: Exception) {
                            Log.e("P2PServer", "Error responding to IDENTIFY handshake: ${e.localizedMessage}")
                        }
                    }
                } else {
                    repository.handleIncomingPayload(jsonString, clientIp)
                }
            }
        } catch (e: Exception) {
            Log.e("P2PServer", "Error processing connection from $clientIp: ${e.localizedMessage}")
        } finally {
            try {
                reader?.close()
                socket.close()
            } catch (e: Exception) {
                // ignored
            }
        }
    }
}
