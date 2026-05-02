package com.sleepcare.watch.contracts

// Wear OS Data Layer에서 사용하는 경로 상수입니다.
// 모바일과 워치가 같은 문자열을 써야 하므로 공통 모듈에서만 정의합니다.
object WatchPaths {
    const val SessionStart = "/sc/v1/ctl/start"
    const val SessionStop = "/sc/v1/ctl/stop"
    const val FlushPolicyUpdate = "/sc/v1/ctl/flush_policy"
    const val BackfillRequest = "/sc/v1/ctl/backfill_req"
    const val HrLive = "/sc/v1/hr/live"
    const val HrBatch = "/sc/v1/hr/batch"
    const val HrAck = "/sc/v1/hr/ack"
    const val SessionReady = "/sc/v1/session/ready"
    const val SessionError = "/sc/v1/session/error"
    const val SessionClosed = "/sc/v1/session/closed"
    const val AlertVibrate = "/sc/v1/alert/vibrate"

    const val Start = SessionStart
    const val Stop = SessionStop
    const val FlushPolicy = FlushPolicyUpdate
    const val Backfill = BackfillRequest
    const val Live = HrLive
    const val Batch = HrBatch
    const val Ack = HrAck
    const val Ready = SessionReady
    const val Error = SessionError
    const val Closed = SessionClosed
    const val Vibrate = AlertVibrate
}

// 모바일은 이 capability를 가진 노드에만 세션 시작 메시지를 보냅니다.
// 단순 페어링 여부가 아니라 SleepCare 워치 앱 설치/도달 가능 여부를 확인하기 위한 기준입니다.
object WatchCapabilities {
    const val SessionRuntime = "sleepcare_watch_session_runtime"
}
