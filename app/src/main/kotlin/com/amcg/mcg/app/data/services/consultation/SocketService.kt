package com.amcg.mcg.app.data.services.consultation

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocketService @Inject constructor() {

    private var socket: Socket? = null
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    companion object {
        private const val TAG = "SocketService"
    }

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    fun connect(serverUrl: String, token: String) {
        try {
            _connectionState.value = ConnectionState.Connecting

            val options = IO.Options().apply {
                auth = mapOf("token" to token)
                transports = arrayOf("websocket", "polling")
                reconnection = true
                reconnectionAttempts = 5
                reconnectionDelay = 1000
            }

            socket = IO.socket(serverUrl, options).apply {
                on(Socket.EVENT_CONNECT) {
                    Log.d(TAG, "Socket connected")
                    _connectionState.value = ConnectionState.Connected
                }

                on(Socket.EVENT_DISCONNECT) {
                    Log.d(TAG, "Socket disconnected")
                    _connectionState.value = ConnectionState.Disconnected
                }

                on(Socket.EVENT_CONNECT_ERROR) { args ->
                    val error = args.firstOrNull()?.toString() ?: "Unknown error"
                    Log.e(TAG, "Socket connection error: $error")
                    _connectionState.value = ConnectionState.Error(error)
                }

                connect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect socket", e)
            _connectionState.value = ConnectionState.Error(e.message ?: "Connection failed")
        }
    }

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
        _connectionState.value = ConnectionState.Disconnected
    }

    fun emit(event: String, data: JSONObject) {
        socket?.emit(event, data)
        Log.d(TAG, "Emitted $event: $data")
    }

    fun emit(event: String, data: JSONObject, callback: (Array<Any>) -> Unit) {
        socket?.emit(event, arrayOf(data), Emitter.Listener { args ->
            callback(args)
        })
    }

    fun on(event: String): Flow<JSONObject> = callbackFlow {
        val listener = Emitter.Listener { args ->
            val data = args.firstOrNull()
            when (data) {
                is JSONObject -> trySend(data)
                is String -> trySend(JSONObject(data))
                else -> Log.w(TAG, "Unexpected data type for $event: ${data?.javaClass}")
            }
        }

        socket?.on(event, listener)
        Log.d(TAG, "Listening to event: $event")

        awaitClose {
            socket?.off(event, listener)
            Log.d(TAG, "Stopped listening to event: $event")
        }
    }

    fun isConnected(): Boolean = socket?.connected() == true

    // WebRTC Signaling Events
    fun joinRoom(roomId: String, sessionId: String) {
        emit("webrtc:join", JSONObject().apply {
            put("roomId", roomId)
            put("sessionId", sessionId)
        })
    }

    fun leaveRoom(roomId: String) {
        emit("webrtc:leave", JSONObject().apply {
            put("roomId", roomId)
        })
    }

    fun sendOffer(roomId: String, offer: String, peerId: String? = null) {
        emit("webrtc:offer", JSONObject().apply {
            put("roomId", roomId)
            put("offer", JSONObject().apply {
                put("type", "offer")
                put("sdp", offer)
            })
            peerId?.let { put("peerId", it) }
        })
    }

    fun sendAnswer(roomId: String, answer: String, peerId: String) {
        emit("webrtc:answer", JSONObject().apply {
            put("roomId", roomId)
            put("peerId", peerId)
            put("answer", JSONObject().apply {
                put("type", "answer")
                put("sdp", answer)
            })
        })
    }

    fun sendIceCandidate(roomId: String, candidate: String, sdpMid: String?, sdpMLineIndex: Int?, peerId: String? = null) {
        emit("webrtc:ice-candidate", JSONObject().apply {
            put("roomId", roomId)
            put("candidate", JSONObject().apply {
                put("candidate", candidate)
                put("sdpMid", sdpMid)
                put("sdpMLineIndex", sdpMLineIndex)
            })
            peerId?.let { put("peerId", it) }
        })
    }

    // Consultation Events
    fun consultationReady(sessionId: String, roomId: String) {
        emit("consultation:ready", JSONObject().apply {
            put("sessionId", sessionId)
            put("roomId", roomId)
        })
    }

    fun sendFrame(sessionId: String, imageBase64: String, captureType: String) {
        emit("consultation:frame", JSONObject().apply {
            put("sessionId", sessionId)
            put("imageData", imageBase64)
            put("captureType", captureType)
            put("timestamp", System.currentTimeMillis())
        })
    }

    fun sendVoice(sessionId: String, audioBase64: String, duration: Long) {
        emit("consultation:voice", JSONObject().apply {
            put("sessionId", sessionId)
            put("audioData", audioBase64)
            put("duration", duration)
            put("timestamp", System.currentTimeMillis())
        })
    }

    fun sendGesture(sessionId: String, gestureType: String, confidence: Float) {
        emit("consultation:gesture", JSONObject().apply {
            put("sessionId", sessionId)
            put("gestureType", gestureType)
            put("confidence", confidence)
            put("timestamp", System.currentTimeMillis())
        })
    }

    fun endConsultation(sessionId: String) {
        emit("consultation:end", JSONObject().apply {
            put("sessionId", sessionId)
        })
    }

    fun requestGuidance(sessionId: String, type: String) {
        emit("consultation:request-guidance", JSONObject().apply {
            put("sessionId", sessionId)
            put("type", type)
        })
    }
}
