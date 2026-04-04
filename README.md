# Sleep Care Mobile

수험생의 공부 중 졸음과 실제 수면 패턴을 함께 분석해 수면 루틴을 제안하는 Android 앱 프로젝트입니다.

현재 이 저장소는 기획 문서와 Stitch 산출물을 바탕으로, `Kotlin + Jetpack Compose` 기반 Android 앱 MVP 골격까지 구현된 상태입니다.

## 현재 상태

- Android 네이티브 앱 프로젝트 생성 완료
- Compose 기반 화면 흐름 구현 완료
- 온보딩, 홈, 분석, 기기 연결, 수면 스케줄, 학습 플랜, 시험 일정, 설정 화면 추가
- Room/DataStore 기반 로컬 저장 구조 추가
- 규칙 기반 추천 엔진 추가
- Raspberry Pi BLE / Smartwatch 연동은 `fake/stub` 구조로 연결
- 단위 테스트 초안 추가

## 기술 스택

- Kotlin
- Jetpack Compose
- Material 3
- Navigation Compose
- Hilt
- Room
- DataStore
- WorkManager

## 주요 폴더

- [`app/src/main/java/com/sleepcare/mobile/navigation`](/mnt/c/Users/cksgm/.gemini/antigravity/scratch/sleep_care-mobile/app/src/main/java/com/sleepcare/mobile/navigation)
  앱 진입점과 전체 라우팅
- [`app/src/main/java/com/sleepcare/mobile/ui`](/mnt/c/Users/cksgm/.gemini/antigravity/scratch/sleep_care-mobile/app/src/main/java/com/sleepcare/mobile/ui)
  기능별 Compose 화면과 공통 컴포넌트
- [`app/src/main/java/com/sleepcare/mobile/domain`](/mnt/c/Users/cksgm/.gemini/antigravity/scratch/sleep_care-mobile/app/src/main/java/com/sleepcare/mobile/domain)
  도메인 모델, 저장소 인터페이스, 점수 계산 로직
- [`app/src/main/java/com/sleepcare/mobile/data`](/mnt/c/Users/cksgm/.gemini/antigravity/scratch/sleep_care-mobile/app/src/main/java/com/sleepcare/mobile/data)
  Room/DataStore, fake 데이터 소스, 저장소 구현, 추천 엔진
- [`docs`](/mnt/c/Users/cksgm/.gemini/antigravity/scratch/sleep_care-mobile/docs)
  제품/아키텍처/데이터 기획 문서
- [`stitch_exports/onboarding`](/mnt/c/Users/cksgm/.gemini/antigravity/scratch/sleep_care-mobile/stitch_exports/onboarding)
  Stitch 화면 HTML, 이미지, 디자인 시스템 산출물

## 구현된 화면

- Onboarding
- Home Dashboard
- Analysis Hub
- Sleep Analysis Detail
- Drowsiness Analysis Detail
- Device Connection
- Sleep Schedule Suggestion
- Study Plan
- Exam Schedule
- Settings

## 실행 및 테스트

### 필수 조건

- JDK 17
- Android SDK
- `local.properties`에 SDK 경로 설정

현재 저장소의 `local.properties` 예시:

```properties
sdk.dir=/mnt/c/Users/cksgm/AppData/Local/Android/Sdk
```

### Gradle 확인

```bash
./gradlew -version
./gradlew help
```

### 단위 테스트

이 Codex 샌드박스에서는 Kotlin daemon이 기본 홈 디렉터리에 쓰지 못하는 경우가 있어서, 아래처럼 `GRADLE_OPTS`를 함께 주는 편이 안전했습니다.

```bash
GRADLE_OPTS='-Duser.home=/tmp' ./gradlew testDebugUnitTest
```

## 현재 확인된 이슈

- `compileSdk`는 현재 설치된 SDK에 맞춰 `35`로 설정됨
- Android Gradle Plugin `8.4.2`는 `compileSdk 35`에 대해 경고를 출력함
- 샌드박스 환경에서는 Kotlin daemon 임시 파일 경로 문제로 fallback 컴파일이 발생할 수 있음
- BLE/Health Connect는 실제 연동이 아니라 스텁 구조만 구현됨

## 다음 세션에서 바로 이어서 할 일

1. `testDebugUnitTest`를 로컬 환경에서 다시 끝까지 확인
2. 테스트 실패가 남으면 `app/build/reports/tests/testDebugUnitTest` 리포트 확인
3. 실제 BLE/Health Connect 구현으로 fake 데이터 소스 교체
4. 문자열 리소스 정리
5. 필요하면 AGP 버전 업그레이드 검토

## 참고 문서

- [전체 계획서](/mnt/c/Users/cksgm/.gemini/antigravity/scratch/sleep_care-mobile/plan.md)
- [모바일 앱 계획](/mnt/c/Users/cksgm/.gemini/antigravity/scratch/sleep_care-mobile/docs/plan-mobile-app.md)
- [데이터 및 추천 계획](/mnt/c/Users/cksgm/.gemini/antigravity/scratch/sleep_care-mobile/docs/plan-data-and-recommendation.md)
- [프로젝트 개요](/mnt/c/Users/cksgm/.gemini/antigravity/scratch/sleep_care-mobile/docs/plan-overview.md)
