# Sleep Care Notion Hub Draft

## 이 문서의 목적
이 문서는 Notion에 그대로 붙여 넣어 프로젝트 허브 페이지로 쓰기 좋은 형태를 목표로 한다.

## 추천 Notion 페이지 구조
- Project Overview
- Raspberry Pi
- Mobile App
- Watch App
- Data And Recommendation
- Discussion Topics
- Meeting Notes
- Decision Log
- Task Board

## Project Overview
### 프로젝트 한 줄 소개
수험생의 공부 중 졸음과 실제 수면 패턴을 함께 분석해 수면 스케줄을 제안하는 프로젝트.

### 목표
- 공부 중 졸음 감지
- 실제 수면 데이터 수집
- 통합 분석 기반 수면 스케줄 제안

### MVP
- 라즈베리파이 기본 졸음 감지
- 스마트워치 수면 데이터 연동
- 모바일 앱 데이터 조회
- 간단한 수면 스케줄 제안

### 모바일 앱 기술 스택
- Kotlin
- Jetpack Compose + Material 3
- MVVM + Repository + UDF(단방향 데이터 흐름)
- ViewModel + Kotlin Coroutines + Flow + Hilt
- Room + DataStore
- WorkManager
- BLE(GATT)
- CompanionDeviceManager 우선 검토
- Health Connect 우선 검토

## 페이지별 하위 구성 예시
### Raspberry Pi
- 목표
- 주요 기능
- 구현 단계
- 기술 리스크
- 논의 사항

### Mobile App
- 목표
- 화면 구조
- 핵심 기능
- 기술 스택
- 추천 로직
- 논의 사항

### Watch App
- 목표
- 수집 가능 데이터
- 플랫폼 제약
- 동기화 구조
- 논의 사항

### Data And Recommendation
- 데이터 항목
- 분석 지표
- 추천 규칙
- 향후 고도화
- 논의 사항

## Notion 데이터베이스 추천
### Decision Log 속성
- 제목
- 날짜
- 카테고리
- 결정 내용
- 이유
- 담당자

### Task Board 속성
- 작업명
- 담당자
- 상태
- 우선순위
- 관련 영역
- 마감일

### Meeting Notes 속성
- 회의명
- 날짜
- 참석자
- 안건
- 결정 사항
- 액션 아이템

## 노션으로 가져오기 좋게 쓰는 팁
- 한 문서는 하나의 목적만 갖도록 짧게 나눈다.
- 제목은 페이지 이름처럼 단순하게 쓴다.
- 섹션은 Heading 2 또는 Heading 3 중심으로 통일한다.
- 논의 포인트는 질문형 bullet로 유지한다.
- GPT 의견은 별도 bullet로 표기해 사람 의견과 구분한다.

## 연결 문서
- [프로젝트 개요](./plan-overview.md)
- [라즈베리파이 계획](./plan-raspberry-pi.md)
- [모바일 앱 계획](./plan-mobile-app.md)
- [워치 앱 계획](./plan-watch-app.md)
- [데이터 및 추천 계획](./plan-data-and-recommendation.md)
- [논의 카테고리](./notion-discussion-topics.md)
