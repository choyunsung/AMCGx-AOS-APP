package com.amcg.mcg.app.data.services.user

import com.amcg.mcg.app.data.services.consultation.ApiResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.*

interface UserApiService {

    // 내 정보 조회
    @GET("v1/users/me")
    suspend fun getMyProfile(): ApiResponse<UserResponse>

    // 내 정보 수정
    @PUT("v1/users/me")
    suspend fun updateMyProfile(
        @Body request: UserUpdateRequest
    ): ApiResponse<UserResponse>

    // 회원 탈퇴
    @DELETE("v1/users/me")
    suspend fun deleteAccount(): ApiResponse<Unit>

    // 비밀번호 변경
    @PUT("v1/users/me/password")
    suspend fun changePassword(
        @Body request: PasswordChangeRequest
    ): ApiResponse<Unit>

    // 건강 프로필 조회
    @GET("v1/users/me/health-profile")
    suspend fun getHealthProfile(): ApiResponse<UserHealthProfile>

    // 건강 프로필 수정
    @PUT("v1/users/me/health-profile")
    suspend fun updateHealthProfile(
        @Body request: UserHealthProfile
    ): ApiResponse<UserHealthProfile>

    // 설정 조회
    @GET("v1/users/me/settings")
    suspend fun getSettings(): ApiResponse<UserSettings>

    // 설정 수정
    @PUT("v1/users/me/settings")
    suspend fun updateSettings(
        @Body request: UserSettings
    ): ApiResponse<UserSettings>
}

// Response DTOs
@Serializable
data class UserResponse(
    @SerialName("id") val id: String,
    @SerialName("email") val email: String,
    @SerialName("name") val name: String,
    @SerialName("nickname") val nickname: String? = null,
    @SerialName("phone") val phone: String? = null,
    @SerialName("birthDate") val birthDate: String? = null,
    @SerialName("gender") val gender: String? = null,
    @SerialName("profileImageUrl") val profileImageUrl: String? = null,
    @SerialName("bio") val bio: String? = null,
    @SerialName("emailVerified") val emailVerified: Boolean = false,
    @SerialName("phoneVerified") val phoneVerified: Boolean = false,
    @SerialName("lastLoginAt") val lastLoginAt: String? = null,
    @SerialName("createdAt") val createdAt: String? = null
)

@Serializable
data class UserUpdateRequest(
    @SerialName("name") val name: String? = null,
    @SerialName("nickname") val nickname: String? = null,
    @SerialName("phone") val phone: String? = null,
    @SerialName("birthDate") val birthDate: String? = null,
    @SerialName("gender") val gender: String? = null,
    @SerialName("bio") val bio: String? = null
)

@Serializable
data class PasswordChangeRequest(
    @SerialName("currentPassword") val currentPassword: String,
    @SerialName("newPassword") val newPassword: String
)

@Serializable
data class UserHealthProfile(
    @SerialName("height") val height: Double? = null,
    @SerialName("weight") val weight: Double? = null,
    @SerialName("bloodType") val bloodType: String? = null,
    @SerialName("allergies") val allergies: List<String>? = null,
    @SerialName("chronicConditions") val chronicConditions: List<String>? = null,
    @SerialName("medications") val medications: List<String>? = null,
    @SerialName("familyHistory") val familyHistory: List<String>? = null,
    @SerialName("smokingStatus") val smokingStatus: String? = null,
    @SerialName("alcoholConsumption") val alcoholConsumption: String? = null,
    @SerialName("exerciseFrequency") val exerciseFrequency: String? = null,
    @SerialName("sleepHours") val sleepHours: Double? = null,
    @SerialName("stressLevel") val stressLevel: String? = null
)

@Serializable
data class UserSettings(
    @SerialName("notificationsEnabled") val notificationsEnabled: Boolean = true,
    @SerialName("observationReminders") val observationReminders: Boolean = true,
    @SerialName("healthSyncEnabled") val healthSyncEnabled: Boolean = true,
    @SerialName("language") val language: String = "ko",
    @SerialName("theme") val theme: String = "system",
    @SerialName("dataRetentionDays") val dataRetentionDays: Int = 365
)
