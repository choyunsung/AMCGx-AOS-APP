package com.amcg.mcg.app.data.models

import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.util.UUID

// MARK: - Heart Rate Data
@Serializable
data class HeartRateData(
    val id: String = UUID.randomUUID().toString(),
    val bpm: Double,
    @Serializable(with = LocalDateTimeSerializer::class)
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val context: HeartRateContext = HeartRateContext.RESTING
) {
    enum class HeartRateContext(val displayName: String, val color: String) {
        RESTING("안정시", "blue"),
        ACTIVE("활동중", "green"),
        EXERCISE("운동중", "orange"),
        SLEEPING("수면중", "purple")
    }

    val status: HeartRateStatus
        get() = when {
            bpm < 60 -> HeartRateStatus.LOW
            bpm <= 100 -> HeartRateStatus.NORMAL
            bpm <= 120 -> HeartRateStatus.ELEVATED
            else -> HeartRateStatus.HIGH
        }

    enum class HeartRateStatus(val displayName: String, val color: String) {
        LOW("낮음", "blue"),
        NORMAL("정상", "green"),
        ELEVATED("약간 높음", "orange"),
        HIGH("높음", "red")
    }
}

// MARK: - Blood Pressure Data
@Serializable
data class BloodPressureData(
    val id: String = UUID.randomUUID().toString(),
    val systolic: Double,      // 수축기 (최고혈압)
    val diastolic: Double,     // 이완기 (최저혈압)
    @Serializable(with = LocalDateTimeSerializer::class)
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val source: DataSource = DataSource.MANUAL
) {
    enum class DataSource(val displayName: String) {
        WEAR_OS("Wear OS"),
        MANUAL("수동 입력"),
        DEVICE("측정 기기")
    }

    val status: BloodPressureStatus
        get() = when {
            systolic < 90 || diastolic < 60 -> BloodPressureStatus.LOW
            systolic < 120 && diastolic < 80 -> BloodPressureStatus.NORMAL
            systolic < 130 && diastolic < 80 -> BloodPressureStatus.ELEVATED
            systolic < 140 || diastolic < 90 -> BloodPressureStatus.STAGE1
            else -> BloodPressureStatus.STAGE2
        }

    enum class BloodPressureStatus(val displayName: String, val color: String) {
        LOW("저혈압", "blue"),
        NORMAL("정상", "green"),
        ELEVATED("주의", "yellow"),
        STAGE1("고혈압 1단계", "orange"),
        STAGE2("고혈압 2단계", "red")
    }
}

// MARK: - Stress Level Data
@Serializable
data class StressLevelData(
    val id: String = UUID.randomUUID().toString(),
    val level: Double,         // 0-100
    @Serializable(with = LocalDateTimeSerializer::class)
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val derivedFrom: List<StressIndicator> = emptyList()
) {
    enum class StressIndicator(val displayName: String) {
        HEART_RATE("심박수"),
        HRV("심박변이도"),
        ACTIVITY("활동량"),
        SLEEP("수면"),
        RESPIRATORY("호흡")
    }

    val status: StressStatus
        get() = when {
            level < 30 -> StressStatus.LOW
            level < 50 -> StressStatus.NORMAL
            level < 70 -> StressStatus.MODERATE
            else -> StressStatus.HIGH
        }

    enum class StressStatus(val displayName: String, val color: String, val emoji: String) {
        LOW("낮음", "green", "😌"),
        NORMAL("정상", "blue", "🙂"),
        MODERATE("보통", "orange", "😟"),
        HIGH("높음", "red", "😰")
    }
}

// MARK: - Heart Rate Variability
@Serializable
data class HRVData(
    val id: String = UUID.randomUUID().toString(),
    val value: Double,         // milliseconds
    @Serializable(with = LocalDateTimeSerializer::class)
    val timestamp: LocalDateTime = LocalDateTime.now()
) {
    val status: HRVStatus
        get() = when {
            value < 20 -> HRVStatus.LOW
            value < 50 -> HRVStatus.BELOW_AVERAGE
            value < 100 -> HRVStatus.AVERAGE
            else -> HRVStatus.ABOVE_AVERAGE
        }

    enum class HRVStatus(val displayName: String, val color: String) {
        LOW("낮음", "red"),
        BELOW_AVERAGE("평균 이하", "orange"),
        AVERAGE("평균", "blue"),
        ABOVE_AVERAGE("평균 이상", "green")
    }
}

// MARK: - Health Summary
@Serializable
data class HealthSummary(
    val currentHeartRate: HeartRateData? = null,
    val latestBloodPressure: BloodPressureData? = null,
    val currentStressLevel: StressLevelData? = null,
    val latestHRV: HRVData? = null,
    val heartRateTrend: List<HeartRateData> = emptyList(),
    val bloodPressureTrend: List<BloodPressureData> = emptyList(),
    val stressTrend: List<StressLevelData> = emptyList(),
    @Serializable(with = LocalDateTimeSerializer::class)
    val lastUpdated: LocalDateTime = LocalDateTime.now()
) {
    companion object {
        fun createMock(): HealthSummary {
            val now = LocalDateTime.now()

            // Generate heart rate trend (last 24 hours)
            val heartRateTrend = (0..23).map { i ->
                val timestamp = now.minusHours(i.toLong())
                val hour = timestamp.hour

                val (baseRate, context) = when (hour) {
                    in 22..23, in 0..6 -> (58.0 + (-5..5).random(), HeartRateData.HeartRateContext.SLEEPING)
                    in 7..9 -> (85.0 + (-5..10).random(), HeartRateData.HeartRateContext.ACTIVE)
                    in 18..19 -> (95.0 + (-5..15).random(), HeartRateData.HeartRateContext.EXERCISE)
                    else -> (72.0 + (-8..8).random(), HeartRateData.HeartRateContext.RESTING)
                }

                HeartRateData(
                    bpm = baseRate,
                    timestamp = timestamp,
                    context = context
                )
            }.reversed()

            // Generate blood pressure trend (last 7 days)
            val bpTrend = (0..6).map { i ->
                val timestamp = now.minusDays(i.toLong())
                BloodPressureData(
                    systolic = 118.0 + (-8..8).random(),
                    diastolic = 76.0 + (-6..6).random(),
                    timestamp = timestamp,
                    source = BloodPressureData.DataSource.DEVICE
                )
            }.reversed()

            // Generate stress trend (last 12 hours)
            val stressTrend = (0..11).map { i ->
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
                        StressLevelData.StressIndicator.HRV,
                        StressLevelData.StressIndicator.ACTIVITY
                    )
                )
            }.reversed()

            return HealthSummary(
                currentHeartRate = heartRateTrend.lastOrNull(),
                latestBloodPressure = bpTrend.lastOrNull(),
                currentStressLevel = stressTrend.lastOrNull(),
                latestHRV = HRVData(value = 62.0, timestamp = now),
                heartRateTrend = heartRateTrend,
                bloodPressureTrend = bpTrend,
                stressTrend = stressTrend,
                lastUpdated = now
            )
        }
    }
}

// MARK: - LocalDateTime Serializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object LocalDateTimeSerializer : KSerializer<LocalDateTime> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDateTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDateTime) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): LocalDateTime {
        return LocalDateTime.parse(decoder.decodeString())
    }
}
