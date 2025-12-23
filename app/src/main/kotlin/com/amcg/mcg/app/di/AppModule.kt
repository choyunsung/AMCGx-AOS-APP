package com.amcg.mcg.app.di

import android.content.Context
import com.amcg.mcg.app.data.services.HealthConnectManager
import com.amcg.mcg.app.data.services.consultation.ConsultationApiService
import com.amcg.mcg.app.data.services.consultation.SocketService
import com.amcg.mcg.app.data.services.consultation.WebRTCService
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideHealthConnectManager(
        @ApplicationContext context: Context
    ): HealthConnectManager {
        return HealthConnectManager(context)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }

    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.compass.health/") // TODO: Configure from BuildConfig
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideConsultationApiService(retrofit: Retrofit): ConsultationApiService {
        return retrofit.create(ConsultationApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideSocketService(): SocketService {
        return SocketService()
    }

    @Provides
    @Singleton
    fun provideWebRTCService(
        @ApplicationContext context: Context,
        socketService: SocketService
    ): WebRTCService {
        return WebRTCService(context, socketService)
    }
}
