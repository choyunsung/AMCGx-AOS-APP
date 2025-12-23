package com.amcg.mcg.app.data.services.auth

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val authService: AuthService
) : Interceptor {

    companion object {
        private val NO_AUTH_PATHS = listOf(
            "/v1/auth/login",
            "/v1/auth/signup",
            "/v1/auth/register",
            "/v1/auth/refresh"
        )
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val path = originalRequest.url.encodedPath

        // 인증이 필요없는 경로는 그대로 진행
        if (NO_AUTH_PATHS.any { path.endsWith(it) }) {
            return chain.proceed(originalRequest)
        }

        val token = authService.accessToken
        if (token.isNullOrEmpty()) {
            return chain.proceed(originalRequest)
        }

        // Bearer 토큰 추가
        val authenticatedRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()

        var response = chain.proceed(authenticatedRequest)

        // 401 응답시 토큰 갱신 시도
        if (response.code == 401) {
            response.close()

            val refreshResult = runBlocking {
                authService.refreshAccessToken()
            }

            if (refreshResult.isSuccess) {
                val newToken = authService.accessToken
                if (!newToken.isNullOrEmpty()) {
                    val retryRequest = originalRequest.newBuilder()
                        .header("Authorization", "Bearer $newToken")
                        .build()
                    response = chain.proceed(retryRequest)
                }
            }
        }

        return response
    }
}
