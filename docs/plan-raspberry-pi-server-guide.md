# Raspberry Pi WSS 서버 구현 가이드

이 문서는 Sleep Care 모바일 앱과 라즈베리파이 서버를 실제로 연결하기 위해 Pi 개발자가 해야 할 일을 정리한다. 모바일 앱은 같은 로컬 Wi-Fi 안에서 Pi를 자동 발견한 뒤, TLS가 적용된 WebSocket인 `WSS`로 연결한다.

최종 목표는 다음 구조다.

```text
Android 앱
  -> QR 스캔으로 Pi의 SPKI(Public Key 지문) 등록 (최초 1회)
  -> NSD/DNS-SD로 _sleepcare._tcp 서비스 발견
  -> TXT record에서 proto/tls/ws/device_id 확인
  -> wss://<pi-ip>:<port>/<ws-path> 연결
  -> 서버 인증서의 SPKI 지문 검증 (Pinning)
  -> hello/session.open/hr.ingest/session.close 송신

Raspberry Pi
  -> Avahi로 _sleepcare._tcp 서비스 광고
  -> WSS 서버 실행 (TLS 적용)
  -> QR 코드로 등록 정보(SPKI 포함) 제공
  -> hello_ack/session.ack/risk.update/alert.fire/session.summary 응답
  -> 카메라 기반 졸음 감지 및 로컬 경고 출력
```

## 1. Pi 개발자가 준비해야 하는 파일

Android 앱과 안전하게 통신하기 위해 Pi 서버는 TLS 인증서 쌍이 필요하다.

```text
pi_cert.pem
  서버 인증서다. Pi 서버가 Android 앱에 자신을 증명할 때 보여준다.
  운영 환경에서는 QR로 등록된 지문을 우선 신뢰한다.

pi_key.pem
  서버 개인키다. Pi 서버가 TLS/WSS를 열 때 사용한다.
  Android 앱이나 공개 저장소, 문서 첨부에 절대 넣으면 안 된다.
```

중요한 점은 cert와 key가 반드시 같은 쌍이어야 한다는 것이다. Pi 서버는 `pi_cert.pem`에 대응되는 `pi_key.pem`으로 WSS를 띄워야 하며, 이 인증서의 Public Key 지문을 QR에 담아 앱에 전달해야 한다. 상세한 QR 생성 규격은 [pi-qr-pairing.md](./pi-qr-pairing.md)를 참조한다.

## 2. 사용하는 도구

Pi에서는 다음 도구를 사용한다.

```text
OpenSSL
  인증서 확인, 지문(SPKI) 계산, TLS 문제 디버깅에 사용한다.

Avahi
  Linux/Raspberry Pi에서 mDNS/DNS-SD 서비스를 광고하는 데 사용한다.
  Android 앱은 이 광고를 NSD로 발견한다.

libwebsockets (LWS)
  C/C++ 기반의 경량 WebSocket 라이브러리다. 
  TLS(OpenSSL/mbedTLS)를 지원하며 WSS 서버를 구현하는 데 사용한다.
```

설치 명령 (Raspberry Pi OS 기준):

```bash
sudo apt update
sudo apt install -y libwebsockets-dev libssl-dev openssl avahi-daemon avahi-utils
```

## 3. 인증서 파일 배치 및 권한

Pi 서버 디렉터리에 파일을 두고 권한을 제한한다.

```bash
chmod 600 ~/sleepcare-pi/pi_key.pem
chmod 644 ~/sleepcare-pi/pi_cert.pem
```

인증서와 개인키가 한 쌍인지 확인하는 법:

```bash
openssl x509 -noout -modulus -in pi_cert.pem | openssl md5
openssl rsa  -noout -modulus -in pi_key.pem  | openssl md5
```

두 출력값이 같아야 한다. 다르면 TLS 연결 시 `Handshake failure`가 발생한다.

## 4. Avahi로 Pi 서비스 광고하기

Android 앱은 `_sleepcare._tcp` 서비스를 찾는다. `/etc/avahi/services/sleepcare.service` 파일을 생성한다.

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
  </service>
</service-group>
```

**중요**: `device_id`와 `ws` 경로는 이후 설명할 QR 코드의 내용과 반드시 일치해야 한다.

## 5. Pi WSS 서버 구현 가이드 (libwebsockets)

`libwebsockets`를 사용하여 WSS 서버를 구성할 때 주의할 설정은 다음과 같다.

### 5.1 TLS 및 프로토콜 설정
- `struct lws_context_creation_info` 설정 시 `options`에 `LWS_SERVER_OPTION_DO_SSL_GLOBAL_INIT`를 포함한다.
- `ssl_cert_filepath`에 `pi_cert.pem`, `ssl_private_key_filepath`에 `pi_key.pem` 경로를 지정한다.
- 핸드셰이크 시 `vhost` 내의 `mount` 경로를 Avahi에 설정한 `ws` 경로(예: `/ws`)와 일치시킨다.

### 5.2 메시지 처리 (JSON)
- 앱에서 오는 메시지는 UTF-8 JSON 문자열이다.
- `LWS_CALLBACK_RECEIVE`에서 받은 데이터를 JSON 파서(예: `cJSON`, `nlohmann/json`)로 파싱한다.
- `t` 필드에 따라 분기하여 처리한다.

### 5.3 주요 응답 예시
서버는 각 요청에 대해 다음과 같은 구조의 JSON을 전송해야 한다. (LWS의 `lws_write` 사용)

**hello_ack (hello 수신 시)**
```json
{
  "v": 1, "t": "hello_ack", "sid": null, "seq": 0, "src": "pi", "sent_at_ms": 1777370000000,
  "body": { "device_id": "deskpi-a1", "proto": "v1", "mode": "eye-only" }
}
```

**session.ack (session.open 수신 시)**
```json
{
  "v": 1, "t": "session.ack", "sid": "app-provided-sid", "seq": 1, "src": "pi", "sent_at_ms": 1777370001000,
  "body": { "accepted": true, "camera_ready": true }
}
```

## 6. 연결 및 보안 검증 흐름

1.  **QR 등록**: 사용자가 Pi의 QR을 스캔한다. 앱은 QR에 담긴 `spki_sha256`(서버 공개키 지문)을 해당 `device_id`의 신뢰 앵커로 저장한다.
2.  **NSD 탐색**: 앱이 Wi-Fi에서 `_sleepcare._tcp`를 찾고, `device_id`가 등록된 값과 같은지 확인한다.
3.  **WSS 연결**: 앱이 `wss://...`로 접속한다.
4.  **SPKI Pinning**: 앱은 TLS 핸드셰이크 과정에서 서버가 보여준 인증서의 Public Key 지문을 계산하여, QR로 등록된 지문과 대조한다. 다르면 연결을 즉시 차단한다.
5.  **애플리케이션 핸드셰이크**: 보안 채널이 열리면 앱은 `hello`를 보낸다. Pi가 정해진 시간 내에 올바른 `hello_ack`를 보내야 연결이 최종 완료된다.

## 7. 타임아웃 및 하트비트 설정

Android 앱은 이제 자체 타이머 대신 라이브러리(OkHttp)의 타임아웃 정책을 따른다.

*   **Read Timeout (15초)**: 앱이 메시지를 보낸 후 서버의 응답(ACK)이 15초 이상 지연되면 앱 측에서 소켓을 강제 종료한다. 서버는 모든 요청(특히 `hello`, `session.open`)에 대해 즉시 응답해야 한다.
*   **Heartbeat (20초)**: 앱은 20초 간격으로 WebSocket Ping을 보낸다.
*   **서버 대응**: `libwebsockets`의 `lws_context_creation_info`에서 `ws_ping_pong_interval` 등을 활용하여 하트비트를 관리하거나, 앱의 Ping에 대해 자동으로 Pong 응답이 나가도록 설정한다.

## 8. 자주 발생하는 문제 및 해결

*   **Handshake Failure**: `pi_cert.pem`과 `pi_key.pem`이 서로 맞지 않거나, LWS의 SSL 컨텍스트 초기화가 실패했을 때 발생한다.
*   **SPKI Pin 불일치**: 서버 인증서를 새로 만들었지만 QR 코드를 갱신하지 않았을 때 발생한다. 인증서를 바꾸면 반드시 새 QR을 표시해야 한다.
*   **NSD 발견 실패**: Android 폰과 Pi가 서로 다른 Wi-Fi 주역대에 있거나, Avahi 설정의 `device_id`가 QR과 다를 때 발생한다.
*   **연결 끊김 (Timeout)**: 서버가 ACK를 너무 늦게 보내 앱의 15초 Read 타임아웃에 걸리는 경우다. LWS의 쓰기 버퍼(`LWS_PRE`) 확보 및 이벤트 루프 블로킹 여부를 확인한다.

## 9. Pi 개발자 완료 기준 (체크리스트)

*   [ ] `libwebsockets` 기반 WSS 서버 구동 및 TLS(pi_cert/key) 적용 확인
*   [ ] Avahi를 통한 서비스 광고 및 TXT 레코드(`device_id`, `ws`, `proto`, `tls`) 설정 확인
*   [ ] [pi-qr-pairing.md](./pi-qr-pairing.md) 규격에 맞는 QR 코드 생성 및 표시 구현
*   [ ] 앱의 `hello`에 대해 `hello_ack` 응답 확인 (15초 이내)
*   [ ] `session.open` 요청 시 세션 ID를 유지하며 `session.ack` 응답 확인
*   [ ] 세션 중 주기적인 `risk.update` 발송 구현

> 상세한 디버깅 방법은 [Pi 개발자 디버그 가이드](./pi-developer-debug-guide.md)를 참고한다.
