# Sleep Care Mobile

수험생의 공부 중 졸음과 수면 패턴을 함께 분석해 수면 루틴을 제안하는 Android 앱 프로젝트입니다.

현재 이 저장소는 기획 문서와 Stitch 산출물을 바탕으로, `Kotlin + Jetpack Compose` 기반 Android 앱 MVP와 Pi 연동 1차 구현까지 완료된 상태입니다.

## 현재 상태

- Android 네이티브 앱 프로젝트 생성 완료
- Compose 기반 화면 흐름 구현 완료
- 온보딩, 홈, 분석, 기기 연결, 수면 스케줄, 학습 플랜, 시험 일정, 설정 화면 추가
- Room/DataStore 기반 로컬 저장 구조 추가
- Raspberry Pi 연동을 `NSD + WSS` 기반 실제 네트워크 구조로 전환
- 홈 화면의 공부 시작/종료, 세션 타이머, 실시간 Pi 위험 상태 반영
- `study_sessions`, `drowsiness_events` 기반 실제 세션/알림 저장
- 워치 앱은 아직 미구현이며, 앱에는 "준비 중" 상태와 수면 empty state를 표시
- 규칙 기반 추천 엔진 추가
- 단위 테스트 추가
- `testDebugUnitTest` 성공
- 실기기 수동 테스트 확인

## 기술 스택

- Kotlin
- Jetpack Compose
- Material 3
- Navigation Compose
- Hilt
- Room
- DataStore
- OkHttp(WebSocket/TLS)
- Android NSD

## 주요 폴더

- `app/src/main/java/com/sleepcare/mobile/navigation`
  앱 진입점과 전체 라우팅
- `app/src/main/java/com/sleepcare/mobile/ui`
  기능별 Compose 화면과 공통 컴포넌트
- `app/src/main/java/com/sleepcare/mobile/domain`
  도메인 모델, 저장소 인터페이스, 점수 계산 로직
- `app/src/main/java/com/sleepcare/mobile/data`
  Room/DataStore, Pi 네트워크 데이터 소스, 저장소 구현, 추천 엔진
- `docs`
  제품/아키텍처/데이터 기획 문서
- `stitch_exports/onboarding`
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
sdk.dir=/path/to/Android/Sdk
```

### Gradle 확인

```bash
./gradlew -version
./gradlew help
```

### 단위 테스트

```bash
./gradlew clean testDebugUnitTest
```

현재 기준으로 `testDebugUnitTest`는 성공했습니다.

Codex 샌드박스처럼 홈 디렉터리 쓰기 제약이 있는 환경에서는 Kotlin daemon이 fallback 컴파일로 동작할 수 있습니다. 그런 경우 아래처럼 `GRADLE_OPTS`를 함께 주는 편이 안전했습니다.

```bash
GRADLE_OPTS='-Duser.home=/tmp' ./gradlew clean testDebugUnitTest
```

### 수동 테스트

실기기에서 기본 플로우 동작을 확인했습니다.

- 앱 실행 및 온보딩 진입
- 홈/분석/스케줄/설정 탭 이동
- 상세 화면 진입 및 뒤로 가기
- 로컬 저장 상태 유지
- 홈 화면에서 공부 세션 시작/종료
- Pi 연결 상태 및 실시간 졸음 이벤트 반영

## 현재 확인된 이슈

- `compileSdk`는 현재 설치된 SDK에 맞춰 `35`로 설정됨
- Android Gradle Plugin `9.1.0`은 `compileSdk 35`에서 기본 Build Tools `36.0.0` 사용 경고를 출력할 수 있음
- 샌드박스 환경에서는 Kotlin daemon 임시 파일 경로 문제로 fallback 컴파일이 발생할 수 있음
- 모바일 앱은 Pi와의 `NSD + WSS` 연결을 구현했지만, 실제 Pi 펌웨어/서버와의 통합 검증은 같은 로컬 Wi-Fi 환경에서 계속 확인이 필요함
- 워치 앱은 아직 구현되지 않았고 `hr.ingest`, Wear OS Data Layer, Health Connect 기반 수면 동기화는 현재 앱에서 사용하지 않음
- 수면 분석/추천은 워치 데이터가 없을 때 empty state와 보정 없음 문구를 표시하도록 설계됨

## 현재 구현 범위 요약

- 모바일 앱 ↔ 라즈베리파이: 구현됨
- 로컬 Wi-Fi에서 `_sleepcare._tcp` NSD 탐색
- `WSS` 연결
- `hello`, `session.open`, `risk.update`, `alert.fire`, `session.close`, `session.summary` 처리
- 모바일 앱 ↔ 워치 앱: 미구현
- 앱 UI에서는 "워치 앱 준비 중" 상태를 표시
- 수면 데이터: 미구현
- 실제 수면 세션 수집은 아직 연결되지 않았고, 홈/분석 화면은 empty state를 표시
