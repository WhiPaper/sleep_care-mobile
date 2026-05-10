# SleepCare Pi QR Pairing 구현 가이드

이 문서는 라즈베리파이 개발자가 SleepCare 모바일 앱과 맞춰 구현해야 하는 QR 기반 Pi 신뢰 등록 규격을 정리한다. 목표는 사용자가 Pi 화면의 QR을 한 번 스캔하면, 이후 앱이 같은 로컬 Wi-Fi 안에서 Pi를 자동 발견하고 `WSS`로 안전하게 연결하는 것이다.

최종 구조는 다음과 같다.

```text
Raspberry Pi
  -> TLS 인증서 + 개인키로 WSS 서버 실행
  -> 인증서 public key의 SPKI SHA-256 fingerprint 계산
  -> fingerprint와 device_id/ws 정보를 QR로 표시
  -> Avahi로 _sleepcare._tcp 서비스 광고

Android 앱
  -> QR payload를 스캔해 trusted Pi 정보 저장
  -> NSD/DNS-SD로 _sleepcare._tcp 서비스 발견
  -> TXT record의 device_id/ws가 QR 등록값과 같은지 확인
  -> wss://<pi-ip>:<port>/<ws-path> 연결
  -> 서버 인증서의 SPKI SHA-256이 QR 등록값과 같은지 확인
  -> hello/session.open/risk.update/session.close 프로토콜 사용
```

## 1. QR pairing이 해결하는 문제

개발용 WSS 연결에서는 Android 앱에 특정 인증서를 고정 포함하는 방식도 가능하다. 하지만 실제 Pi가 여러 대가 되거나, 사용자가 자기 Pi를 등록해야 하는 상황에서는 앱 빌드 시점에 모든 Pi 인증서를 넣을 수 없다.

QR pairing은 이 문제를 다음 방식으로 푼다.

```text
사용자가 직접 보고 스캔한 QR
  = 이 Pi를 내 앱에 등록하겠다는 최초 신뢰 행위

QR 안의 spki_sha256
  = 이 Pi WSS 서버가 제시해야 하는 인증서 public key의 fingerprint

WSS 연결 시 SPKI pin 검증
  = 지금 접속한 서버가 QR로 등록한 Pi와 같은 key pair를 쓰는지 확인
```

중요한 점은 QR payload가 인증서를 새로 만들지 않는다는 것이다. Pi는 이미 TLS 인증서와 개인키를 가지고 있어야 한다. QR에는 인증서 전체도, 개인키도, 공개키 원문도 넣지 않는다. QR에는 **인증서 public key에서 계산한 짧은 신뢰 지문**인 `spki_sha256`만 넣는다.

## 2. Pi 개발자가 준비해야 하는 것

Pi 쪽에는 다음 구성요소가 필요하다.

```text
pi_cert.pem
  WSS 서버가 Android 앱에 제시할 서버 인증서다.
  self-signed 인증서여도 된다. 앱은 일반 CA 신뢰가 아니라 QR로 등록한 SPKI pin을 기준으로 검증한다.

pi_key.pem
  인증서 public key에 대응되는 private key다.
  Pi 서버 밖으로 노출하면 안 된다.

device_id
  QR payload와 Avahi TXT record에 같은 값으로 들어가는 Pi 식별자다.
  예: deskpi-a1, lab-pi-03

ws path
  앱이 접속할 WebSocket path다.
  현재 기본값은 /ws다.

Avahi/DNS-SD service
  Android 앱이 로컬 네트워크에서 Pi를 찾을 수 있도록 _sleepcare._tcp를 광고한다.
```

인증서와 개인키가 한 쌍인지 먼저 확인한다.

```bash
openssl x509 -noout -modulus -in pi_cert.pem | openssl md5
openssl rsa  -noout -modulus -in pi_key.pem  | openssl md5
```

두 출력값이 같아야 한다. 다르면 WSS 서버는 해당 인증서의 소유자임을 TLS handshake에서 증명할 수 없다.

## 3. QR payload 형식

QR 내용은 UTF-8 JSON 문자열이다. URL이나 바이너리 포맷이 아니라, 앱이 그대로 JSON으로 파싱할 수 있는 문자열을 QR 이미지로 만든다.

```json
{
  "proto": "sleepcare-pair-v1",
  "device_id": "deskpi-a1",
  "display_name": "SleepCare Pi Desk",
  "service": "_sleepcare._tcp",
  "ws": "/ws",
  "tls": 1,
  "spki_sha256": "BASE64_SHA256_OF_DER_SPKI",
  "issued_at_ms": 1775578000000,
  "key_id": "deskpi-a1-2026-04",
  "pin_hint": "ab12...9Z"
}
```

필수 필드는 다음과 같다.

| 필드 | 값 | 앱 검증 기준 |
|---|---|---|
| `proto` | `sleepcare-pair-v1` | 다른 값이면 등록 거부 |
| `device_id` | Pi를 안정적으로 식별하는 문자열 | NSD TXT record의 `device_id`와 같아야 함 |
| `service` | `_sleepcare._tcp` | 다른 서비스 타입이면 등록 거부 |
| `ws` | WebSocket path, 예: `/ws` | NSD TXT record의 `ws`와 같아야 함 |
| `tls` | `1` | TLS 없는 연결은 등록 거부 |
| `spki_sha256` | DER-encoded SPKI bytes의 SHA-256 Base64 | Base64 decode 결과가 32 bytes여야 함 |

선택 필드는 다음과 같다.

| 필드 | 용도 |
|---|---|
| `display_name` | 앱 UI에 보여줄 이름. 없으면 `device_id`를 표시한다. |
| `issued_at_ms` | QR 생성 시각. Unix epoch milliseconds. |
| `key_id` | 어떤 키쌍/인증서로 발급한 QR인지 운영자가 추적하기 위한 값. |
| `pin_hint` | 사용자가 육안으로 확인할 짧은 fingerprint 힌트. 보안 검증에는 직접 쓰지 않는다. |

현재 Android 앱은 `device_id`, `display_name`, `service`, `ws`, `spki_sha256`, `registered_at_ms`를 로컬 trusted Pi 정보로 저장한다.

## 4. SPKI SHA-256의 정체

`spki_sha256`은 인증서 파일 전체의 fingerprint가 아니다. 인증서 안에 들어 있는 public key의 `SubjectPublicKeyInfo` 값을 DER로 인코딩한 뒤 SHA-256을 계산하고 Base64로 표현한 값이다.

앱이 인증서 전체 fingerprint가 아니라 SPKI fingerprint를 쓰는 이유는 다음과 같다.

```text
같은 key pair로 인증서만 재발급
  -> public key는 같음
  -> SPKI fingerprint도 같음
  -> 앱 재등록 불필요

key pair까지 교체
  -> public key가 바뀜
  -> SPKI fingerprint도 바뀜
  -> 앱에서 QR 재등록 필요
```

즉 `spki_sha256`은 "이 Pi가 어떤 공개키를 가진 서버여야 하는가"를 기록하는 pin이다. TLS handshake에서는 서버가 해당 공개키에 대응되는 private key를 가지고 있음을 증명한다.

## 5. OpenSSL로 SPKI fingerprint 만들기

Pi 서버 인증서가 `pi_cert.pem`이라고 할 때 다음 명령으로 QR에 넣을 `spki_sha256`을 만들 수 있다.

```bash
openssl x509 -in pi_cert.pem -pubkey -noout \
  | openssl pkey -pubin -outform DER \
  | openssl dgst -sha256 -binary \
  | openssl base64
```

출력 예시는 다음처럼 보인다.

```text
9Qq2y8pR5wL3N2pM7eU8tYwY7n3c2iV3yxr6v8vKf9E=
```

이 문자열 전체를 QR payload의 `spki_sha256`에 넣는다. 앞뒤 공백이나 줄바꿈이 들어가지 않게 주의한다.

짧은 표시용 힌트가 필요하면 앞뒤 일부만 잘라 `pin_hint`에 넣을 수 있다.

```bash
PIN="$(openssl x509 -in pi_cert.pem -pubkey -noout \
  | openssl pkey -pubin -outform DER \
  | openssl dgst -sha256 -binary \
  | openssl base64)"

echo "${PIN:0:10}...${PIN: -6}"
```

`pin_hint`는 사람이 눈으로 확인하기 위한 보조 정보일 뿐이다. 앱의 실제 보안 판단은 전체 `spki_sha256`으로 한다.

## 6. Python으로 QR payload 생성하기

Pi 설정 화면이나 부팅 스크립트에서 payload를 만들려면 Python으로 생성해도 된다.

필요 패키지를 설치한다.

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install cryptography qrcode[pil]
```

다음 예시는 인증서에서 SPKI fingerprint를 계산하고 QR PNG까지 만든다.

```python
import base64
import hashlib
import json
import time
from pathlib import Path

import qrcode
from cryptography import x509
from cryptography.hazmat.primitives import serialization

# Pi 서버가 TLS/WSS에 실제로 연결할 인증서다.
# QR fingerprint도 반드시 이 인증서에서 계산해야 앱의 pin 검증과 맞는다.
CERT_FILE = Path("pi_cert.pem")

# device_id와 ws는 Avahi TXT record에도 같은 값으로 광고해야 한다.
# 앱은 QR 등록값과 NSD 발견값이 다르면 다른 Pi로 보고 연결하지 않는다.
DEVICE_ID = "deskpi-a1"
DISPLAY_NAME = "SleepCare Pi Desk"
WS_PATH = "/ws"


def certificate_spki_sha256(cert_path: Path) -> str:
    # 인증서 전체 fingerprint가 아니라 public key의 SPKI fingerprint를 만든다.
    # 같은 key pair로 인증서만 재발급하면 이 값은 유지되므로 앱 재등록을 줄일 수 있다.
    cert = x509.load_pem_x509_certificate(cert_path.read_bytes())
    spki = cert.public_key().public_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    return base64.b64encode(hashlib.sha256(spki).digest()).decode("ascii")


spki_sha256 = certificate_spki_sha256(CERT_FILE)
payload = {
    # proto/service/tls는 앱이 QR 종류와 보안 조건을 확인하는 필수 값이다.
    "proto": "sleepcare-pair-v1",
    "device_id": DEVICE_ID,
    "display_name": DISPLAY_NAME,
    "service": "_sleepcare._tcp",
    "ws": WS_PATH,
    "tls": 1,
    "spki_sha256": spki_sha256,
    "issued_at_ms": int(time.time() * 1000),
    # key_id는 키 교체 이력을 사람이 추적하기 위한 값이고, 앱의 보안 판단은 spki_sha256으로 한다.
    "key_id": f"{DEVICE_ID}-current",
    "pin_hint": f"{spki_sha256[:10]}...{spki_sha256[-6:]}",
}

# QR에는 private key나 인증서 원문을 넣지 않는다.
# 앱은 이 JSON을 최초 신뢰 정보로 저장한 뒤, WSS 연결 시 서버 인증서의 SPKI와 대조한다.
raw_payload = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
print(raw_payload)

image = qrcode.make(raw_payload)
image.save("sleepcare_pi_pairing_qr.png")
```

Android 앱은 JSON 문자열 자체를 스캔한다. QR을 URL로 감싸거나 Base64로 다시 감싸지 않는다.

## 7. Avahi TXT record와 QR 값 맞추기

QR 등록이 성공해도 Android 앱은 아무 `_sleepcare._tcp` 서버에나 연결하지 않는다. NSD로 발견한 서비스의 TXT record가 QR 등록값과 맞아야 한다.

Avahi 서비스 예시는 다음과 같다.

```xml
<?xml version="1.0" standalone="no"?>
<!DOCTYPE service-group SYSTEM "avahi-service.dtd">
<service-group>
  <name replace-wildcards="yes">SleepCare-Pi-%h</name>

  <service>
    <type>_sleepcare._tcp</type>
    <port>8765</port>

    <txt-record>proto=v1</txt-record>
    <txt-record>tls=1</txt-record>
    <txt-record>ws=/ws</txt-record>
    <txt-record>device_id=deskpi-a1</txt-record>
    <txt-record>cam=1</txt-record>
  </service>
</service-group>
```

다음 값은 반드시 일치해야 한다.

| QR payload | Avahi TXT record | 설명 |
|---|---|---|
| `device_id` | `device_id` | 다르면 앱이 등록된 Pi가 아니라고 판단한다. |
| `ws` | `ws` | 다르면 앱이 잘못된 WebSocket endpoint로 보고 연결하지 않는다. |
| `service` | service type | 현재는 `_sleepcare._tcp`만 지원한다. |
| `tls=1` | `tls=1` | TLS 없는 Pi는 연결 대상에서 제외한다. |

Avahi 광고를 확인한다.

```bash
sudo systemctl restart avahi-daemon
avahi-browse -rt _sleepcare._tcp
```

출력에서 `device_id`, `ws`, `tls`, `proto`가 예상대로 보이는지 확인한다.

## 8. WSS 서버가 지켜야 할 조건

Pi WSS 서버는 QR의 `spki_sha256`을 만들 때 사용한 인증서와 같은 key pair의 인증서를 TLS에 연결해야 한다.

Python `ssl` 설정 예시는 다음과 같다.

```python
import ssl
from aiohttp import web

CERT_FILE = "pi_cert.pem"
KEY_FILE = "pi_key.pem"

context = ssl.create_default_context(ssl.Purpose.CLIENT_AUTH)
# QR의 spki_sha256을 계산할 때 사용한 인증서와 같은 key pair를 반드시 연결한다.
# 다른 인증서나 개인키를 쓰면 Android 앱의 SPKI pin 검증에서 연결이 차단된다.
context.load_cert_chain(certfile=CERT_FILE, keyfile=KEY_FILE)

app = web.Application()


async def websocket_handler(request: web.Request) -> web.WebSocketResponse:
    # heartbeat는 죽은 연결을 빨리 감지하기 위한 WebSocket ping/pong 보조 장치다.
    # Android 앱도 주기적으로 ping을 쓰므로 양쪽 모두 연결 끊김을 빠르게 알 수 있다.
    ws = web.WebSocketResponse(heartbeat=20)
    await ws.prepare(request)
    # Android 앱은 TLS pin 검증이 끝난 뒤 hello를 보내고 hello_ack를 기다린다.
    # 이 응답이 없으면 인증은 성공했더라도 앱에서는 Pi 준비 실패로 처리된다.
    await ws.send_str('{"v":1,"t":"hello_ack","sid":null,"seq":1,"src":"pi","sent_at_ms":0,"ack_required":false,"body":{"device_id":"deskpi-a1","proto":"sleepcare-pi-v1","mode":"eye-only"}}')
    async for message in ws:
        pass
    return ws


app.router.add_get("/ws", websocket_handler)
web.run_app(app, host="0.0.0.0", port=8765, ssl_context=context)
```

실제 서버 코드는 `hello`, `session.open`, `risk.update`, `alert.fire`, `session.close`, `session.summary`를 구현해야 한다. QR pairing 문서의 범위는 서버 인증과 등록 흐름이며, 메시지 프로토콜 세부 사항은 `sleepcare_protocol_design.md`를 따른다.

## 9. Android 앱의 실제 검증 흐름

앱은 QR 등록과 WSS 연결에서 서로 다른 검증을 한다.

QR 스캔 시점:

```text
1. QR 문자열이 JSON인지 확인
2. proto == sleepcare-pair-v1 확인
3. service == _sleepcare._tcp 확인
4. tls == 1 확인
5. spki_sha256이 Base64이고 decode 결과가 32 bytes인지 확인
6. TrustedPiDevice로 저장
```

Pi 연결 시점:

```text
1. 저장된 TrustedPiDevice가 있는지 확인
2. NSD로 _sleepcare._tcp 검색
3. TXT record의 proto=v1, tls=1 확인
4. TXT record의 device_id가 QR 등록값과 같은지 확인
5. TXT record의 ws가 QR 등록값과 같은지 확인
6. wss://host:port/ws 연결 시작
7. TLS handshake에서 서버 leaf certificate의 SPKI SHA-256 계산
8. 계산값이 QR 등록값과 같으면 연결 허용
9. hello 전송 후 hello_ack 수신 대기
```

앱은 로컬 네트워크 특성상 hostname 검증은 완화한다. 대신 보안의 핵심 조건을 SPKI pin 일치로 둔다. 따라서 IP 주소나 mDNS 이름이 바뀌어도, Pi가 같은 key pair를 쓰고 있으면 연결할 수 있다.

## 10. 오류 처리 기준

| 상황 | 원인 | 앱/Pi에서 할 일 |
|---|---|---|
| QR JSON 파싱 실패 | QR 내용이 JSON이 아님 | QR 생성 코드가 JSON 문자열을 그대로 넣는지 확인 |
| `proto` 불일치 | 앱이 모르는 pairing 버전 | `sleepcare-pair-v1`로 생성 |
| `service` 불일치 | 다른 서비스 타입 | `_sleepcare._tcp` 사용 |
| `tls != 1` | 보안 연결 조건 불충족 | WSS/TLS 서버로 구현 |
| `spki_sha256` Base64 오류 | 잘못 잘린 fingerprint 또는 공백 포함 | 32 bytes SHA-256 결과를 Base64로 넣었는지 확인 |
| QR 등록은 됐지만 Pi 미발견 | Avahi 광고 없음, 같은 Wi-Fi 아님 | `avahi-browse -rt _sleepcare._tcp` 확인 |
| 등록된 Pi가 아니라고 나옴 | QR `device_id`와 TXT `device_id` 불일치 | 두 값을 같게 맞춤 |
| WSS 연결 실패 | 포트, 방화벽, 서버 미실행 | `openssl s_client`, 서버 로그 확인 |
| SPKI pin 불일치 | 서버 인증서가 QR 생성 때 쓴 인증서/key와 다름 | QR 재생성 또는 서버 cert/key 교체 확인 |
| hello_ack timeout | TLS는 성공했지만 앱 프로토콜 응답 없음 | WSS handler가 `hello` 수신 후 `hello_ack`를 보내는지 확인 |

## 11. 디버깅 명령 모음

인증서 내용을 본다.

```bash
openssl x509 -in pi_cert.pem -subject -issuer -dates -noout
```

인증서와 개인키가 한 쌍인지 확인한다.

```bash
openssl x509 -noout -modulus -in pi_cert.pem | openssl md5
openssl rsa  -noout -modulus -in pi_key.pem  | openssl md5
```

QR에 넣은 SPKI pin을 다시 계산한다.

```bash
openssl x509 -in pi_cert.pem -pubkey -noout \
  | openssl pkey -pubin -outform DER \
  | openssl dgst -sha256 -binary \
  | openssl base64
```

Avahi 광고를 본다.

```bash
avahi-browse -rt _sleepcare._tcp
```

Pi WSS 포트가 열려 있는지 본다.

```bash
ss -lntp | grep 8765
```

TLS 인증서가 실제로 제시되는지 본다.

```bash
openssl s_client -connect 127.0.0.1:8765 -showcerts
```

다른 PC에서 Pi IP로 확인할 때는 다음처럼 실행한다.

```bash
openssl s_client -connect <pi-ip>:8765 -showcerts
```

이 명령은 self-signed 인증서라서 일반 CA 검증 오류를 보여줄 수 있다. QR pairing 방식에서는 그 자체가 문제가 아니다. 중요한 것은 서버가 제시한 인증서의 SPKI fingerprint가 QR의 `spki_sha256`과 같은지다.

## 12. 키 교체 운영

인증서만 재발급하고 같은 private key를 유지하면 SPKI pin은 유지된다. 이 경우 앱에서 QR 재등록이 필요하지 않다.

key pair를 새로 만들면 public key가 바뀌므로 `spki_sha256`도 바뀐다. 이 경우 반드시 Pi 화면에 새 QR을 표시하고, 앱에서 기존 Pi 등록을 지운 뒤 다시 등록해야 한다.

운영에서는 다음 규칙을 권장한다.

```text
device_id
  Pi 장비 단위로 안정적으로 유지한다.

key_id
  키쌍이 바뀔 때마다 바꾼다.
  예: deskpi-a1-2026-04, deskpi-a1-2026-08

pin_hint
  사용자/개발자가 화면에서 확인하기 쉽게 짧게 표시한다.
  실제 검증에는 전체 spki_sha256만 사용한다.
```

키쌍이 바뀌었는데 QR을 갱신하지 않으면 앱은 SPKI pin 불일치로 연결을 차단한다. 이는 정상적인 보안 동작이다.

## 13. 구현 체크리스트

Pi 쪽 체크리스트:

- [ ] WSS 서버가 사용할 `pi_cert.pem`과 `pi_key.pem`이 같은 key pair인지 확인한다.
- [ ] 인증서 public key에서 `spki_sha256`을 계산한다.
- [ ] `sleepcare-pair-v1` JSON payload를 생성한다.
- [ ] QR에는 JSON 문자열 자체를 넣는다.
- [ ] QR `device_id`와 Avahi TXT `device_id`를 같게 둔다.
- [ ] QR `ws`와 Avahi TXT `ws`를 같게 둔다.
- [ ] Avahi가 `_sleepcare._tcp`, `proto=v1`, `tls=1`을 광고한다.
- [ ] WSS 서버가 QR 생성에 사용한 인증서와 같은 key pair를 사용한다.
- [ ] 앱 연결 후 `hello`에 대해 `hello_ack`를 응답한다.
- [ ] key pair 교체 시 QR 재등록이 필요하다는 안내를 Pi UI에 표시한다.

앱 쪽 기대 동작:

- [ ] QR payload가 유효하면 trusted Pi로 저장한다.
- [ ] QR 등록 전에는 Pi 연결 시 등록 안내를 보여준다.
- [ ] 등록된 `device_id`와 다른 Pi는 발견되어도 연결하지 않는다.
- [ ] SPKI pin이 다르면 보안 오류로 연결을 차단한다.
- [ ] SPKI pin이 같으면 `hello`/`hello_ack` 후 공부 세션을 열 수 있다.

> 참고: QR, Avahi, WSS, hello, session 흐름을 앱 개발자 도움 없이 단계별로 확인하려면 [Pi 개발자 디버그 가이드](./pi-developer-debug-guide.md)를 먼저 따라간다. QR 인식이 안 되는 초기 Pi 구현도 앱 개발자 모드의 직접 endpoint 테스트로 진단할 수 있다.
