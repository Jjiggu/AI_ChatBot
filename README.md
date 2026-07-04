# AI 챗봇 API 사용 안내

이 프로젝트는 질문을 보내면 AI가 답변하는 API 데모입니다. 처음 사용하는 고객도
아래 순서대로 실행하면 `회원가입 -> 로그인 -> 질문 -> AI 답변 확인`까지 재현할 수
있습니다.

## 현재 제공되는 기능

- 회원가입
- 로그인 및 24시간 유효한 인증 토큰 발급
- 로그인한 사용자만 질문 가능
- 질문을 OpenAI에 전달하고 답변 저장
- 마지막 질문 후 30분 안에 다시 질문하면 같은 대화 묶음으로 저장
- 마지막 질문 후 30분이 지나면 새 대화 묶음으로 저장

## 예정 기능

- 대화 목록 조회와 대화 묶음 삭제
- 관리자 전체 대화 조회
- 답변 피드백 등록, 조회, 상태 변경
- 실시간 스트리밍 답변
- 최근 24시간 활동 분석과 CSV 보고서

## 1. 사전 준비

먼저 아래 프로그램과 값이 필요합니다.

- Docker Desktop
- Java 21
- OpenAI API 키

앱 실행에는 환경변수 3개가 필요합니다.

```bash
export OPENAI_API_KEY="발급받은_OpenAI_API_키"
export JWT_SECRET="32글자_이상의_임의_문자열로_바꾸세요"
export ADMIN_PASSWORD="관리자_비밀번호로_바꾸세요"
```

`OPENAI_API_KEY`가 없거나 사용할 수 없는 모델 권한이면 질문 요청에서 실패합니다.
기본 모델은 `gpt-4o-mini`입니다.

## 2. 데이터베이스 실행

프로젝트 폴더에서 PostgreSQL을 실행합니다.

```bash
docker compose up -d
```

정상 실행 여부는 아래 명령으로 확인할 수 있습니다.

```bash
docker compose ps
```

## 3. 앱 실행

다음 명령으로 API 서버를 실행합니다.

```bash
./gradlew bootRun
```

서버가 켜지면 기본 주소는 `http://localhost:8080`입니다. 다른 터미널을 열고
아래 명령으로 상태를 확인합니다.

```bash
curl http://localhost:8080/actuator/health
```

예상 응답:

```json
{"status":"UP"}
```

## 4. 사용 시나리오

아래 예시는 `test@example.com` 고객 계정을 새로 만들고, 로그인 후 질문을 보내는
흐름입니다. 이미 같은 이메일로 가입했다면 이메일 주소만 바꿔서 실행하세요.

### 4.1 회원가입

```bash
curl -i -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password123","name":"홍길동"}'
```

예상 응답:

```http
HTTP/1.1 201
```

```json
{
  "id": "2b4b62f8-6c3a-45b5-9d65-75c8a6f9a111",
  "email": "test@example.com",
  "name": "홍길동",
  "role": "MEMBER",
  "createdAt": "2026-07-04T07:30:00Z"
}
```

### 4.2 로그인

```bash
curl -i -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password123"}'
```

예상 응답:

```http
HTTP/1.1 200
```

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

다음 단계에서 필요하므로 `accessToken` 값을 복사해 둡니다.

### 4.3 질문 보내기

아래 명령의 `여기에_토큰_붙여넣기`를 로그인 응답의 `accessToken` 값으로 바꿉니다.

```bash
curl -i -X POST http://localhost:8080/api/chats \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer 여기에_토큰_붙여넣기" \
  -d '{"question":"고객에게 보낼 짧은 환영 문구를 써줘"}'
```

예상 응답:

```http
HTTP/1.1 201
```

```json
{
  "id": "7d0d4cd1-c0a3-4a30-8b14-cf56c6a0a222",
  "question": "고객에게 보낼 짧은 환영 문구를 써줘",
  "answer": "환영합니다! 궁금한 점이 있으면 언제든 편하게 문의해 주세요.",
  "model": "gpt-4o-mini",
  "threadId": "0fe59d4b-08f8-4973-8b68-67981006b333",
  "createdAt": "2026-07-04T07:31:00Z"
}
```

`answer`가 AI가 생성한 답변입니다. 실제 문장은 요청 시점과 모델 응답에 따라 달라질
수 있습니다.

### 4.4 같은 대화로 이어 질문하기

마지막 질문 후 30분 안에 다시 질문하면 같은 `threadId`로 저장됩니다.

```bash
curl -i -X POST http://localhost:8080/api/chats \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer 여기에_토큰_붙여넣기" \
  -d '{"question":"조금 더 따뜻한 말투로 바꿔줘"}'
```

예상 응답에서는 앞 질문과 같은 `threadId`가 나옵니다.

```json
{
  "id": "af62864f-69e7-493a-8072-13d34f397444",
  "question": "조금 더 따뜻한 말투로 바꿔줘",
  "answer": "찾아주셔서 감사합니다. 필요한 일이 있으시면 언제든 편안하게 말씀해 주세요.",
  "model": "gpt-4o-mini",
  "threadId": "0fe59d4b-08f8-4973-8b68-67981006b333",
  "createdAt": "2026-07-04T07:32:00Z"
}
```

## 5. 자주 만나는 응답

로그인하지 않고 질문하면 인증 오류가 납니다.

```json
{
  "code": "UNAUTHORIZED",
  "message": "인증이 필요합니다"
}
```

이미 가입된 이메일로 다시 가입하면 중복 오류가 납니다.

```json
{
  "code": "CONFLICT",
  "message": "이미 가입된 이메일입니다"
}
```

스트리밍 답변을 요청하면 아직 지원하지 않는다는 응답이 납니다.

```json
{
  "code": "NOT_SUPPORTED",
  "message": "스트리밍 응답은 아직 지원하지 않습니다"
}
```

## 6. 종료와 초기화

앱은 실행 중인 터미널에서 `Ctrl+C`로 종료합니다.

데이터베이스 컨테이너를 중지하려면 아래 명령을 실행합니다.

```bash
docker compose down
```

다시 처음부터 테스트하려면 데이터베이스를 내린 뒤 다시 올리면 됩니다.

```bash
docker compose down
docker compose up -d
```
