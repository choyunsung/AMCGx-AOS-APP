package com.amcg.mcg.app.data.services.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthService @Inject constructor(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "auth_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_CURRENT_USER = "current_user"
        private const val BASE_URL = "https://api.compass.health"
    }

    init {
        loadStoredAuth()
    }

    // MARK: - Token Management

    var accessToken: String?
        get() = securePrefs.getString(KEY_ACCESS_TOKEN, null)
        private set(value) {
            securePrefs.edit().apply {
                if (value != null) {
                    putString(KEY_ACCESS_TOKEN, value)
                } else {
                    remove(KEY_ACCESS_TOKEN)
                }
            }.apply()
        }

    var refreshToken: String?
        get() = securePrefs.getString(KEY_REFRESH_TOKEN, null)
        private set(value) {
            securePrefs.edit().apply {
                if (value != null) {
                    putString(KEY_REFRESH_TOKEN, value)
                } else {
                    remove(KEY_REFRESH_TOKEN)
                }
            }.apply()
        }

    val websocketUrl: String
        get() = BASE_URL
            .replace("https://", "wss://")
            .replace("http://", "ws://") + "/ws"

    // MARK: - Authentication

    suspend fun login(email: String, password: String): Result<User> {
        return try {
            val requestBody = json.encodeToString(
                mapOf("email" to email, "password" to password)
            ).toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$BASE_URL/v1/auth/login")
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string() ?: throw Exception("Empty response")
                val authResponse = json.decodeFromString<AuthResponse>(body)
                handleAuthResponse(authResponse)
                Result.success(authResponse.user!!)
            } else {
                Result.failure(AuthException.LoginFailed)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(email: String, password: String, name: String): Result<User> {
        return try {
            val requestBody = json.encodeToString(
                mapOf("email" to email, "password" to password, "name" to name)
            ).toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$BASE_URL/v1/auth/register")
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string() ?: throw Exception("Empty response")
                val authResponse = json.decodeFromString<AuthResponse>(body)
                handleAuthResponse(authResponse)
                Result.success(authResponse.user!!)
            } else {
                Result.failure(AuthException.RegistrationFailed)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshAccessToken(): Result<Unit> {
        val currentRefreshToken = refreshToken ?: return Result.failure(AuthException.NoRefreshToken)

        return try {
            val requestBody = json.encodeToString(
                mapOf("refreshToken" to currentRefreshToken)
            ).toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$BASE_URL/v1/auth/refresh")
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string() ?: throw Exception("Empty response")
                val authResponse = json.decodeFromString<AuthResponse>(body)
                handleAuthResponse(authResponse)
                Result.success(Unit)
            } else {
                logout()
                Result.failure(AuthException.TokenRefreshFailed)
            }
        } catch (e: Exception) {
            logout()
            Result.failure(e)
        }
    }

    fun logout() {
        accessToken = null
        refreshToken = null
        _currentUser.value = null
        _isAuthenticated.value = false
        securePrefs.edit().remove(KEY_CURRENT_USER).apply()
    }

    // MARK: - Private Methods

    private fun handleAuthResponse(response: AuthResponse) {
        accessToken = response.accessToken
        refreshToken = response.refreshToken
        _currentUser.value = response.user
        _isAuthenticated.value = true

        response.user?.let { user ->
            securePrefs.edit()
                .putString(KEY_CURRENT_USER, json.encodeToString(user))
                .apply()
        }
    }

    private fun loadStoredAuth() {
        val token = accessToken
        if (!token.isNullOrEmpty()) {
            _isAuthenticated.value = true

            securePrefs.getString(KEY_CURRENT_USER, null)?.let { userJson ->
                try {
                    _currentUser.value = json.decodeFromString<User>(userJson)
                } catch (e: Exception) {
                    // Ignore parsing errors
                }
            }
        }
    }
}

// MARK: - Models

@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: User?
)

@Serializable
data class User(
    val id: String,
    val email: String,
    val name: String,
    val profileImageUrl: String? = null,
    val createdAt: String? = null
)

// MARK: - Exceptions

sealed class AuthException : Exception() {
    object LoginFailed : AuthException() {
        private fun readResolve(): Any = LoginFailed
        override val message: String = "로그인에 실패했습니다."
    }
    object RegistrationFailed : AuthException() {
        private fun readResolve(): Any = RegistrationFailed
        override val message: String = "회원가입에 실패했습니다."
    }
    object NoRefreshToken : AuthException() {
        private fun readResolve(): Any = NoRefreshToken
        override val message: String = "인증 정보가 없습니다. 다시 로그인해주세요."
    }
    object TokenRefreshFailed : AuthException() {
        private fun readResolve(): Any = TokenRefreshFailed
        override val message: String = "인증이 만료되었습니다. 다시 로그인해주세요."
    }
}
