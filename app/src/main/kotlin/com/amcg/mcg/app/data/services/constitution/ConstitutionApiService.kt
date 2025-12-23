package com.amcg.mcg.app.data.services.constitution

import com.amcg.mcg.app.data.services.consultation.ApiResponse
import com.amcg.mcg.app.data.services.consultation.PagedResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.*

interface ConstitutionApiService {

    // 체질 진단 시작
    @POST("v1/oriental/constitution/assess")
    suspend fun startAssessment(
        @Body request: ConstitutionAssessmentRequest
    ): ApiResponse<ConstitutionSessionResponse>

    // 설문 응답 저장
    @POST("v1/oriental/constitution/responses")
    suspend fun submitAnswers(
        @Body request: ConstitutionAnswerRequest
    ): ApiResponse<Unit>

    // 체질 진단 결과 계산
    @POST("v1/oriental/constitution/result/{sessionId}")
    suspend fun calculateResult(
        @Path("sessionId") sessionId: String
    ): ApiResponse<ConstitutionResultResponse>

    // 최신 체질 진단 결과 조회
    @GET("v1/oriental/constitution/result")
    suspend fun getLatestResult(): ApiResponse<ConstitutionResultResponse>

    // 특정 체질 진단 결과 조회
    @GET("v1/oriental/constitution/result/{resultId}")
    suspend fun getResultById(
        @Path("resultId") resultId: String
    ): ApiResponse<ConstitutionResultResponse>

    // 체질 진단 이력 조회
    @GET("v1/oriental/constitution/history")
    suspend fun getHistory(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 10
    ): ApiResponse<PagedResponse<ConstitutionHistoryResponse>>
}

// Request DTOs
@Serializable
data class ConstitutionAssessmentRequest(
    @SerialName("questionSet") val questionSet: String // sasang_basic, sasang_detailed, eight_constitution
)

@Serializable
data class ConstitutionAnswerRequest(
    @SerialName("sessionId") val sessionId: String,
    @SerialName("answers") val answers: List<QuestionAnswer>
)

@Serializable
data class QuestionAnswer(
    @SerialName("questionId") val questionId: String,
    @SerialName("selectedOptionId") val selectedOptionId: String,
    @SerialName("weight") val weight: Double? = null
)

// Response DTOs
@Serializable
data class ConstitutionSessionResponse(
    @SerialName("sessionId") val sessionId: String,
    @SerialName("questionSet") val questionSet: String,
    @SerialName("totalQuestions") val totalQuestions: Int,
    @SerialName("questions") val questions: List<ConstitutionQuestion>,
    @SerialName("createdAt") val createdAt: String
)

@Serializable
data class ConstitutionQuestion(
    @SerialName("id") val id: String,
    @SerialName("category") val category: String,
    @SerialName("questionText") val questionText: String,
    @SerialName("questionType") val questionType: String,
    @SerialName("options") val options: List<QuestionOption>,
    @SerialName("order") val order: Int
)

@Serializable
data class QuestionOption(
    @SerialName("id") val id: String,
    @SerialName("text") val text: String,
    @SerialName("weight") val weight: Double? = null,
    @SerialName("targetConstitution") val targetConstitution: String? = null
)

@Serializable
data class ConstitutionResultResponse(
    @SerialName("id") val id: String,
    @SerialName("sessionId") val sessionId: String,
    @SerialName("primaryConstitution") val primaryConstitution: ConstitutionType,
    @SerialName("secondaryConstitution") val secondaryConstitution: ConstitutionType? = null,
    @SerialName("scores") val scores: Map<String, Double>,
    @SerialName("confidence") val confidence: Double,
    @SerialName("characteristics") val characteristics: ConstitutionCharacteristics,
    @SerialName("healthRecommendations") val healthRecommendations: HealthRecommendations,
    @SerialName("assessedAt") val assessedAt: String
)

@Serializable
data class ConstitutionType(
    @SerialName("code") val code: String,
    @SerialName("koreanName") val koreanName: String,
    @SerialName("englishName") val englishName: String,
    @SerialName("description") val description: String
)

@Serializable
data class ConstitutionCharacteristics(
    @SerialName("physicalTraits") val physicalTraits: List<String>,
    @SerialName("personalityTraits") val personalityTraits: List<String>,
    @SerialName("strengths") val strengths: List<String>,
    @SerialName("weaknesses") val weaknesses: List<String>,
    @SerialName("commonSymptoms") val commonSymptoms: List<String>
)

@Serializable
data class HealthRecommendations(
    @SerialName("beneficialFoods") val beneficialFoods: List<String>,
    @SerialName("avoidFoods") val avoidFoods: List<String>,
    @SerialName("exerciseTypes") val exerciseTypes: List<String>,
    @SerialName("lifestyleAdvice") val lifestyleAdvice: List<String>,
    @SerialName("vulnerableOrgans") val vulnerableOrgans: List<String>,
    @SerialName("preventiveCare") val preventiveCare: List<String>
)

@Serializable
data class ConstitutionHistoryResponse(
    @SerialName("id") val id: String,
    @SerialName("questionSet") val questionSet: String,
    @SerialName("primaryConstitution") val primaryConstitution: String,
    @SerialName("confidence") val confidence: Double,
    @SerialName("assessedAt") val assessedAt: String,
    @SerialName("createdAt") val createdAt: String
)

// Question set types
object QuestionSet {
    const val SASANG_BASIC = "sasang_basic"
    const val SASANG_DETAILED = "sasang_detailed"
    const val EIGHT_CONSTITUTION = "eight_constitution"
}

// Constitution codes
object ConstitutionCode {
    // 사상체질
    const val TAEYANG = "taeyang"      // 태양인
    const val TAEEUM = "taeeum"        // 태음인
    const val SOYANG = "soyang"        // 소양인
    const val SOEUM = "soeum"          // 소음인

    // 8체질
    const val HEPATONIA = "hepatonia"              // 목양체질
    const val CHOLECYSTONIA = "cholecystonia"      // 목음체질
    const val PANCREOTONIA = "pancreotonia"        // 토양체질
    const val GASTROTONIA = "gastrotonia"          // 토음체질
    const val PULMOTONIA = "pulmotonia"            // 금양체질
    const val COLONOTONIA = "colonotonia"          // 금음체질
    const val RENOTONIA = "renotonia"              // 수양체질
    const val VESICOTONIA = "vesicotonia"          // 수음체질
}
