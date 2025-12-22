package com.amcg.mcg.app.data.services

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.amcg.mcg.app.data.models.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val healthConnectClient by lazy {
        HealthConnectClient.getOrCreate(context)
    }

    // Required permissions
    val permissions = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(BloodPressureRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class)
    )

    // Check if Health Connect is available
    suspend fun isAvailable(): Boolean {
        return try {
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        } catch (e: Exception) {
            false
        }
    }

    // Check if permissions are granted
    suspend fun hasAllPermissions(): Boolean {
        return try {
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            permissions.all { it in granted }
        } catch (e: Exception) {
            false
        }
    }

    // MARK: - Fetch Heart Rate Data
    suspend fun fetchHeartRateData(
        startTime: Instant = Instant.now().minusSeconds(7 * 24 * 60 * 60),
        endTime: Instant = Instant.now()
    ): List<HeartRateData> {
        return try {
            val request = ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )

            val response = healthConnectClient.readRecords(request)

            response.records.mapNotNull { record ->
                val sample = record.samples.firstOrNull() ?: return@mapNotNull null
                HeartRateData(
                    bpm = sample.beatsPerMinute.toDouble(),
                    timestamp = LocalDateTime.ofInstant(sample.time, ZoneId.systemDefault()),
                    context = HeartRateData.HeartRateContext.RESTING
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // MARK: - Fetch Blood Pressure Data
    suspend fun fetchBloodPressureData(
        startTime: Instant = Instant.now().minusSeconds(7 * 24 * 60 * 60),
        endTime: Instant = Instant.now()
    ): List<BloodPressureData> {
        return try {
            val request = ReadRecordsRequest(
                recordType = BloodPressureRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )

            val response = healthConnectClient.readRecords(request)

            response.records.map { record ->
                BloodPressureData(
                    systolic = record.systolic.inMillimetersOfMercury,
                    diastolic = record.diastolic.inMillimetersOfMercury,
                    timestamp = LocalDateTime.ofInstant(record.time, ZoneId.systemDefault()),
                    source = BloodPressureData.DataSource.DEVICE
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // MARK: - Fetch HRV Data
    suspend fun fetchHRVData(
        startTime: Instant = Instant.now().minusSeconds(24 * 60 * 60),
        endTime: Instant = Instant.now()
    ): List<HRVData> {
        return try {
            val request = ReadRecordsRequest(
                recordType = HeartRateVariabilityRmssdRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )

            val response = healthConnectClient.readRecords(request)

            response.records.map { record ->
                HRVData(
                    value = record.heartRateVariabilityMillis,
                    timestamp = LocalDateTime.ofInstant(record.time, ZoneId.systemDefault())
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // MARK: - Fetch Current Heart Rate
    suspend fun fetchCurrentHeartRate(): HeartRateData? {
        val recentData = fetchHeartRateData(
            startTime = Instant.now().minusSeconds(3600),
            endTime = Instant.now()
        )
        return recentData.lastOrNull()
    }

    // MARK: - Fetch Latest Blood Pressure
    suspend fun fetchLatestBloodPressure(): BloodPressureData? {
        val recentData = fetchBloodPressureData(
            startTime = Instant.now().minusSeconds(7 * 24 * 60 * 60),
            endTime = Instant.now()
        )
        return recentData.lastOrNull()
    }

    // MARK: - Fetch Latest HRV
    suspend fun fetchLatestHRV(): HRVData? {
        val recentData = fetchHRVData(
            startTime = Instant.now().minusSeconds(24 * 60 * 60),
            endTime = Instant.now()
        )
        return recentData.lastOrNull()
    }

    // MARK: - Calculate Stress Level (Mock)
    suspend fun calculateStressLevel(): List<StressLevelData> {
        // TODO: Implement stress calculation based on HRV, heart rate, and activity
        // For now, return mock data
        val now = LocalDateTime.now()
        return (0..11).map { i ->
            val timestamp = now.minusHours(i.toLong())
            val hour = timestamp.hour

            val stressLevel = when (hour) {
                in 9..11 -> 65.0 + (-10..15).random()  // 오전 업무
                in 14..17 -> 55.0 + (-10..20).random()  // 오후 업무
                else -> 35.0 + (-10..10).random()       // 휴식
            }.coerceIn(0.0, 100.0)

            StressLevelData(
                level = stressLevel,
                timestamp = timestamp,
                derivedFrom = listOf(
                    StressLevelData.StressIndicator.HEART_RATE,
                    StressLevelData.StressIndicator.HRV
                )
            )
        }.reversed()
    }

    // MARK: - Fetch All Health Data
    suspend fun fetchAllHealthData(): HealthSummary {
        val currentHeartRate = fetchCurrentHeartRate()
        val latestBloodPressure = fetchLatestBloodPressure()
        val latestHRV = fetchLatestHRV()
        val heartRateTrend = fetchHeartRateData()
        val bloodPressureTrend = fetchBloodPressureData()
        val stressTrend = calculateStressLevel()

        return HealthSummary(
            currentHeartRate = currentHeartRate,
            latestBloodPressure = latestBloodPressure,
            currentStressLevel = stressTrend.lastOrNull(),
            latestHRV = latestHRV,
            heartRateTrend = heartRateTrend,
            bloodPressureTrend = bloodPressureTrend,
            stressTrend = stressTrend,
            lastUpdated = LocalDateTime.now()
        )
    }

    // MARK: - Observe Health Data (Flow)
    fun observeHeartRateData(): Flow<HeartRateData?> = flow {
        while (true) {
            emit(fetchCurrentHeartRate())
            kotlinx.coroutines.delay(10000) // Update every 10 seconds
        }
    }
}
