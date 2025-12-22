# AMCGx Android 구현 가이드

## 프로젝트 개요

iOS AMCGx 앱을 Android로 포팅한 프로젝트입니다.

## 현재 구현 상태

### ✅ 완료된 작업

#### 1. 프로젝트 기본 구조
- **패키지 구조**: Clean Architecture 기반 모듈 분리
- **빌드 시스템**: Gradle Kotlin DSL
- **의존성 관리**: Version Catalog 패턴

#### 2. UI 레이어
- **테마 시스템**: Material Design 3 기반
  - `Color.kt`: iOS 앱과 동일한 컬러 팔레트
  - `Theme.kt`: 라이트/다크 모드 지원
  - `Type.kt`: 타이포그래피 시스템
- **대시보드 화면**: `DashboardScreen.kt`
  - 탭 네비게이션 (6개 탭)
  - 건강 데이터 카드 UI
  - 차트 플레이스홀더

#### 3. 데이터 레이어
- **모델 클래스**: `HealthData.kt`
  - `HeartRateData`: 심박수 데이터
  - `BloodPressureData`: 혈압 데이터
  - `StressLevelData`: 스트레스 레벨
  - `HRVData`: 심박변이도
  - `HealthSummary`: 종합 건강 데이터
- **서비스**: `HealthConnectManager.kt`
  - Health Connect API 통합
  - 데이터 읽기/쓰기 기능

#### 4. DI 설정
- **Hilt 모듈**: `AppModule.kt`
  - HealthConnectManager 싱글톤 제공

#### 5. 설정 파일
- `AndroidManifest.xml`: 권한 및 액티비티 설정
- `gradle.properties`: 빌드 최적화
- `proguard-rules.pro`: ProGuard 규칙
- `.gitignore`: 버전 관리 제외 파일

## iOS와 Android 대응표

| iOS 컴포넌트 | Android 대응 | 상태 |
|-------------|-------------|------|
| `HealthKit` | `Health Connect` | ✅ 완료 |
| `SwiftUI` | `Jetpack Compose` | ✅ 완료 |
| `@StateObject` | `ViewModel` | ⚠️ 부분 완료 |
| `Apple Watch` | `Wear OS` | 📋 예정 |
| `Apple Intelligence` | `ML Kit + TensorFlow` | 📋 예정 |
| `Face ID` | `BiometricPrompt` | 📋 예정 |

## 다음 단계 구현 계획

### Phase 1: ViewModel 및 Repository 레이어 (우선순위: 높음)

```kotlin
// 1. Repository 인터페이스 정의
interface HealthRepository {
    suspend fun fetchHealthSummary(): Result<HealthSummary>
    fun observeHeartRate(): Flow<HeartRateData>
}

// 2. ViewModel 구현
class DashboardViewModel(
    private val healthRepository: HealthRepository
) : ViewModel() {
    val healthSummary: StateFlow<HealthSummary>
    // ...
}
```

**위치**:
- `app/src/main/kotlin/com/amcg/mcg/app/domain/repositories/HealthRepository.kt`
- `app/src/main/kotlin/com/amcg/mcg/app/ui/viewmodels/DashboardViewModel.kt`

### Phase 2: 권한 처리 (우선순위: 높음)

```kotlin
// 권한 요청 컴포저블
@Composable
fun HealthPermissionScreen(
    onPermissionGranted: () -> Unit
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permission result
    }
}
```

**위치**: `app/src/main/kotlin/com/amcg/mcg/app/ui/screens/PermissionScreen.kt`

### Phase 3: 차트 구현 (우선순위: 중간)

YCharts 라이브러리를 사용하여 실제 차트 구현:

```kotlin
@Composable
fun HeartRateChart(data: List<HeartRateData>) {
    LineChart(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        lineChartData = LineChartData(
            linePlotData = LinePlotData(
                lines = listOf(
                    Line(
                        dataPoints = data.map {
                            DataPoint(x = it.timestamp, y = it.bpm)
                        }
                    )
                )
            )
        )
    )
}
```

### Phase 4: AI/ML 기능 (우선순위: 중간)

1. **얼굴 감지**
```kotlin
class FaceDetectionService {
    private val detector = FaceDetection.getClient()

    suspend fun analyzeFace(image: InputImage): List<Face> {
        // ML Kit Face Detection
    }
}
```

2. **텍스트 분석**
```kotlin
class NaturalLanguageService {
    suspend fun analyzeText(text: String): TextAnalysisResult {
        // ML Kit Natural Language API
    }
}
```

**위치**: `app/src/main/kotlin/com/amcg/mcg/app/data/services/ml/`

### Phase 5: Wear OS 연동 (우선순위: 낮음)

별도의 Wear OS 모듈 생성:
```
wear/
├── build.gradle.kts
└── src/main/kotlin/com/amcg/mcg/wear/
    ├── WearMainActivity.kt
    └── services/
        └── DataSyncService.kt
```

## 빌드 및 실행 가이드

### 1. 프로젝트 열기
```bash
cd /Users/yunsung/workspace/apple-app/AMCGx-Android
```

Android Studio에서 프로젝트 열기

### 2. Health Connect 설치
- Google Play에서 Health Connect 앱 설치 필요
- 또는 에뮬레이터에서 테스트용 APK 설치

### 3. 빌드
```bash
./gradlew build
```

### 4. 실행
```bash
./gradlew installDebug
```

## 트러블슈팅

### Health Connect 권한 오류
**문제**: Health Connect 권한을 요청했지만 데이터를 읽을 수 없음

**해결방법**:
1. Settings > Apps > Health Connect 확인
2. 앱 권한이 부여되었는지 확인
3. Health Connect 앱이 설치되어 있는지 확인

### 컴파일 오류
**문제**: `Cannot access 'kotlinx.serialization.json.Json'`

**해결방법**:
```kotlin
// build.gradle.kts에 추가
plugins {
    kotlin("plugin.serialization") version "1.9.20"
}
```

### UI 미표시
**문제**: 화면이 비어있거나 데이터가 표시되지 않음

**해결방법**:
1. ViewModel이 제대로 주입되었는지 확인
2. 권한이 부여되었는지 확인
3. Mock 데이터로 먼저 테스트

## 코드 스타일 가이드

### 1. 파일 구조
```kotlin
// 1. Package declaration
package com.amcg.mcg.app.ui.screens

// 2. Imports (alphabetically)
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*

// 3. Constants
private const val TAG = "DashboardScreen"

// 4. Composables
@Composable
fun DashboardScreen() { }

// 5. Helper functions
private fun calculateScore() { }
```

### 2. Naming Conventions
- **Composables**: PascalCase (예: `DashboardScreen`)
- **Functions**: camelCase (예: `fetchHealthData`)
- **Constants**: UPPER_SNAKE_CASE (예: `MAX_HEART_RATE`)
- **Files**: PascalCase (예: `DashboardScreen.kt`)

### 3. Composable 구조
```kotlin
@Composable
fun MyComponent(
    // 1. Required parameters
    title: String,
    // 2. Optional parameters with defaults
    subtitle: String = "",
    // 3. Modifier (always last or second-to-last)
    modifier: Modifier = Modifier,
    // 4. Lambda parameters (always last)
    onClick: () -> Unit = {}
) {
    // Implementation
}
```

## 테스트 전략

### 1. 단위 테스트
```kotlin
@Test
fun `heart rate status should be normal when bpm is 75`() {
    val heartRate = HeartRateData(bpm = 75.0)
    assertEquals(HeartRateStatus.NORMAL, heartRate.status)
}
```

### 2. UI 테스트
```kotlin
@Test
fun dashboard_displays_health_data() {
    composeTestRule.setContent {
        DashboardScreen()
    }

    composeTestRule
        .onNodeWithText("심박수")
        .assertIsDisplayed()
}
```

## 참고 자료

### 공식 문서
- [Health Connect API](https://developer.android.com/guide/health-and-fitness/health-connect)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [ML Kit](https://developers.google.com/ml-kit)

### 라이브러리 문서
- [YCharts](https://github.com/yml-org/YCharts)
- [Hilt](https://dagger.dev/hilt/)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)

## 기여 가이드라인

1. 새로운 기능 추가 시 해당 가이드를 업데이트
2. 코드 스타일 가이드 준수
3. 단위 테스트 작성
4. README.md 업데이트
