package com.amcg.mcg.app.ui.screens.consultation

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amcg.mcg.app.data.services.consultation.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.VideoSink
import java.io.ByteArrayOutputStream
import javax.inject.Inject

@HiltViewModel
class VideoConsultationViewModel @Inject constructor(
    private val socketService: SocketService,
    private val webRTCService: WebRTCService,
    private val consultationApiService: ConsultationApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoConsultationUiState())
    val uiState: StateFlow<VideoConsultationUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<VideoConsultationEvent>()
    val events: SharedFlow<VideoConsultationEvent> = _events.asSharedFlow()

    companion object {
        private const val TAG = "VideoConsultationVM"
        private const val FRAME_CAPTURE_INTERVAL = 5000L // 5 seconds
    }

    init {
        observeSocketConnection()
        observeWebRTCState()
        observeAIEvents()
    }

    private fun observeSocketConnection() {
        viewModelScope.launch {
            socketService.connectionState.collect { state ->
                _uiState.update { it.copy(socketState = state) }
            }
        }
    }

    private fun observeWebRTCState() {
        viewModelScope.launch {
            webRTCService.connectionState.collect { state ->
                _uiState.update { it.copy(webRTCState = state) }

                if (state is WebRTCService.WebRTCState.Connected) {
                    _uiState.value.session?.let { session ->
                        socketService.consultationReady(session.sessionId, session.signalingRoomId)
                    }
                }
            }
        }

        viewModelScope.launch {
            webRTCService.localVideoEnabled.collect { enabled ->
                _uiState.update { it.copy(isVideoEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            webRTCService.localAudioEnabled.collect { enabled ->
                _uiState.update { it.copy(isAudioEnabled = enabled) }
            }
        }
    }

    private fun observeAIEvents() {
        viewModelScope.launch {
            socketService.on("ai:guidance").collect { data ->
                val guidance = parseAIGuidance(data)
                _uiState.update { it.copy(currentGuidance = guidance) }
                _events.emit(VideoConsultationEvent.AIGuidanceReceived(guidance))
            }
        }

        viewModelScope.launch {
            socketService.on("ai:detection").collect { data ->
                val detection = parseAIDetection(data)
                _uiState.update {
                    it.copy(detections = it.detections + detection)
                }
                _events.emit(VideoConsultationEvent.AIDetectionReceived(detection))
            }
        }

        viewModelScope.launch {
            socketService.on("ai:response").collect { data ->
                val response = data.optJSONObject("response")
                val textContent = response?.optString("textContent") ?: ""
                val audioContent = response?.optString("audioContent")

                _uiState.update { it.copy(lastAIResponse = textContent) }
                _events.emit(VideoConsultationEvent.AIResponseReceived(textContent, audioContent))
            }
        }

        viewModelScope.launch {
            socketService.on("consultation:analysis-progress").collect { data ->
                val step = data.optString("step")
                val percentage = data.optInt("percentage")
                val message = data.optString("message")

                _uiState.update {
                    it.copy(analysisProgress = AnalysisProgress(step, percentage, message))
                }
            }
        }

        viewModelScope.launch {
            socketService.on("consultation:analysis-complete").collect { data ->
                val resultId = data.optString("resultId")
                val summary = data.optString("summary")

                _uiState.update { it.copy(analysisComplete = true) }
                _events.emit(VideoConsultationEvent.AnalysisComplete(resultId, summary))
            }
        }

        viewModelScope.launch {
            socketService.on("consultation:ready").collect { data ->
                val greeting = data.optJSONObject("greeting")
                greeting?.let {
                    val guidance = parseAIGuidance(it)
                    _uiState.update { state -> state.copy(currentGuidance = guidance) }
                }
            }
        }
    }

    fun startSession(consultationType: String, websocketUrl: String, token: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                // Create session via API
                val response = consultationApiService.startSession(
                    StartSessionRequest(
                        consultationType = consultationType,
                        deviceInfo = android.os.Build.MODEL
                    )
                )

                if (response.success && response.data != null) {
                    val session = response.data
                    _uiState.update { it.copy(session = session, isLoading = false) }

                    // Initialize WebRTC
                    webRTCService.initialize()

                    // Connect to WebSocket
                    socketService.connect(websocketUrl, token)

                    Log.d(TAG, "Session created: ${session.sessionId}")
                } else {
                    _uiState.update {
                        it.copy(isLoading = false, error = response.error ?: "Failed to create session")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start session", e)
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Unknown error")
                }
            }
        }
    }

    fun connectToRoom(localVideoSink: VideoSink, remoteVideoSink: VideoSink) {
        val session = _uiState.value.session ?: return

        // Start local media
        webRTCService.startLocalMedia(localVideoSink, remoteVideoSink)

        // Join room
        webRTCService.joinRoom(session.signalingRoomId, session.sessionId)
    }

    fun activateSession() {
        viewModelScope.launch {
            val session = _uiState.value.session ?: return@launch

            try {
                val response = consultationApiService.activateSession(session.sessionId)
                if (response.success && response.data != null) {
                    _uiState.update { it.copy(session = response.data) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to activate session", e)
            }
        }
    }

    fun captureFrame(bitmap: Bitmap, captureType: String = CaptureType.PERIODIC) {
        viewModelScope.launch {
            val session = _uiState.value.session ?: return@launch

            try {
                val base64 = bitmapToBase64(bitmap)
                socketService.sendFrame(session.sessionId, base64, captureType)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to capture frame", e)
            }
        }
    }

    fun captureTongue(bitmap: Bitmap) {
        captureFrame(bitmap, CaptureType.TONGUE)
    }

    fun captureFacial(bitmap: Bitmap) {
        captureFrame(bitmap, CaptureType.FACIAL)
    }

    fun sendVoice(audioData: ByteArray, duration: Long) {
        viewModelScope.launch {
            val session = _uiState.value.session ?: return@launch

            val base64 = Base64.encodeToString(audioData, Base64.NO_WRAP)
            socketService.sendVoice(session.sessionId, base64, duration)
        }
    }

    fun sendGesture(gestureType: String, confidence: Float) {
        viewModelScope.launch {
            val session = _uiState.value.session ?: return@launch
            socketService.sendGesture(session.sessionId, gestureType, confidence)
        }
    }

    fun requestGuidance(type: String) {
        viewModelScope.launch {
            val session = _uiState.value.session ?: return@launch
            socketService.requestGuidance(session.sessionId, type)
        }
    }

    fun toggleVideo() {
        webRTCService.toggleVideo()
    }

    fun toggleAudio() {
        webRTCService.toggleAudio()
    }

    fun switchCamera() {
        webRTCService.switchCamera()
    }

    fun endSession() {
        viewModelScope.launch {
            val session = _uiState.value.session ?: return@launch

            try {
                _uiState.update { it.copy(isEnding = true) }

                socketService.endConsultation(session.sessionId)
                val response = consultationApiService.endSession(session.sessionId)

                if (response.success) {
                    _uiState.update {
                        it.copy(session = response.data, isEnding = false)
                    }
                }

                webRTCService.leaveRoom()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to end session", e)
                _uiState.update { it.copy(isEnding = false, error = e.message) }
            }
        }
    }

    fun getAnalysisResult() {
        viewModelScope.launch {
            val session = _uiState.value.session ?: return@launch

            try {
                val response = consultationApiService.getAnalysisResult(session.sessionId)
                if (response.success && response.data != null) {
                    _uiState.update { it.copy(analysisResult = response.data) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get analysis result", e)
            }
        }
    }

    fun dismissGuidance() {
        _uiState.update { it.copy(currentGuidance = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    private fun parseAIGuidance(data: JSONObject): AIGuidance {
        val guidance = data.optJSONObject("guidance") ?: data
        return AIGuidance(
            type = guidance.optString("type", "instruction"),
            textContent = guidance.optString("textContent", ""),
            audioContent = guidance.optString("audioContent", null),
            expectedResponse = guidance.optString("expectedResponse", null),
            targetBodyPart = guidance.optString("targetBodyPart", null),
            timeout = guidance.optInt("timeout", 30)
        )
    }

    private fun parseAIDetection(data: JSONObject): AIDetection {
        return AIDetection(
            type = data.optString("type", "object"),
            detected = data.optString("detected", ""),
            confidence = data.optDouble("confidence", 0.0).toFloat(),
            timestamp = data.optLong("timestamp", System.currentTimeMillis())
        )
    }

    fun getEglBase() = webRTCService.getEglBase()

    override fun onCleared() {
        super.onCleared()
        webRTCService.release()
        socketService.disconnect()
    }
}

// UI State
data class VideoConsultationUiState(
    val isLoading: Boolean = false,
    val isEnding: Boolean = false,
    val error: String? = null,
    val session: SessionResponse? = null,
    val socketState: SocketService.ConnectionState = SocketService.ConnectionState.Disconnected,
    val webRTCState: WebRTCService.WebRTCState = WebRTCService.WebRTCState.Idle,
    val isVideoEnabled: Boolean = true,
    val isAudioEnabled: Boolean = true,
    val currentGuidance: AIGuidance? = null,
    val lastAIResponse: String? = null,
    val detections: List<AIDetection> = emptyList(),
    val analysisProgress: AnalysisProgress? = null,
    val analysisComplete: Boolean = false,
    val analysisResult: AnalysisResultResponse? = null
)

data class AIGuidance(
    val type: String,
    val textContent: String,
    val audioContent: String?,
    val expectedResponse: String?,
    val targetBodyPart: String?,
    val timeout: Int
)

data class AIDetection(
    val type: String,
    val detected: String,
    val confidence: Float,
    val timestamp: Long
)

data class AnalysisProgress(
    val step: String,
    val percentage: Int,
    val message: String
)

// Events
sealed class VideoConsultationEvent {
    data class AIGuidanceReceived(val guidance: AIGuidance) : VideoConsultationEvent()
    data class AIDetectionReceived(val detection: AIDetection) : VideoConsultationEvent()
    data class AIResponseReceived(val text: String, val audio: String?) : VideoConsultationEvent()
    data class AnalysisComplete(val resultId: String, val summary: String) : VideoConsultationEvent()
    data class Error(val message: String) : VideoConsultationEvent()
}
