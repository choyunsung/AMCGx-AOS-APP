package com.amcg.mcg.app.data.services.consultation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.*

interface ConsultationApiService {

    @POST("v1/video-consultation/sessions")
    suspend fun startSession(
        @Body request: StartSessionRequest
    ): ApiResponse<SessionResponse>

    @POST("v1/video-consultation/sessions/{sessionId}/activate")
    suspend fun activateSession(
        @Path("sessionId") sessionId: String
    ): ApiResponse<SessionResponse>

    @POST("v1/video-consultation/sessions/{sessionId}/end")
    suspend fun endSession(
        @Path("sessionId") sessionId: String
    ): ApiResponse<SessionResponse>

    @GET("v1/video-consultation/sessions/{sessionId}")
    suspend fun getSession(
        @Path("sessionId") sessionId: String
    ): ApiResponse<SessionResponse>

    @GET("v1/video-consultation/sessions/active")
    suspend fun getActiveSession(): ApiResponse<SessionResponse>

    @DELETE("v1/video-consultation/sessions/{sessionId}")
    suspend fun cancelSession(
        @Path("sessionId") sessionId: String
    ): ApiResponse<Unit>

    @GET("v1/video-consultation/sessions/{sessionId}/analysis")
    suspend fun getAnalysisResult(
        @Path("sessionId") sessionId: String
    ): ApiResponse<AnalysisResultResponse>

    @GET("v1/video-consultation/history")
    suspend fun getSessionHistory(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 10
    ): ApiResponse<PagedResponse<SessionHistoryResponse>>

    @Multipart
    @POST("v1/video-consultation/sessions/{sessionId}/frames")
    suspend fun uploadFrame(
        @Path("sessionId") sessionId: String,
        @Part file: okhttp3.MultipartBody.Part,
        @Part("captureType") captureType: String
    ): ApiResponse<FrameUploadResponse>
}

// Request/Response DTOs
@Serializable
data class StartSessionRequest(
    @SerialName("consultationType") val consultationType: String,
    @SerialName("linkedConstitutionId") val linkedConstitutionId: String? = null,
    @SerialName("deviceInfo") val deviceInfo: String? = null
)

@Serializable
data class ApiResponse<T>(
    @SerialName("success") val success: Boolean,
    @SerialName("data") val data: T? = null,
    @SerialName("message") val message: String? = null,
    @SerialName("error") val error: String? = null
)

@Serializable
data class SessionResponse(
    @SerialName("sessionId") val sessionId: String,
    @SerialName("userId") val userId: String,
    @SerialName("consultationType") val consultationType: String,
    @SerialName("status") val status: String,
    @SerialName("signalingRoomId") val signalingRoomId: String,
    @SerialName("websocketUrl") val websocketUrl: String,
    @SerialName("iceServers") val iceServers: List<IceServerConfig>? = null,
    @SerialName("startedAt") val startedAt: String? = null,
    @SerialName("endedAt") val endedAt: String? = null,
    @SerialName("durationSeconds") val durationSeconds: Int? = null,
    @SerialName("totalFramesCaptured") val totalFramesCaptured: Int? = null
)

@Serializable
data class IceServerConfig(
    @SerialName("urls") val urls: List<String>,
    @SerialName("username") val username: String? = null,
    @SerialName("credential") val credential: String? = null
)

@Serializable
data class AnalysisResultResponse(
    @SerialName("id") val id: String,
    @SerialName("sessionId") val sessionId: String,
    @SerialName("analyzedAt") val analyzedAt: String,
    @SerialName("riskLevel") val riskLevel: String,
    @SerialName("urgencyLevel") val urgencyLevel: String,
    @SerialName("followUpRequired") val followUpRequired: Boolean,
    @SerialName("visionSummary") val visionSummary: String? = null,
    @SerialName("tongueDiagnosis") val tongueDiagnosis: TongueDiagnosisResult? = null,
    @SerialName("voiceSummary") val voiceSummary: String? = null,
    @SerialName("gestureSummary") val gestureSummary: String? = null,
    @SerialName("orientalDiagnosis") val orientalDiagnosis: OrientalDiagnosisResult? = null,
    @SerialName("recommendations") val recommendations: List<String>? = null
)

@Serializable
data class TongueDiagnosisResult(
    @SerialName("tongueColor") val tongueColor: String,
    @SerialName("coating") val coating: String,
    @SerialName("moisture") val moisture: String,
    @SerialName("shape") val shape: String,
    @SerialName("interpretation") val interpretation: String? = null
)

@Serializable
data class OrientalDiagnosisResult(
    @SerialName("qi") val qi: String,
    @SerialName("blood") val blood: String,
    @SerialName("yin") val yin: String,
    @SerialName("yang") val yang: String,
    @SerialName("constitutionType") val constitutionType: String? = null,
    @SerialName("recommendations") val recommendations: List<String>? = null
)

@Serializable
data class SessionHistoryResponse(
    @SerialName("id") val id: String,
    @SerialName("consultationType") val consultationType: String,
    @SerialName("status") val status: String,
    @SerialName("startedAt") val startedAt: String? = null,
    @SerialName("endedAt") val endedAt: String? = null,
    @SerialName("durationSeconds") val durationSeconds: Int? = null,
    @SerialName("totalFramesCaptured") val totalFramesCaptured: Int? = null,
    @SerialName("analysisResultId") val analysisResultId: String? = null,
    @SerialName("createdAt") val createdAt: String
)

@Serializable
data class PagedResponse<T>(
    @SerialName("content") val content: List<T>,
    @SerialName("totalPages") val totalPages: Int,
    @SerialName("totalElements") val totalElements: Long,
    @SerialName("size") val size: Int,
    @SerialName("number") val number: Int
)

@Serializable
data class FrameUploadResponse(
    @SerialName("frameId") val frameId: String
)

// Consultation types
object ConsultationType {
    const val GENERAL = "general"
    const val TONGUE = "tongue"
    const val FACIAL = "facial"
    const val COMPREHENSIVE = "comprehensive"
}

// Capture types
object CaptureType {
    const val PERIODIC = "periodic"
    const val ON_DEMAND = "on_demand"
    const val GESTURE_TRIGGER = "gesture_trigger"
    const val TONGUE = "tongue"
    const val FACIAL = "facial"
}

// Session status
object SessionStatus {
    const val PENDING = "pending"
    const val ACTIVE = "active"
    const val ANALYZING = "analyzing"
    const val COMPLETED = "completed"
    const val FAILED = "failed"
    const val CANCELLED = "cancelled"
}
