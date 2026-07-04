# AI 챗봇 API 사용 안내

질문을 보내면 AI가 답변하는 백엔드 API 데모입니다. 아래 순서대로 실행하면
`회원가입 -> 로그인 -> 질문 -> 답변 확인`까지 재현할 수 있습니다.

## 과제 접근과 AI 활용

### 과제 분석

이번 과제는 모든 기능을 많이 구현하는 것보다, 제한된 시간 안에서 고객이 실제로 따라 해볼 수 있는
핵심 데모 경로를 안정적으로 만드는 것이 더 중요하다고 판단했습니다. 그래서 우선순위는
`회원가입 -> 로그인 -> 질문 -> AI 답변`을 최상위에 두고, 그 다음으로 30분 스레드 규칙, 조회/삭제,
피드백 기능을 붙이는 순서로 잡았습니다.

요구사항이 모호한 부분은 구현 전에 가정으로 고정했습니다. 가입 사용자는 항상 `MEMBER`로 생성하고,
관리자는 시드 계정으로만 제공했습니다. 30분 규칙은 "해당 사용자의 가장 최근 chat 생성 시각"을 기준으로
해석했습니다. 스트리밍 응답과 최근 24시간 리포트는 시간 대비 리스크가 커서 핵심 데모 이후의 컷 대상으로
분류했습니다.

확장성 요구는 LLM 호출부를 `ChatModelClient` 인터페이스로 분리하는 방식으로 반영했습니다. 지금은 OpenAI를
직접 호출하지만, 향후 문서 검색/RAG 단계를 붙일 때 컨트롤러나 도메인 로직을 크게 바꾸지 않아도 되도록
확장 지점을 남겼습니다.

### AI 활용 방식

AI는 단순히 코드를 빠르게 작성하는 도구가 아니라, 작업 흐름을 통제하는 협업 도구로 사용했습니다.
루트에는 `CLAUDE.md`와 `AGENTS.md` 포인터 파일을 두고, 실제 지침은 `docs/AGENTS.md` 허브에 모았습니다.
이 허브에는 Orchestrator, Builder, Reviewer, Docs Writer 역할을 나누는 라우팅 테이블을 두어 각 도구가
자신의 역할에 필요한 문서만 읽도록 했습니다. 제한 시간이 있는 과제에서 AI가 모든 문서를 계속 읽으며
맥락을 낭비하지 않도록 하기 위한 구조였습니다.

구현 전에는 AI와 함께 `docs/SPEC.md`를 먼저 작성했습니다. 여기에는 요구사항 해석, 가정, API 명세,
우선순위와 컷라인을 정리했습니다. 특히 "계획을 먼저 보고하고 승인 후 진행", "한 에이전트는 한 단계만
담당", "단계마다 빌드와 smoke test를 통과해야 다음 단계로 이동" 같은 규칙을 두었습니다. AI가 빠르게
코드를 만들 수는 있지만, 방향이 틀어진 상태로 계속 진행하면 짧은 시간 안에 되돌리기 어렵기 때문입니다.

역할도 의도적으로 나누었습니다. Claude Code는 주로 단계별 구현을 맡기고, Codex는 리뷰와 문서화를 맡기는
흐름으로 사용했습니다. 보안과 도메인 판단이 중요한 단계가 끝난 뒤에는 Codex 리뷰를 통해 JWT 설정,
인증 허용 범위, 관리자 비밀번호 주입 방식 같은 부분을 다시 확인했습니다. 기능 구현이 이어지는 동안에는
README를 함께 정리해, 구현 결과와 판단 근거가 문서에 남도록 했습니다.

어려웠던 점은 AI가 만든 결과도 결국 실제 실행 환경에서 다시 검증해야 한다는 점이었습니다. OpenAI 모델 권한
문제로 smoke test가 막힌 적이 있었고, Gradle daemon이 이전 환경변수를 잡고 있어 터미널에서 설정한 값과
애플리케이션 실행 값이 달라지는 문제도 있었습니다. 첫 헬스체크가 실제 이 애플리케이션의 응답인지, 혹은
다른 로컬 서버의 응답인지도 확인해야 했습니다. 이런 문제는 코드만 읽어서는 알기 어렵기 때문에 포트,
환경변수, 실제 HTTP 응답을 직접 확인하면서 보정했습니다.

### 가장 어려웠던 기능

가장 어려웠던 기능은 30분 스레드 규칙이었습니다. 단순히 새 채팅을 저장하는 문제가 아니라, 사용자의 마지막
질문 시각을 기준으로 기존 스레드를 재사용할지 새 스레드를 만들지 결정해야 했습니다. 기준 시각을
`Instant.now()`로 직접 읽으면 테스트가 불안정해지므로 `Clock`을 주입했고, 첫 질문, 29분 59초,
30분 1초 케이스를 단위 테스트로 고정했습니다.

목록 조회도 단순 chat 페이징이 아니라 스레드 단위 페이징 후 각 스레드의 chat 목록을 다시 붙이는 방식으로
구현했습니다. 이 요구를 chat 기준 페이지네이션으로 풀면 구현은 쉬워지지만 "스레드 단위 그룹화"라는 요구와
맞지 않는다고 판단했습니다.

남은 리스크도 있습니다. 동일 사용자가 동시에 질문을 보내면 30분 판단 직후 스레드가 중복 생성될 수 있습니다.
운영 수준으로 올린다면 유저 단위 잠금, 트랜잭션 격리 수준 조정, 또는 재시도 가능한 제약 설계를 추가해야
합니다. 이번 과제 범위에서는 이 리스크를 인지하고 문서화하는 선에서 멈췄습니다.

## 제공 기능

- 회원가입 및 로그인
- JWT 기반 인증
- 관리자 계정 자동 생성
- OpenAI Chat Completions 기반 답변 생성
- 마지막 질문 후 30분 이내 질문 시 같은 대화 스레드로 저장
- 스레드 단위 대화 목록 조회
- 본인 스레드 삭제
- 답변 피드백 등록, 조회, 상태 변경

## 구현 범위와 컷

- `isStreaming=true` 스트리밍 응답은 현재 미지원입니다. 요청 시 `NOT_SUPPORTED` 에러를 반환합니다.
- 최근 24시간 활동 분석과 CSV 보고서는 시간 제한상 제외했습니다.
- 향후 대외비 문서 검색/RAG 연동을 고려해 LLM 호출부는 `ChatModelClient` 인터페이스 뒤에 분리했습니다.

## 1. 사전 준비

먼저 아래 프로그램과 값이 필요합니다.

- Docker Desktop
- Java 21
- OpenAI API 키

서버 실행 전에 환경변수를 설정합니다.

```bash
export OPENAI_API_KEY="발급받은_OpenAI_API_키"
export JWT_SECRET="32글자_이상의_임의_문자열로_바꾸세요"
export ADMIN_PASSWORD="관리자_비밀번호로_바꾸세요"
```

기본 AI 모델은 `gpt-4o-mini`입니다. OpenAI API 키가 없거나 모델 권한이 없으면 질문 요청은
`BAD_GATEWAY` 에러로 실패할 수 있습니다.

## 2. 데이터베이스 실행

프로젝트 폴더에서 PostgreSQL을 실행합니다.

```bash
docker compose up -d
```

정상 실행 여부를 확인합니다.

```bash
docker compose ps
```

기본 DB 값은 아래와 같습니다.

- host: `localhost`
- port: `5432`
- database: `ai_chatbot`
- username: `postgres`
- password: `postgres`

## 3. API 서버 실행

다음 명령으로 서버를 실행합니다.

```bash
./gradlew bootRun
```

서버가 켜지면 기본 주소는 `http://localhost:8080`입니다. 다른 터미널에서 상태를 확인합니다.

```bash
curl http://localhost:8080/actuator/health
```

예상 응답:

```json
{"status":"UP"}
```

## 4. 기본 사용 시나리오

아래 예시는 새 고객 계정을 만들고, 로그인 후 질문을 보내는 흐름입니다. 이미 같은 이메일로
가입했다면 이메일 주소만 바꿔서 실행하세요.

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

`answer`가 AI가 생성한 답변입니다. 실제 문장은 요청 시점과 모델 응답에 따라 달라질 수 있습니다.

### 4.4 같은 스레드로 이어 질문하기

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

## 5. 대화 조회와 삭제

### 5.1 스레드 단위 목록 조회

```bash
curl -i "http://localhost:8080/api/chats?page=0&size=10&sort=createdAt,desc" \
  -H "Authorization: Bearer 여기에_토큰_붙여넣기"
```

예상 응답:

```json
{
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "threads": [
    {
      "threadId": "0fe59d4b-08f8-4973-8b68-67981006b333",
      "userId": "2b4b62f8-6c3a-45b5-9d65-75c8a6f9a111",
      "createdAt": "2026-07-04T07:31:00Z",
      "chats": [
        {
          "id": "7d0d4cd1-c0a3-4a30-8b14-cf56c6a0a222",
          "question": "고객에게 보낼 짧은 환영 문구를 써줘",
          "answer": "환영합니다! 궁금한 점이 있으면 언제든 편하게 문의해 주세요.",
          "model": "gpt-4o-mini",
          "createdAt": "2026-07-04T07:31:00Z"
        }
      ]
    }
  ]
}
```

일반 사용자는 본인 스레드만 조회합니다. 관리자는 전체 사용자의 스레드를 조회할 수 있습니다.

### 5.2 스레드 삭제

삭제하면 해당 스레드의 대화와 피드백도 함께 사라집니다. 아래 삭제 예시는 피드백 테스트까지 마친 뒤
마지막에 실행하세요.

```bash
curl -i -X DELETE http://localhost:8080/api/threads/0fe59d4b-08f8-4973-8b68-67981006b333 \
  -H "Authorization: Bearer 여기에_토큰_붙여넣기"
```

예상 응답:

```http
HTTP/1.1 204
```

삭제는 스레드 소유자만 할 수 있습니다.

## 6. 피드백 사용

### 6.1 답변 피드백 등록

아래 명령의 URL에서 `chatId`는 질문 응답의 `id` 값입니다.

```bash
curl -i -X POST http://localhost:8080/api/chats/7d0d4cd1-c0a3-4a30-8b14-cf56c6a0a222/feedbacks \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer 여기에_토큰_붙여넣기" \
  -d '{"isPositive":true}'
```

예상 응답:

```http
HTTP/1.1 201
```

```json
{
  "id": "11d984dd-6f01-4588-9c38-d84f3949a333",
  "userId": "2b4b62f8-6c3a-45b5-9d65-75c8a6f9a111",
  "chatId": "7d0d4cd1-c0a3-4a30-8b14-cf56c6a0a222",
  "isPositive": true,
  "status": "PENDING",
  "createdAt": "2026-07-04T07:35:00Z"
}
```

같은 사용자가 같은 답변에 피드백을 두 번 등록하면 `409 CONFLICT`가 반환됩니다.

### 6.2 피드백 목록 조회

```bash
curl -i "http://localhost:8080/api/feedbacks?page=0&size=10&sort=createdAt,desc&isPositive=true" \
  -H "Authorization: Bearer 여기에_토큰_붙여넣기"
```

예상 응답:

```json
{
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "feedbacks": [
    {
      "id": "11d984dd-6f01-4588-9c38-d84f3949a333",
      "userId": "2b4b62f8-6c3a-45b5-9d65-75c8a6f9a111",
      "chatId": "7d0d4cd1-c0a3-4a30-8b14-cf56c6a0a222",
      "isPositive": true,
      "status": "PENDING",
      "createdAt": "2026-07-04T07:35:00Z"
    }
  ]
}
```

## 7. 관리자 기능

서버 시작 시 관리자 계정이 자동 생성됩니다.

- email: `admin@example.com`
- password: `ADMIN_PASSWORD` 환경변수 값

관리자로 로그인하면 전체 대화와 전체 피드백을 조회할 수 있습니다. 피드백 상태 변경은 관리자만 가능합니다.

```bash
curl -i -X PATCH http://localhost:8080/api/feedbacks/11d984dd-6f01-4588-9c38-d84f3949a333/status \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer 관리자_토큰_붙여넣기" \
  -d '{"status":"RESOLVED"}'
```

예상 응답의 `status`가 `RESOLVED`로 변경됩니다.

## 8. 자주 만나는 응답

로그인하지 않고 보호 API를 호출하면 인증 오류가 납니다.

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

질문 값이 비어 있으면 검증 오류가 납니다.

```json
{
  "code": "BAD_REQUEST",
  "message": "question: question은 필수입니다"
}
```

## 9. 테스트

자동 테스트를 실행하려면 아래 명령을 사용합니다.

```bash
./gradlew test
```

현재 테스트는 30분 스레드 규칙의 경계값과 피드백 중복 생성 오류를 중심으로 작성했습니다.

## 10. 종료와 초기화

서버는 실행 중인 터미널에서 `Ctrl+C`로 종료합니다.

데이터베이스 컨테이너를 중지하려면 아래 명령을 실행합니다.

```bash
docker compose down
```

데이터까지 모두 지우고 처음부터 테스트하려면 볼륨을 함께 삭제합니다.

```bash
docker compose down -v
docker compose up -d
```
