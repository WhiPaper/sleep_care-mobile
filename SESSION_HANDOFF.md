# Session Handoff

## 한 줄 요약

기획 문서와 Stitch 산출물을 바탕으로 Android Compose 앱 MVP를 구현했고, 로컬 저장소와 규칙 기반 추천 엔진, fake Pi/Watch 연동 구조까지 연결한 뒤 유닛 테스트와 실기기 수동 테스트까지 확인한 상태입니다.

## 이번 세션에서 한 일

- Android 프로젝트 생성
- Gradle wrapper 및 Android 빌드 설정 추가
- Compose 기반 10개 화면 흐름 추가
- 공통 테마/컴포넌트 구성
- Room/DataStore 기반 로컬 데이터 구조 추가
- fake `PiBleDataSource`, fake `WatchSleepDataSource` 추가
- `RecommendationEngine` 및 저장소 구현 추가
- 단위 테스트 초안 추가
- `./gradlew clean testDebugUnitTest` 성공 확인
- 실기기 수동 테스트 확인
- README 정리

## 현재 설계 기준

- 최신 통신 구조 기준은 `스마트워치 → 스마트폰 → 라즈베리파이`다.
- 워치 ↔ 모바일 앱은 `Wear OS Data Layer` 기준으로 본다.
- 모바일 앱 ↔ 라즈베리파이는 `NSD + WSS` 기준으로 본다.
- 최종 졸음 판단 주체는 모바일 앱이 아니라 라즈베리파이다.
- 심박 데이터는 보조 신호이고, 안구 데이터가 주 신호다.
- 현재 코드에는 예전 명칭인 `PiBleDataSource`가 남아 있지만, 다음 구현은 BLE가 아니라 프로토콜 문서 기준으로 진행하는 것이 맞다.

## 핵심 코드 위치

- `app/src/main/java/com/sleepcare/mobile/navigation/SleepCareApp.kt`
- `app/src/main/java/com/sleepcare/mobile/data/repository/AppRepositories.kt`
- `app/src/main/java/com/sleepcare/mobile/data/local/LocalStorage.kt`
- `app/src/main/java/com/sleepcare/mobile/ui/components/SleepCareComponents.kt`
- `app/build.gradle.kts`

## 현재 상태

- 앱 소스 구현 완료
- `local.properties`는 WSL 기준 SDK 경로로 정리됨
- `compileSdk` / `targetSdk`는 `35`

## 검증 결과

- `./gradlew -version` 성공
- `./gradlew help` 성공
- `./gradlew clean testDebugUnitTest` 성공
- 실기기에서 기본 화면 플로우 수동 테스트 확인
- 이 샌드박스에서는 Kotlin daemon 기본 홈 경로에 쓰지 못해 fallback 컴파일이 자주 발생

```bash
GRADLE_OPTS='-Duser.home=/tmp' ./gradlew clean testDebugUnitTest
```

## 남아 있는 이슈

1. 샌드박스 특성상 Kotlin daemon 관련 환경 이슈가 있음
2. `compileSdk 35`에 대해 AGP 8.4.2 경고가 출력됨
3. Pi/Watch 연동과 수면 데이터 연동은 아직 fake 구현
4. instrumentation/UI 테스트는 아직 본격 추가 전

## 다음 세션 추천 순서

1. 실제 연동 작업으로 확장

- 워치 세션 통신을 Wear OS Data Layer 기준 구현으로 교체
- 라즈베리파이 연동을 NSD + WSS 기준 구현으로 교체
- 수면 데이터 연동 경로를 Health Connect 또는 실제 플랫폼 API 기준으로 구체화

2. 테스트 범위 확장

- Compose UI 테스트 추가
- 설정/일정/온보딩 복원 흐름 검증 추가

3. 명칭과 구조 정리

- `PiBleDataSource`처럼 예전 설계 기준이 남은 이름을 최신 프로토콜 기준으로 정리
- 세션 제어, 파이 통신, 워치 통신 책임을 저장소 계층에서 분리

4. 빌드 환경 정리

- 필요하면 AGP 업그레이드 검토
- Codex 샌드박스에서는 `GRADLE_OPTS='-Duser.home=/tmp'` 유지

## 주의

- 현재 워크트리에는 앱 구현 파일 외에 `docs`, `stitch_exports`, `.idea` 변경도 함께 있음
