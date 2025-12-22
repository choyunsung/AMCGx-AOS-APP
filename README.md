# AMCGx - Android Health Monitoring App

Android 버전의 AMCGx 건강 모니터링 앱입니다. iOS 앱과 동일한 기능을 제공합니다.

## 프로젝트 개요

AMCGx는 건강 데이터를 종합적으로 모니터링하고 AI 기반 건강 상담을 제공하는 의료 애플리케이션입니다.

## 주요 기능

### 1. 건강 데이터 모니터링
- **Health Connect 통합**: Google Health Connect를 통한 건강 데이터 수집
- **실시간 모니터링**: 심박수, 혈압, HRV, 스트레스 레벨 실시간 추적
- **데이터 시각화**: 차트와 그래프를 통한 건강 트렌드 분석

### 2. Wear OS 연동
- **스마트워치 연동**: Wear OS 기기와 실시간 데이터 동기화
- **자동 데이터 수집**: 착용형 기기에서 자동으로 건강 데이터 수집

### 3. AI 건강 상담
- **ML Kit 통합**: Google ML Kit를 활용한 이미지 분석
- **자연어 처리**: 건강 질문에 대한 맞춤형 답변 제공
- **얼굴 분석**: 카메라를 통한 안색 및 표정 분석

### 4. 개인화 기능
- **건강 프로필**: 나이, 성별, 건강 상태 기반 맞춤 조언
- **알림 시스템**: 건강 이상 징후 감지 시 알림
- **생체 인증**: 지문/얼굴 인식을 통한 보안 강화

## 기술 스택

- **언어**: Kotlin
- **UI 프레임워크**: Jetpack Compose
- **아키텍처**: MVVM + Clean Architecture
- **DI**: Hilt
- **비동기 처리**: Coroutines & Flow
- **건강 데이터**: Health Connect API
- **AI/ML**: ML Kit, TensorFlow Lite
- **차트**: YCharts
- **카메라**: CameraX

## 시스템 요구사항

- **최소 SDK**: Android 8.0 (API 26)
- **목표 SDK**: Android 14 (API 34)
- **필수 하드웨어**: 카메라 (선택사항)
- **필수 소프트웨어**: Health Connect (Google Play)

## 프로젝트 구조

```
app/
├── data/
│   ├── models/          # 데이터 모델
│   ├── repositories/    # 리포지토리
│   └── services/        # 서비스 (Health Connect, ML Kit 등)
├── domain/
│   └── usecases/        # 비즈니스 로직
├── ui/
│   ├── screens/         # 화면 컴포저블
│   ├── components/      # 재사용 가능한 UI 컴포넌트
│   └── theme/           # 테마 및 스타일
└── di/                  # Dependency Injection 모듈
```

## 권한

### 필수 권한
- `android.permission.health.READ_HEART_RATE` - 심박수 데이터 읽기
- `android.permission.health.READ_BLOOD_PRESSURE` - 혈압 데이터 읽기
- `android.permission.health.READ_HEART_RATE_VARIABILITY` - HRV 데이터 읽기
- `android.permission.CAMERA` - 카메라 액세스
- `android.permission.POST_NOTIFICATIONS` - 알림 전송

### 선택 권한
- `android.permission.BLUETOOTH_CONNECT` - Wear OS 연동

## 빌드 방법

### 1. 프로젝트 복제
```bash
git clone <repository-url>
cd AMCGx-Android
```

### 2. Android Studio에서 열기
1. Android Studio 실행
2. "Open an Existing Project" 선택
3. AMCGx-Android 디렉토리 선택

### 3. 빌드 및 실행
```bash
./gradlew build
./gradlew installDebug
```

## 개발 계획

### Phase 1: 기본 기능 구현 ✅
- [x] 프로젝트 구조 설정
- [x] UI 테마 및 디자인 시스템
- [x] 데이터 모델 구현
- [x] 대시보드 화면 구현

### Phase 2: Health Connect 통합 (진행 중)
- [x] Health Connect Manager 구현
- [ ] 권한 요청 플로우
- [ ] 실시간 데이터 동기화

### Phase 3: AI 기능 통합 (예정)
- [ ] ML Kit Vision 통합
- [ ] 얼굴 분석 기능
- [ ] 자연어 처리

### Phase 4: Wear OS 연동 (예정)
- [ ] Wear OS 앱 모듈 생성
- [ ] 데이터 동기화 구현
- [ ] 실시간 모니터링

### Phase 5: 테스트 및 최적화 (예정)
- [ ] 단위 테스트 작성
- [ ] UI 테스트 작성
- [ ] 성능 최적화

## iOS 앱과의 차이점

| 기능 | iOS | Android |
|------|-----|---------|
| 건강 데이터 | HealthKit | Health Connect |
| 웨어러블 | Apple Watch | Wear OS |
| AI 서비스 | Apple Intelligence | ML Kit + TensorFlow |
| 생체 인증 | Face ID | BiometricPrompt |
| UI 프레임워크 | SwiftUI | Jetpack Compose |

## 라이센스

[라이센스 정보 추가 예정]

## 기여

[기여 가이드라인 추가 예정]

## 연락처

[연락처 정보 추가 예정]
