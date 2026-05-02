# Raspberry Pi WSS 서버 구현 가이드

이 문서는 Sleep Care 모바일 앱과 라즈베리파이 서버를 실제로 연결하기 위해 Pi 개발자가 해야 할 일을 정리한다. 모바일 앱은 같은 로컬 Wi-Fi 안에서 Pi를 자동 발견한 뒤, TLS가 적용된 WebSocket인 `WSS`로 연결한다.

최종 목표는 다음 구조다.

```text
Android 앱
  -> NSD/DNS-SD로 _sleepcare._tcp 서비스 발견
  -> TXT record에서 proto/tls/ws/device_id 확인
  -> wss://<pi-ip>:<port>/<ws-path> 연결
  -> hello/session.open/hr.ingest/session.close 송신

Raspberry Pi
  -> Avahi로 _sleepcare._tcp 서비스 광고
  -> WSS 서버 실행
  -> hello_ack/session.ack/risk.update/alert.fire/session.summary 응답
  -> 카메라 기반 졸음 감지 및 로컬 경고 출력
```

## 1. Pi 개발자가 받아야 하는 파일

Android 앱 개발자가 Pi 개발자에게 전달해야 하는 파일은 두 개다.

```text
sleepcare_pi_dev_cert.pem
sleepcare_pi_dev_key.pem
```

각 파일의 역할은 다르다.

```text
sleepcare_pi_dev_cert.pem
  서버 인증서다. Pi 서버가 Android 앱에 자신을 증명할 때 보여준다.
  Android 앱에도 같은 cert 파일이 들어간다.

sleepcare_pi_dev_key.pem
  서버 개인키다. Pi 서버가 TLS/WSS를 열 때 사용한다.
  Android 앱, APK, 공개 저장소, 문서 첨부에 절대 넣으면 안 된다.
```

중요한 점은 cert와 key가 반드시 같은 쌍이어야 한다는 것이다. Android 앱은 `sleepcare_pi_dev_cert.pem`을 신뢰하도록 빌드되어 있으므로, Pi 서버는 그 인증서에 대응되는 `sleepcare_pi_dev_key.pem`으로 WSS를 띄워야 한다.

## 2. 사용하는 도구

Pi에서는 다음 도구를 사용한다.

```text
OpenSSL
  인증서 확인, 인증서/개인키 생성, TLS 문제 디버깅에 사용한다.

Avahi
  Linux/Raspberry Pi에서 mDNS/DNS-SD 서비스를 광고하는 데 사용한다.
  Android 앱은 이 광고를 NSD로 발견한다.

Python 3
  WSS 서버와 졸음 판단 로직을 실행한다.

aiohttp
  Python에서 WebSocket 서버를 구현한다.

Python ssl 모듈
  sleepcare_pi_dev_cert.pem + sleepcare_pi_dev_key.pem을 서버 TLS에 연결한다.
```

설치 명령은 Raspberry Pi OS/Debian/Ubuntu 기준이다.

```bash
sudo apt update
sudo apt install -y python3 python3-venv python3-pip openssl avahi-daemon avahi-utils
```

Python 가상환경을 쓰는 것을 권장한다.

```bash
mkdir -p ~/sleepcare-pi
cd ~/sleepcare-pi

python3 -m venv .venv
source .venv/bin/activate

pip install aiohttp
```

## 3. 인증서 파일 배치

Pi 서버 디렉터리에 두 파일을 둔다.

```text
~/sleepcare-pi/
  sleepcare_pi_server.py
  sleepcare_pi_dev_cert.pem
  sleepcare_pi_dev_key.pem
```

개인키 권한은 소유자만 읽을 수 있게 제한한다.

```bash
chmod 600 ~/sleepcare-pi/sleepcare_pi_dev_key.pem
chmod 644 ~/sleepcare-pi/sleepcare_pi_dev_cert.pem
```

인증서 내용을 확인한다.

```bash
openssl x509 -in ~/sleepcare-pi/sleepcare_pi_dev_cert.pem -subject -issuer -dates -noout
```

cert와 key가 한 쌍인지 확인한다.

```bash
openssl x509 -noout -modulus -in ~/sleepcare-pi/sleepcare_pi_dev_cert.pem | openssl md5
openssl rsa  -noout -modulus -in ~/sleepcare-pi/sleepcare_pi_dev_key.pem  | openssl md5
```

두 출력값이 같아야 한다. 다르면 cert와 key가 서로 맞지 않는 파일이다.

## 4. Avahi로 Pi 서비스 광고하기

Android 앱은 `_sleepcare._tcp` 서비스를 찾는다. Pi는 Avahi로 이 서비스를 광고해야 한다.

다음 파일을 만든다.

```bash
sudo nano /etc/avahi/services/sleepcare.service
```

내용은 다음과 같다.

```xml
<?xml version="1.0" standalone="no"?>
<!DOCTYPE service-group SYSTEM "avahi-service.dtd">
<service-group>
  <!-- Android 앱의 기기 목록에 보일 이름이다. -->
  <name replace-wildcards="yes">SleepCare-Pi-%h</name>

  <service>
    <!-- Android 앱은 정확히 이 service type을 탐색한다. -->
    <type>_sleepcare._tcp</type>

    <!-- WSS 서버가 대기하는 TCP 포트다. 서버 코드의 PORT와 같아야 한다. -->
    <port>8765</port>

    <!-- 앱은 proto=v1과 tls=1이 아니면 연결 대상으로 인정하지 않는다. -->
    <txt-record>proto=v1</txt-record>
    <txt-record>tls=1</txt-record>

    <!-- 앱이 접속할 WebSocket path다. 서버 코드의 /ws와 같아야 한다. -->
    <txt-record>ws=/ws</txt-record>

    <!-- 로그와 UI에 표시할 Pi 식별자다. Pi마다 다르게 두는 것을 권장한다. -->
    <txt-record>device_id=deskpi-a1</txt-record>

    <!-- 카메라가 있는 Pi라는 힌트다. 현재 앱은 필수로 검사하지 않지만 문서상 유지한다. -->
    <txt-record>cam=1</txt-record>
  </service>
</service-group>
```

Avahi를 재시작한다.

```bash
sudo systemctl restart avahi-daemon
sudo systemctl enable avahi-daemon
```

광고가 보이는지 Pi에서 확인한다.

```bash
avahi-browse -rt _sleepcare._tcp
```

출력에 `SleepCare-Pi-...`, `proto=v1`, `tls=1`, `ws=/ws`가 보여야 한다.

## 5. Pi WSS 서버 예제 코드

아래 코드는 Android 앱과 연결되는 최소 구현이다. 카메라/AI 모델이 아직 없어도 앱 연결, handshake, 세션 시작, 위험도 업데이트, 알림, 세션 종료를 검증할 수 있다.

파일명 예시:

```bash
nano ~/sleepcare-pi/sleepcare_pi_server.py
```

코드:

```python
#!/usr/bin/env python3
"""
Sleep Care Raspberry Pi WSS server example.

역할:
- Android 앱이 NSD로 찾은 뒤 wss://<pi-ip>:8765/ws 로 접속한다.
- 앱에서 hello/session.open/hr.ingest/session.close를 받는다.
- Pi는 hello_ack/session.ack/risk.update/alert.fire/session.summary를 보낸다.

실제 카메라 졸음 감지 모델은 calculate_risk() 안쪽에 연결하면 된다.
"""

import asyncio
import json
import ssl
import time
from dataclasses import dataclass, field
from typing import Any, Optional

from aiohttp import web


# 0.0.0.0은 Pi의 모든 네트워크 인터페이스에서 접속을 받겠다는 뜻이다.
# Android 폰은 같은 Wi-Fi에서 Pi의 실제 IP로 접속하므로 localhost나 127.0.0.1을 쓰면 안 된다.
HOST = "0.0.0.0"

# Avahi 서비스 파일의 <port> 값과 반드시 같아야 한다.
PORT = 8765

# Avahi TXT record의 ws=/ws 값과 반드시 같아야 한다.
WS_PATH = "/ws"

# Android 앱에 들어간 인증서와 같은 cert 파일이다.
# 이 파일은 공개되어도 되지만, 앱에 들어간 cert와 byte-for-byte로 같아야 한다.
CERT_FILE = "sleepcare_pi_dev_cert.pem"

# cert에 대응되는 개인키다. 이 파일은 Pi 서버 밖으로 노출하면 안 된다.
KEY_FILE = "sleepcare_pi_dev_key.pem"

# 앱 UI와 로그에서 Pi를 구분하는 값이다. Pi가 여러 대라면 각자 다르게 둔다.
DEVICE_ID = "deskpi-a1"

# Android 앱의 PiProtocolCodec이 현재 v=1 envelope를 기대한다.
PROTOCOL_VERSION = 1


def now_ms() -> int:
    """현재 시각을 epoch millisecond로 반환한다."""
    return int(time.time() * 1000)


@dataclass
class SessionState:
    """현재 학습 세션 상태를 보관한다."""

    # Android 앱이 session.open에서 보낸 세션 ID다.
    # 이후 risk.update, alert.fire, session.summary는 모두 같은 sid를 써야 앱이 현재 세션으로 인식한다.
    sid: str

    # 세션 시작 시각이다. 예제의 더미 risk 패턴과 세션 길이 계산에 사용한다.
    opened_at_ms: int

    # eye-only: 카메라 기반 단독 판단
    # eye+hr: 카메라 판단에 워치 심박 보조 신호를 함께 사용
    mode: str = "eye-only"

    # False로 바꾸면 risk_loop가 자연스럽게 종료된다.
    running: bool = True

    # Pi가 앱으로 보내는 메시지의 증가 번호다.
    # 앱은 현재 엄격히 검증하지 않지만, 로그 추적과 향후 ACK/replay에 필요하다.
    seq: int = 1

    # alert.fire를 보낸 횟수다. session.summary에 포함된다.
    total_alerts: int = 0

    # 세션 중 가장 높았던 fused_score다. session.summary에 포함된다.
    peak_fused_score: float = 0.0

    # Android 앱이 hr.ingest로 전달한 최신 심박 샘플이다.
    # 워치가 없거나 eye-only 모드면 None일 수 있다.
    latest_hr: Optional[dict[str, Any]] = None

    # 마지막 위험 상태다. 세션 종료 요약의 final_state로 사용한다.
    last_risk_state: str = "BASELINE"

    # risk.update를 주기적으로 보내는 백그라운드 task다.
    # 새 세션 시작/세션 종료 때 반드시 cancel해야 중복 루프가 생기지 않는다.
    background_task: Optional[asyncio.Task] = field(default=None, repr=False)

    # alert.fire cooldown용 timestamp다. 같은 경고를 너무 자주 보내지 않기 위해 사용한다.
    last_alert_sent_ms: int = 0

    def next_seq(self) -> int:
        """Pi가 보내는 메시지의 sequence 번호를 1씩 증가시킨다."""
        value = self.seq
        self.seq += 1
        return value


class SleepCarePiServer:
    def __init__(self) -> None:
        # MVP는 동시에 하나의 Android 앱, 하나의 세션만 처리한다고 가정한다.
        # 다중 클라이언트를 지원하려면 sid 또는 WebSocket별 session map으로 바꿔야 한다.
        self.session: Optional[SessionState] = None

    def build_envelope(
        self,
        *,
        message_type: str,
        sequence: int,
        session_id: Optional[str] = None,
        ack_required: bool = False,
        body: Optional[dict[str, Any]] = None,
    ) -> str:
        """모바일 앱이 기대하는 공통 JSON envelope 형식으로 메시지를 만든다."""
        # 모든 Pi -> Android 메시지는 이 envelope를 반드시 지켜야 한다.
        # 앱 파서는 t, sid, seq, src, sent_at_ms, body를 기준으로 도메인 모델을 만든다.
        # separators를 지정해 공백 없는 JSON으로 보내지만, 공백이 있어도 앱 파싱에는 문제 없다.
        payload = {
            "v": PROTOCOL_VERSION,
            "t": message_type,
            "sid": session_id,
            "seq": sequence,
            "src": "pi",
            "sent_at_ms": now_ms(),
            "ack_required": ack_required,
            "body": body or {},
        }
        return json.dumps(payload, separators=(",", ":"))

    async def websocket_handler(self, request: web.Request) -> web.WebSocketResponse:
        """Android 앱의 /ws WebSocket 연결을 처리한다."""
        # heartbeat=20은 aiohttp가 WebSocket ping/pong으로 죽은 연결을 감지하게 한다.
        # Android 쪽 OkHttp도 20초 pingInterval을 사용하므로 양쪽 모두 연결 상태를 빨리 알 수 있다.
        ws = web.WebSocketResponse(heartbeat=20)
        await ws.prepare(request)

        print("[ws] Android client connected")

        # 이 루프는 클라이언트가 연결을 닫을 때까지 계속 돈다.
        # TEXT 메시지만 Sleep Care JSON protocol로 처리하고, 바이너리 메시지는 사용하지 않는다.
        async for msg in ws:
            if msg.type == web.WSMsgType.TEXT:
                await self.handle_text_message(ws, msg.data)
            elif msg.type == web.WSMsgType.ERROR:
                print(f"[ws] connection error: {ws.exception()}")

        print("[ws] Android client disconnected")
        return ws

    async def handle_text_message(self, ws: web.WebSocketResponse, raw: str) -> None:
        """앱에서 받은 JSON 메시지를 event type별로 분기한다."""
        # 네트워크 입력은 항상 신뢰하지 않는다. JSON이 깨졌으면 서버를 죽이지 말고 무시한다.
        try:
            envelope = json.loads(raw)
        except json.JSONDecodeError:
            print(f"[protocol] invalid JSON ignored: {raw}")
            return

        message_type = envelope.get("t")
        session_id = envelope.get("sid")
        body = envelope.get("body") or {}

        print(f"[protocol] recv type={message_type} sid={session_id} body={body}")

        # Android 앱이 현재 보내는 주요 이벤트:
        # - hello: WSS 연결 직후 handshake
        # - session.open: 공부 세션 시작
        # - hr.ingest: 워치 심박 샘플 중계
        # - session.close: 공부 세션 종료
        # - ping: 선택적 연결 유지 메시지
        if message_type == "hello":
            await self.handle_hello(ws)
        elif message_type == "session.open":
            await self.handle_session_open(ws, session_id, body)
        elif message_type == "hr.ingest":
            await self.handle_hr_ingest(body)
        elif message_type == "session.close":
            await self.handle_session_close(ws, session_id)
        elif message_type == "ping":
            await self.send_pong(ws, session_id)
        else:
            print(f"[protocol] unsupported message type: {message_type}")

    async def handle_hello(self, ws: web.WebSocketResponse) -> None:
        """앱의 최초 연결 확인 메시지에 응답한다."""
        # Android 앱은 WSS가 열리자마자 hello를 보낸 뒤 hello_ack를 기다린다.
        # 이 응답이 6초 안에 오지 않으면 앱은 연결 실패로 처리한다.
        # sid는 아직 세션이 열리기 전이므로 null이어도 된다.
        payload = self.build_envelope(
            message_type="hello_ack",
            sequence=0,
            body={
                # 앱의 연결 상세 문구와 로그에 표시할 Pi ID다.
                "device_id": DEVICE_ID,

                # 앱과 Pi가 같은 프로토콜 버전을 말하고 있음을 알려준다.
                "proto": "v1",

                # 초기 MVP는 워치가 없어도 카메라만으로 돌아가야 하므로 eye-only를 기본값으로 둔다.
                "mode": "eye-only",
            },
        )
        await ws.send_str(payload)
        print("[protocol] sent hello_ack")

    async def handle_session_open(
        self,
        ws: web.WebSocketResponse,
        session_id: Optional[str],
        body: dict[str, Any],
    ) -> None:
        """학습 세션을 시작하고, 카메라/졸음 판단 루프를 켠다."""
        # session.open의 sid는 앱이 생성한 현재 공부 세션 ID다.
        # Pi가 이후 보내는 모든 세션 메시지에 같은 sid를 넣어야 앱 저장소가 같은 세션으로 묶는다.
        if not session_id:
            print("[session] session.open without sid ignored")
            return

        # 이미 세션이 있으면 정리하고 새 세션으로 교체한다.
        # 실서비스에서는 기존 세션을 거절할지, 종료 후 새로 열지 정책을 정해야 한다.
        await self.stop_background_task()

        # 앱은 워치 연결 여부와 eye_only 여부를 body에 담아 보낸다.
        # watch_available=True이고 eye_only=False이면 Pi는 심박 보조 신호를 기대할 수 있다.
        watch_available = bool(body.get("watch_available", False))
        eye_only = bool(body.get("eye_only", True))
        mode = "eye+hr" if watch_available and not eye_only else "eye-only"

        self.session = SessionState(
            sid=session_id,
            opened_at_ms=now_ms(),
            mode=mode,
        )

        ack = self.build_envelope(
            message_type="session.ack",
            sequence=self.session.next_seq(),
            session_id=session_id,
            ack_required=False,
            body={
                # accepted=False를 보내는 흐름은 아직 앱에서 세밀히 처리하지 않는다.
                # 실패 상황에서는 연결을 닫거나 session.ack를 보내지 않는 대신, 추후 error 이벤트를 정의하는 편이 좋다.
                "accepted": True,
                "mode": mode,

                # 실제 구현에서는 카메라 open, 모델 warmup, 조명 조건 확인이 끝난 뒤 True로 두는 것이 좋다.
                "camera_ready": True,
            },
        )
        await ws.send_str(ack)
        print(f"[session] opened sid={session_id} mode={mode}")

        # 실제 구현에서는 이 루프가 카메라 프레임과 모델 결과를 계속 읽는다.
        self.session.background_task = asyncio.create_task(self.risk_loop(ws, session_id))

    async def handle_hr_ingest(self, body: dict[str, Any]) -> None:
        """앱이 워치 심박 샘플을 중계하면 Pi의 보조 신호로 저장한다."""
        # hr.ingest는 필수 신호가 아니라 보조 신호다.
        # Pi는 이 메시지가 한 번도 오지 않아도 eye-only 모드로 risk.update를 계속 보내야 한다.
        if self.session is None:
            return

        # 여기서는 최신 샘플 하나만 저장한다.
        # 실제 구현에서 HRV/IBI 추세가 필요하면 최근 N분짜리 ring buffer로 바꾸면 된다.
        self.session.latest_hr = body
        print(
            "[hr] sample_seq=%s bpm=%s quality=%s"
            % (body.get("sample_seq"), body.get("bpm"), body.get("hr_quality"))
        )

    async def handle_session_close(
        self,
        ws: web.WebSocketResponse,
        session_id: Optional[str],
    ) -> None:
        """세션을 종료하고 앱에 요약을 보낸다."""
        # 앱이 종료하려는 sid와 Pi가 들고 있는 sid가 다르면 무시한다.
        # 이 검사는 오래된 close 메시지가 현재 세션을 잘못 닫는 일을 막는다.
        if self.session is None or self.session.sid != session_id:
            print("[session] close ignored: no matching session")
            return

        session = self.session
        session.running = False

        # risk_loop를 먼저 멈춘 뒤 summary를 보내야,
        # session.summary 이후에 risk.update가 한 번 더 나가는 순서 꼬임을 피할 수 있다.
        await self.stop_background_task()

        summary = self.build_envelope(
            message_type="session.summary",
            sequence=session.next_seq(),
            session_id=session.sid,
            ack_required=False,
            body={
                # 앱은 final_state를 세션의 마지막 상태로 저장한다.
                "final_state": session.last_risk_state,

                # 앱의 세션 기록/분석 화면에서 사용할 수 있는 경고 횟수다.
                "total_alerts": session.total_alerts,

                # 세션 중 최고 위험도다. 없으면 0.0으로 보내도 된다.
                "peak_fused_score": round(session.peak_fused_score, 3),
                "mode": session.mode,
                "summary_reason": "user_stop",
            },
        )
        await ws.send_str(summary)
        print(f"[session] closed sid={session.sid}")
        self.session = None

    async def send_pong(self, ws: web.WebSocketResponse, session_id: Optional[str]) -> None:
        """앱이 ping을 보낼 경우 연결 유지 응답을 보낸다."""
        # OkHttp의 WebSocket ping과 별개로, 애플리케이션 레벨 ping을 지원하고 싶을 때 사용한다.
        # 현재 앱은 pong/ack를 특별히 저장하지 않으므로 연결 확인용으로만 보면 된다.
        seq = self.session.next_seq() if self.session else 0
        payload = self.build_envelope(
            message_type="pong",
            sequence=seq,
            session_id=session_id,
        )
        await ws.send_str(payload)

    async def risk_loop(self, ws: web.WebSocketResponse, session_id: str) -> None:
        """세션 중 주기적으로 위험도를 계산해 앱으로 보낸다."""
        try:
            while self.session and self.session.running and not ws.closed:
                # 1. 현재 카메라/심박 상태로 위험도를 계산한다.
                risk = self.calculate_risk()

                # 2. 앱 UI가 실시간으로 반영할 수 있도록 risk.update를 보낸다.
                await self.send_risk_update(ws, risk)

                # 3. ALERTING 상태라면 별도의 alert.fire 이벤트를 보낸다.
                # risk.update는 상태 표시용이고, alert.fire는 이벤트 저장/진동/경고 트리거용이다.
                if risk["state"] == "ALERTING":
                    await self.send_alert_fire(ws, risk)

                # MVP에서는 1초마다 보낸다. 실제 제품에서는 부하와 UI 필요에 맞춰 조정한다.
                await asyncio.sleep(1.0)
        except asyncio.CancelledError:
            pass

    def calculate_risk(self) -> dict[str, Any]:
        """
        졸음 위험도를 계산한다.

        현재 코드는 연결 검증용 더미 로직이다.
        실제 구현에서는 여기서 카메라 프레임, 눈 감김 점수, 고개 상태,
        심박 보조 신호를 읽어 eye_score/hr_score/fused_score/state를 계산한다.

        실제 구현 예:
        - camera.read()로 프레임 획득
        - 얼굴 landmark 또는 눈 영역 crop
        - eye aspect ratio, 눈 감김 지속 시간, 고개 떨굼 여부 계산
        - latest_hr가 있으면 심박 품질과 IBI/HRV를 보조 신호로 반영
        - 최종 state를 BASELINE/SUSPECT/ALERTING 중 하나로 결정
        """
        if self.session is None:
            # 세션이 없을 때는 안전한 기본값을 반환한다.
            # 이 분기는 정상 흐름에서는 거의 타지 않는다.
            return {
                "mode": "eye-only",
                "eye_score": 0.0,
                "hr_score": None,
                "fused_score": 0.0,
                "state": "BASELINE",
                "recommended_flush_sec": 15,
            }

        elapsed_sec = max(0, (now_ms() - self.session.opened_at_ms) // 1000)

        # 데모용 패턴:
        # 0~9초 BASELINE, 10~19초 SUSPECT, 20~24초 ALERTING, 이후 BASELINE.
        # 이 패턴 덕분에 실제 카메라 모델이 없어도 Android 앱에서 상태 변화와 alert.fire 저장을 검증할 수 있다.
        # 카메라 모델을 붙이면 이 if/elif 블록을 모델 결과 기반 계산으로 교체하면 된다.
        cycle = elapsed_sec % 30
        if cycle < 10:
            # 정상 상태: 앱은 안정 상태 메시지를 보여준다.
            eye_score = 0.25
            state = "BASELINE"
            recommended_flush_sec = 15
        elif cycle < 20:
            # 의심 상태: 앱은 주의 상태를 보여주고, 워치가 있으면 flush 주기를 줄일 수 있다.
            eye_score = 0.68
            state = "SUSPECT"
            recommended_flush_sec = 5
        elif cycle < 25:
            # 경고 상태: risk.update와 별도로 alert.fire를 발생시킨다.
            eye_score = 0.86
            state = "ALERTING"
            recommended_flush_sec = 2
        else:
            # 회복 상태: 다시 정상으로 돌아가는 흐름을 검증한다.
            eye_score = 0.35
            state = "BASELINE"
            recommended_flush_sec = 15

        # 심박은 보조 신호다. 값이 없으면 eye_score만 fused_score로 사용한다.
        # 값이 있으면 예시로 eye 75%, hr 25% 가중치를 둔다.
        # 실제 가중치는 실험 데이터로 조정해야 한다.
        hr_score = self.estimate_hr_score(self.session.latest_hr)
        fused_score = eye_score if hr_score is None else (eye_score * 0.75 + hr_score * 0.25)

        # summary에 넣을 세션 최고 점수와 마지막 상태를 갱신한다.
        self.session.peak_fused_score = max(self.session.peak_fused_score, fused_score)
        self.session.last_risk_state = state

        return {
            "mode": self.session.mode,
            "eye_score": eye_score,
            "hr_score": hr_score,
            "fused_score": fused_score,
            "state": state,
            "recommended_flush_sec": recommended_flush_sec,
        }

    def estimate_hr_score(self, sample: Optional[dict[str, Any]]) -> Optional[float]:
        """
        심박 보조 점수를 계산한다.

        현재는 샘플 품질이 ok이면 약한 보조 점수를 주는 예시다.
        실제 구현에서는 사용자 기준 심박, IBI, HRV, 움직임 품질을 반영한다.
        """
        # 워치가 없거나 아직 샘플이 도착하지 않은 경우다.
        # None은 "심박 신호를 융합에 쓰지 않음"을 의미한다.
        if not sample:
            return None

        # Android 앱은 Samsung Health Sensor SDK의 status 값을 hr_quality로 변환해서 보낸다.
        # ok가 아니면 움직임/착용 불량/초기화 상태일 가능성이 크므로 가중치를 거의 주지 않는다.
        if sample.get("hr_quality") != "ok":
            return 0.0

        bpm = sample.get("bpm")
        if not isinstance(bpm, (int, float)):
            return 0.0

        # 예시 규칙:
        # 졸음이 오면 일부 사용자에게 심박 저하가 나타날 수 있으므로 낮은 bpm에 조금 더 높은 보조 점수를 준다.
        # 실제 제품에서는 개인 기준선과 최근 추세를 같이 봐야 한다.
        return 0.45 if bpm < 55 else 0.25

    async def send_risk_update(self, ws: web.WebSocketResponse, risk: dict[str, Any]) -> None:
        """앱의 홈/분석 UI가 사용할 실시간 위험도 업데이트를 보낸다."""
        if self.session is None:
            return

        # risk.update는 이벤트 저장보다는 "현재 상태 표시" 성격이 강하다.
        # 앱은 이 메시지를 받아 홈/분석 화면의 실시간 졸음 상태를 갱신한다.
        payload = self.build_envelope(
            message_type="risk.update",
            sequence=self.session.next_seq(),
            session_id=self.session.sid,
            ack_required=False,
            body={
                # eye-only 또는 eye+hr.
                "mode": risk["mode"],

                # 0.0~1.0 범위의 카메라 기반 위험 점수.
                "eye_score": round(risk["eye_score"], 3),

                # 워치 신호가 없으면 null로 보낸다.
                "hr_score": None if risk["hr_score"] is None else round(risk["hr_score"], 3),

                # 앱이 주로 표시할 최종 위험 점수.
                "fused_score": round(risk["fused_score"], 3),

                # BASELINE, SUSPECT, ALERTING 중 하나를 권장한다.
                "state": risk["state"],

                # 워치가 있을 경우 앱이 flush 정책을 조정하는 힌트로 사용한다.
                "recommended_flush_sec": risk["recommended_flush_sec"],
            },
        )
        await ws.send_str(payload)
        print(f"[risk] sent state={risk['state']} fused={risk['fused_score']:.3f}")

    async def send_alert_fire(self, ws: web.WebSocketResponse, risk: dict[str, Any]) -> None:
        """졸음 경고가 필요할 때 앱에 alert.fire를 보낸다."""
        if self.session is None:
            return

        # ALERTING 상태가 반복되어도 앱 이벤트가 과도하게 쌓이지 않도록
        # 최소 20초 간격으로만 alert.fire를 보낸다.
        # 실제 구현에서는 "ALERTING에 처음 진입한 순간" 또는 "cooldown 후 재진입"에만 보내는 방식이 더 좋다.
        if now_ms() - self.session.last_alert_sent_ms < 20_000:
            return
        self.session.last_alert_sent_ms = now_ms()
        self.session.total_alerts += 1

        # alert.fire는 앱에서 실제 졸음 이벤트로 저장된다.
        # 워치가 연결되어 있으면 앱이 이 이벤트를 받아 워치 진동 명령도 보낸다.
        payload = self.build_envelope(
            message_type="alert.fire",
            sequence=self.session.next_seq(),
            session_id=self.session.sid,
            ack_required=True,
            body={
                # 1~4 정도의 경고 강도를 권장한다. 앱은 severity로 저장한다.
                "level": 2,

                # 분석 화면에 사람이 읽을 수 있는 라벨로 변환될 수 있다.
                "reason": "eye_closed_persistent",

                # 경고가 지속된 시간 또는 알림 지속 시간을 ms로 표현한다.
                "duration_ms": 5000,
            },
        )
        await ws.send_str(payload)
        print("[alert] sent alert.fire")

    async def stop_background_task(self) -> None:
        """기존 위험도 루프가 돌고 있으면 안전하게 중단한다."""
        if self.session and self.session.background_task:
            # task.cancel()은 CancelledError를 발생시켜 risk_loop의 except 블록으로 빠지게 한다.
            self.session.background_task.cancel()
            try:
                await self.session.background_task
            except asyncio.CancelledError:
                pass
            self.session.background_task = None


def create_ssl_context() -> ssl.SSLContext:
    """WSS 서버에 사용할 TLS context를 만든다."""
    # PROTOCOL_TLS_SERVER는 서버용 TLS 설정을 만든다.
    # Android 앱은 wss:// 로 접속하므로 일반 ws:// 서버를 열면 연결에 실패한다.
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)

    # certfile은 앱에 보여줄 서버 인증서, keyfile은 그 인증서에 대응되는 개인키다.
    # 두 파일이 한 쌍이 아니면 서버 시작 또는 TLS handshake가 실패한다.
    context.load_cert_chain(certfile=CERT_FILE, keyfile=KEY_FILE)
    return context


def create_app() -> web.Application:
    """aiohttp 애플리케이션을 구성한다."""
    # 서버 객체는 세션 상태를 메모리에 들고 있다.
    # 프로세스를 재시작하면 세션 상태는 사라진다. 장기 저장이 필요하면 SQLite/파일 로그를 추가한다.
    server = SleepCarePiServer()
    app = web.Application()

    # Android 앱의 TXT record ws=/ws와 반드시 같아야 한다.
    # Avahi가 ws=/ws라고 광고했는데 여기 route가 /socket이면 앱은 연결하지 못한다.
    app.router.add_get(WS_PATH, server.websocket_handler)

    return app


def main() -> None:
    """서버를 시작한다."""
    # 1. TLS context를 먼저 만든다. cert/key 경로가 틀리면 여기서 바로 예외가 난다.
    ssl_context = create_ssl_context()

    # 2. /ws WebSocket route를 가진 aiohttp 앱을 만든다.
    app = create_app()

    print(f"[boot] SleepCare Pi WSS server listening on wss://0.0.0.0:{PORT}{WS_PATH}")

    # 3. 0.0.0.0:8765에서 WSS 서버를 시작한다.
    # 같은 포트를 Avahi 서비스 파일에서도 광고해야 Android 앱이 올바른 주소로 접속한다.
    web.run_app(app, host=HOST, port=PORT, ssl_context=ssl_context)


if __name__ == "__main__":
    main()
```

실행:

```bash
cd ~/sleepcare-pi
source .venv/bin/activate
python3 sleepcare_pi_server.py
```

성공하면 이런 로그가 나온다.

```text
[boot] SleepCare Pi WSS server listening on wss://0.0.0.0:8765/ws
```

## 6. Android 앱이 기대하는 메시지 형식

모든 메시지는 공통 envelope를 사용한다.

```json
{
  "v": 1,
  "t": "event.type",
  "sid": "study-2026-04-28-abcd1234",
  "seq": 1,
  "src": "pi",
  "sent_at_ms": 1777370000000,
  "ack_required": false,
  "body": {}
}
```

필드 의미:

```text
v
  프로토콜 버전. 현재 1.

t
  메시지 타입. 예: hello_ack, session.ack, risk.update.

sid
  세션 ID. hello/hello_ack처럼 세션 전 메시지는 null이어도 된다.

seq
  송신자 기준 증가 번호. Pi가 보내는 메시지는 Pi 내부에서 증가시키면 된다.

src
  송신자. Pi 서버는 "pi"를 사용한다.

sent_at_ms
  송신 시각. epoch millisecond.

ack_required
  수신자 ACK가 필요한지 여부. 현재 앱은 일반 ack를 완전히 구현하지 않았지만 필드는 유지한다.

body
  메시지별 실제 payload.
```

## 7. 연결 흐름

앱과 Pi는 다음 순서로 동작해야 한다.

```text
1. Pi
   Avahi로 _sleepcare._tcp 광고
   WSS 서버를 8765 포트 /ws path로 실행

2. Android 앱
   _sleepcare._tcp 탐색
   TXT record 검사: proto=v1, tls=1, ws=/ws
   wss://<pi-ip>:8765/ws 연결

3. Android -> Pi
   hello

4. Pi -> Android
   hello_ack

5. Android -> Pi
   session.open

6. Pi -> Android
   session.ack

7. 세션 중
   Android -> Pi: hr.ingest
   Pi -> Android: risk.update
   Pi -> Android: alert.fire

8. Android -> Pi
   session.close

9. Pi -> Android
   session.summary
```

## 8. Pi가 반드시 구현해야 하는 이벤트

### 8.1 hello 수신, hello_ack 송신

앱이 WSS 연결 직후 `hello`를 보낸다. Pi는 6초 안에 `hello_ack`를 보내야 앱이 연결 성공으로 판단한다.

```json
{
  "v": 1,
  "t": "hello_ack",
  "sid": null,
  "seq": 0,
  "src": "pi",
  "sent_at_ms": 1777370000000,
  "ack_required": false,
  "body": {
    "device_id": "deskpi-a1",
    "proto": "v1",
    "mode": "eye-only"
  }
}
```

### 8.2 session.open 수신, session.ack 송신

앱이 학습 세션 시작을 요청하면 Pi는 카메라 준비를 시작하고 `session.ack`를 보낸다.

```json
{
  "v": 1,
  "t": "session.ack",
  "sid": "study-2026-04-28-abcd1234",
  "seq": 1,
  "src": "pi",
  "sent_at_ms": 1777370001000,
  "ack_required": false,
  "body": {
    "accepted": true,
    "mode": "eye-only",
    "camera_ready": true
  }
}
```

앱은 6초 안에 `session.ack`가 오면 세션 시작 성공으로 처리한다.

### 8.3 risk.update 송신

Pi는 세션 중 위험도를 주기적으로 보낸다.

```json
{
  "v": 1,
  "t": "risk.update",
  "sid": "study-2026-04-28-abcd1234",
  "seq": 2,
  "src": "pi",
  "sent_at_ms": 1777370002000,
  "ack_required": false,
  "body": {
    "mode": "eye-only",
    "eye_score": 0.72,
    "hr_score": null,
    "fused_score": 0.72,
    "state": "SUSPECT",
    "recommended_flush_sec": 5
  }
}
```

`state`는 우선 다음 값을 사용한다.

```text
BASELINE
  정상 감시 상태.

SUSPECT
  졸음 의심 상태. 앱은 UI를 주의 상태로 바꾸고 워치 flush 주기를 줄일 수 있다.

ALERTING
  즉시 경고가 필요한 상태.
```

### 8.4 alert.fire 송신

Pi가 실제 경고가 필요하다고 판단하면 `alert.fire`를 보낸다. 앱은 이 이벤트를 졸음 이벤트로 저장하고, 워치가 연결된 경우 진동 명령을 보낸다.

```json
{
  "v": 1,
  "t": "alert.fire",
  "sid": "study-2026-04-28-abcd1234",
  "seq": 3,
  "src": "pi",
  "sent_at_ms": 1777370003000,
  "ack_required": true,
  "body": {
    "level": 2,
    "reason": "eye_closed_persistent",
    "duration_ms": 5000
  }
}
```

`alert.fire`는 너무 자주 보내면 앱 이벤트가 많이 쌓인다. 실제 구현에서는 같은 경고가 반복될 때 20초 정도 cooldown을 두는 것을 권장한다.

### 8.5 session.close 수신, session.summary 송신

앱이 세션 종료를 요청하면 Pi는 카메라/판단 루프를 멈추고 요약을 보낸다.

```json
{
  "v": 1,
  "t": "session.summary",
  "sid": "study-2026-04-28-abcd1234",
  "seq": 4,
  "src": "pi",
  "sent_at_ms": 1777370100000,
  "ack_required": false,
  "body": {
    "final_state": "BASELINE",
    "total_alerts": 1,
    "peak_fused_score": 0.86,
    "mode": "eye-only",
    "summary_reason": "user_stop"
  }
}
```

앱은 `session.summary`를 받으면 세션 기록을 저장하고 현재 위험도 표시를 초기화한다.

## 9. Android 앱에서 Pi를 인정하는 조건

현재 Android 앱 구현 기준으로 Pi 발견 조건은 다음과 같다.

```text
service type == _sleepcare._tcp
TXT proto == v1
TXT tls == 1
TXT ws 값이 있으면 그 path 사용, 없으면 /ws
host와 port가 resolve되어야 함
```

따라서 Avahi 설정에서 최소한 아래 TXT record는 반드시 필요하다.

```text
proto=v1
tls=1
ws=/ws
device_id=deskpi-a1
```

## 10. 로컬 테스트 방법

### 10.1 Pi에서 서비스 광고 확인

```bash
avahi-browse -rt _sleepcare._tcp
```

### 10.2 Pi에서 WSS 서버 포트 확인

```bash
ss -ltnp | grep 8765
```

### 10.3 다른 Linux/WSL 장비에서 TLS 연결 확인

Pi IP가 `192.168.0.25`라면:

```bash
openssl s_client -connect 192.168.0.25:8765 -servername SleepCare-Pi
```

self-signed 인증서라 검증 경고가 나올 수 있지만, 인증서 subject가 `CN=SleepCare Pi Dev`로 보이면 서버가 인증서를 내보내는 것이다.

### 10.4 Android 앱에서 확인

1. Android 기기와 Pi를 같은 Wi-Fi에 연결한다.
2. Pi에서 `python3 sleepcare_pi_server.py`를 실행한다.
3. Android 앱의 기기 연결 화면에서 로컬 Pi 재검색을 누른다.
4. Pi 서버 로그에 `[ws] Android client connected`가 나오는지 확인한다.
5. 앱에서 공부 세션을 시작한다.
6. Pi 서버 로그에 `session.open`, `session.ack`, `risk.update` 흐름이 보이는지 확인한다.

## 11. 자주 나는 문제

### 앱이 Pi를 못 찾음

확인할 것:

```text
Android와 Pi가 같은 Wi-Fi인가?
Avahi가 실행 중인가?
_sleepcare._tcp로 광고 중인가?
proto=v1, tls=1 TXT record가 들어가 있는가?
공유기에서 mDNS/multicast를 막고 있지 않은가?
```

명령:

```bash
systemctl status avahi-daemon
avahi-browse -rt _sleepcare._tcp
```

### 앱이 찾기는 하는데 연결 실패

확인할 것:

```text
WSS 서버가 0.0.0.0:8765로 떠 있는가?
Avahi port와 서버 port가 같은가?
TXT ws=/ws와 서버 route /ws가 같은가?
Pi 방화벽이 포트를 막고 있지 않은가?
```

명령:

```bash
ss -ltnp | grep 8765
```

### TLS handshake 실패

확인할 것:

```text
Pi 서버가 sleepcare_pi_dev_cert.pem과 sleepcare_pi_dev_key.pem을 같이 쓰고 있는가?
Android 앱에 들어간 cert가 Pi 서버 cert와 같은 파일인가?
cert와 key가 같은 쌍인가?
```

검증:

```bash
openssl x509 -noout -modulus -in sleepcare_pi_dev_cert.pem | openssl md5
openssl rsa  -noout -modulus -in sleepcare_pi_dev_key.pem  | openssl md5
```

두 md5 값이 같아야 한다.

### hello_ack 이후 세션 시작 실패

확인할 것:

```text
session.open을 받았을 때 6초 안에 session.ack를 보내는가?
session.ack의 t 값이 정확히 "session.ack"인가?
session.ack의 sid가 앱이 보낸 sid와 같은가?
JSON envelope 형식이 깨지지 않았는가?
```

## 12. 실제 졸음 감지 로직을 붙이는 위치

예제 코드에서 실제 모델을 붙일 위치는 `calculate_risk()`다.

권장 구조:

```text
camera_loop
  카메라 프레임 읽기
  얼굴/눈/고개 특징 추출
  eye_score 계산

hr_ingest
  앱에서 받은 심박/IBI 샘플 저장
  hr_quality에 따라 보조 가중치 계산

calculate_risk
  eye_score를 주 신호로 사용
  hr_score는 보조 신호로 사용
  BASELINE/SUSPECT/ALERTING 상태 결정

risk_loop
  risk.update 주기 송신
  ALERTING 진입 시 alert.fire 송신
  Pi 로컬 부저/LED/LCD 경고 출력
```

초기 MVP는 eye-only로도 정상 동작해야 한다. 워치/심박 데이터가 없어도 Pi는 카메라 기반으로 `risk.update`와 `alert.fire`를 보낼 수 있어야 한다.

## 13. 운영 시 주의할 점

개인키는 Pi 서버 밖으로 노출하지 않는다.

```text
절대 하면 안 되는 것:
- sleepcare_pi_dev_key.pem을 Android 앱 리소스에 넣기
- sleepcare_pi_dev_key.pem을 공개 Git 저장소에 커밋하기
- 채팅방/문서/노션에 개인키 내용을 그대로 붙여넣기
```

개발용 self-signed 인증서는 MVP 검증용이다. 실제 배포 단계에서는 다음 중 하나로 강화하는 것을 검토한다.

```text
기기 등록 시 cert pinning
Pi별 인증서 발급
QR 코드 기반 최초 등록
hostname/SAN 검증 활성화
개인키 교체/폐기 절차 마련
```

현재 Android 앱은 개발 편의를 위해 hostname verifier를 완화해 둔 상태다. 그래서 보안의 핵심은 “앱에 들어간 cert와 Pi 서버 cert가 같은가”에 있다.

## 14. Pi 개발자 완료 기준

Pi 개발자는 아래 항목을 완료하면 모바일 앱과 1차 통합 검증이 가능하다.

```text
[ ] sleepcare_pi_dev_cert.pem + sleepcare_pi_dev_key.pem을 Pi 서버 디렉터리에 배치
[ ] key 파일 권한을 600으로 제한
[ ] Avahi로 _sleepcare._tcp 서비스 광고
[ ] TXT record에 proto=v1, tls=1, ws=/ws, device_id 설정
[ ] WSS 서버를 0.0.0.0:8765/ws로 실행
[ ] hello 수신 시 hello_ack 송신
[ ] session.open 수신 시 session.ack 송신
[ ] 세션 중 risk.update 주기 송신
[ ] 경고 조건에서 alert.fire 송신
[ ] session.close 수신 시 session.summary 송신
[ ] Android 앱 기기 연결 화면에서 Pi가 연결됨으로 표시되는지 확인
[ ] 공부 세션 시작/종료가 앱과 Pi 로그에서 모두 확인되는지 검증
```
