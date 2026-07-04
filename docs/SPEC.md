# AI 챗봇 API — 설계 및 구현 스펙

> 이 문서는 구현 시작 전 20분간 작성한 설계 문서입니다.
> 요구사항 분석 → 가정 → 설계 → 우선순위 순으로 정리했으며,
> AI 코딩 에이전트(Claude Code)에 전달하는 구현 명세를 겸합니다.

---

## 1. 과제 분석 요약

- **목표**: 비개발자 고객도 따라할 수 있는 "AI를 API로 사용하는" 데모. 크리티컬 패스는
  `회원가입 → 로그인 → 질문 → AI 답변` 이며, 이 경로의 완성도가 최우선이다.
- **제약**: 총 3시간(문서 포함). 채점 기준상 구현의 양보다 판단과 설명이 중요하므로,
  전 기능 구현이 아니라 **의도적인 우선순위와 컷**을 설계한다.
- **확장성 요구**: "지속 확장 개발" + "향후 대외비 문서 학습(RAG)" → LLM 호출부를
  인터페이스로 추상화하고, DB는 처음부터 운영급인 Postgres를 채택한다(docker-compose 제공).

## 2. 가정 (요구사항 조율 불가 상황에서의 판단)

| # | 가정 | 근거 |
|---|------|------|
| A1 | OpenAI API 키는 환경변수 `OPENAI_API_KEY`로 주입 | 대외비/키 노출 방지, 12-factor |
| A2 | 회원가입은 항상 `MEMBER` 권한. 관리자는 시드 데이터로 1명 생성 | 가입 필수 정보에 권한이 없음. 자가 승격은 보안 결함 |
| A3 | 30분 규칙의 기준은 "해당 유저의 가장 최근 chat의 createdAt" | 스펙 문구 "마지막 질문 후 30분"의 직독. 스레드별이 아닌 유저별 판단 |
| A4 | JWT 유효기간 24시간, HS256, 시크릿은 환경변수 | 데모 편의와 보안의 절충. refresh token은 스코프 아웃 |
| A5 | 패스워드는 BCrypt 해싱, 최소 8자 | 기본 보안 요건 |
| A6 | 스레드 삭제는 소유자만 가능 (관리자 포함 타인 스레드 삭제 불가) | 스펙이 조회에만 관리자 전권을 명시. 삭제는 명시 없음 → 보수적 해석 |
| A7 | 스레드 삭제 시 소속 chat 및 feedback은 cascade 삭제 | 고아 데이터 방지 |
| A8 | isStreaming=true는 SSE(text/event-stream)로 응답, 완료 후 DB 저장 | 표준적 스트리밍 방식 |
| A9 | 기본 모델 `gpt-4o-mini`, model 파라미터로 오버라이드 | 데모 비용 최소화 |
| A10 | "하루 동안" = 요청 시점 기준 최근 24시간 | 자정 기준보다 스펙 문구에 충실 |
| A11 | DB는 Postgres 단일 채택, docker-compose로 원커맨드 기동 | "지속 확장 개발" 요건에 운영 DB 직행이 유리. timestamptz 요구도 Postgres 네이티브 타입으로 정확히 충족. 테스트는 Testcontainers 우선, 여의치 않으면 H2 fallback |

## 3. 데이터 모델

```
User      : id(UUID), email(unique), password(bcrypt), name,
            role(MEMBER|ADMIN), createdAt(timestamptz)
Thread    : id(UUID), user(FK, N:1), createdAt(timestamptz)
Chat      : id(UUID), thread(FK, N:1), question(text), answer(text),
            model(string), createdAt(timestamptz)
Feedback  : id(UUID), user(FK), chat(FK), isPositive(boolean),
            status(PENDING|RESOLVED), createdAt(timestamptz)
            UNIQUE(user_id, chat_id)  ← DB 레벨 제약
```

- Feedback의 유니크 제약은 애플리케이션 체크 + DB 제약 이중으로 건다
  (동시 요청 시 애플리케이션 체크만으로는 뚫림).
- Chat 조회 성능을 위해 `chat(thread_id, created_at)` 인덱스.
- 30분 규칙 판단을 위해 `chat(created_at)` + user 경유 조회 또는
  `thread(user_id, ...)` 인덱스.

## 4. API 명세

인증: 회원가입/로그인 제외 전부 `Authorization: Bearer {JWT}` 필수.
공통 에러 포맷: `{ "code": string, "message": string }`

### 4.1 인증
| Method | Path | Body | 응답 |
|---|---|---|---|
| POST | /api/auth/signup | email, password, name | 201, 유저 요약 |
| POST | /api/auth/login | email, password | 200, `{ accessToken }` |

### 4.2 대화
| Method | Path | 파라미터 | 응답 |
|---|---|---|---|
| POST | /api/chats | body: question(필수), isStreaming?, model? | 201 chat / SSE 스트림 |
| GET | /api/chats | query: page, size, sort(createdAt,asc\|desc) | 스레드 단위 그룹 목록 |
| DELETE | /api/threads/{threadId} | - | 204 |

- POST /api/chats 처리 순서:
  1. 유저의 최근 chat 조회 → 없거나 30분 경과 → 새 Thread 생성, 아니면 기존 Thread 사용
  2. 해당 Thread의 기존 chat들을 (question→user, answer→assistant) 메시지 배열로 변환해 OpenAI 호출
  3. 응답을 Chat으로 저장 후 반환
- GET /api/chats 응답 형태: **Thread 기준 페이지네이션**, 각 thread 안에 chats 배열.
  (chat 기준 페이징이 아님 — "스레드 단위로 그룹화된 목록" 요구의 직독)
- 권한: MEMBER는 자기 것만, ADMIN은 전체 조회.

### 4.3 피드백
| Method | Path | 파라미터 | 응답 |
|---|---|---|---|
| POST | /api/chats/{chatId}/feedbacks | body: isPositive | 201 |
| GET | /api/feedbacks | query: page, size, sort, isPositive? | 목록 |
| PATCH | /api/feedbacks/{feedbackId}/status | body: status | 200 (ADMIN 전용) |

- 생성 권한: MEMBER는 자신의 chat에만, ADMIN은 모든 chat에.
- (userId, chatId) 중복 생성 시 409 CONFLICT.

### 4.4 분석/보고 (P6 — 시간 잔여 시에만)
| Method | Path | 설명 |
|---|---|---|
| GET | /api/admin/activity | 최근 24h 가입/로그인/대화 수 (ADMIN) |
| GET | /api/admin/reports/chats | 최근 24h 대화 CSV (ADMIN) |

- 로그인 수 집계를 위해 구현 시 `ActivityLog` 테이블 또는 로그인 이벤트 기록 필요.
- **컷 판단 시 README에 미구현 사유와 확장 설계를 기술하는 것으로 대체.**

## 5. 아키텍처 결정

- **스택 (고정)**: Kotlin 1.9.x / Spring Boot 3.x / Java 21 /
  PostgreSQL 15.8 / Gradle Kotlin DSL / Spring Data JPA
- **레이어**: Controller → Service → Repository (Spring Data JPA)
- **LLM 추상화**: `ChatModelClient` 인터페이스 + `OpenAiChatClient` 구현체.
  향후 RAG 도입 시 이 인터페이스 뒤에 retrieval 단계를 끼워넣는 확장점이 된다.
- **시간 의존 로직**: `Clock`을 빈으로 주입 → 30분 규칙 단위 테스트 가능하게.
- **Security**: JWT 필터 1개 + SecurityFilterChain. 세션 STATELESS.
- **DB 기동**: Postgres는 `docker-compose.yml`로 제공 (`docker compose up -d` 한 줄로 기동).
  README 매뉴얼의 첫 단계가 되므로 비개발자도 실행 가능하게 기본값(포트/계정)을 compose에 고정.
  `ddl-auto=update`로 데모 편의 우선, 운영 전환 시 Flyway 도입을 확장 포인트로 명시.
- **테스트 DB**: Testcontainers(Postgres)로 프로덕션과 동일 엔진 사용.
  단, 셋업에 15분 이상 소모되면 테스트 한정 H2로 전환 (30분 규칙 테스트는 DB 무관 단위 테스트라 영향 없음).
- **알려진 트레이드오프 (의도적 미해결, 문서화로 대체)**:
  - 동일 유저의 동시 질문 시 스레드 2개 생성 가능한 race condition.
    해결책(유저 단위 잠금 또는 유니크 제약 + 재시도)은 인지하고 있으나 3시간 스코프에서 제외.
  - 스트리밍 중 연결 끊김 시 부분 응답 저장 정책 미정의.

## 6. 우선순위와 컷라인

구현 순서: 인증 → 대화 생성(비스트리밍)+30분 규칙 → 조회/삭제 → 피드백 → (스트리밍) → (분석/보고)

시간 부족 시 컷 순서: 분석/보고 → 스트리밍 → 피드백 필터/상태변경 → 피드백 전체

어떤 경우에도 유지: 데모 크리티컬 패스, 30분 규칙 + 단위 테스트, 본 문서와 README.

## 7. 테스트 전략 (최소 스코프)

- 스레드 30분 규칙: 경계값 포함 단위 테스트 3개
  (첫 질문 / 29분 59초 / 30분 1초)
- 피드백 중복 생성 409 테스트 1개
- 나머지는 수동 검증(curl 시나리오)으로 대체하고 README에 시나리오 수록
