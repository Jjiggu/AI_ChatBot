# AI_LOG — 지시 → 결과 → 수정한 것

## P1 스캐폴딩 (PR #1)
- 지시: compose(Postgres 15.8) + 엔티티 4종/인덱스/유니크 제약 + Clock 빈 + 헬스체크, Gradle Kotlin DSL 변환.
- 결과: 게이트(build + health 200) 1회 통과. Builder가 환경 충돌 2건(호스트 postgres 5432 선점, 무관 컨테이너의 8080 점유) 자체 발견·해결 — 첫 헬스체크 200이 다른 앱 응답이었던 것을 잡아냄.
- 수정: 없음. 시작 전 패키지 org.ims → com.example.chatbot 정리(회사 식별 정보 제거, 루트 커밋 amend).

## P2 인증 (PR #2)
- 지시: SPEC §4.1 + A2/A4/A5. 가입/로그인/JWT 보호, admin 시드, RestControllerAdvice 1개.
- 결과: 게이트(빌드 + curl 6단계: 201/409/토큰/401/필터통과/admin 200) 1회 통과.
- 수정: 없음. Builder가 커스텀 JWT 필터 대신 내장 Nimbus 채택(허용한 택1) — 직접 작성 코드 감소.

## P2 리뷰 반영 (PR #3, Codex 리뷰)
- 지시: Codex 지적 3건 — JWT_SECRET 기본값 제거, permitAll→deny-by-default, ADMIN_PASSWORD 필수화.
- 결과: 3건 전부 반영. env 미설정 기동 실패 확인, 정상 기동 시 401/admin 200/비-API 401 게이트 통과.
- 수정: 없음. 트레이드오프 — 데모 기동에 env 2개 필수화, README에서 안내 예정.

## 인수 기록 (Codex, P3 진행 중)
- 지시: Claude Code 중단 후 Orchestrator 겸 Builder로 인수, STATE 부재를 실제 repo 상태 기준으로 복구.
- 결과: `feat/chat`의 P3 구현/테스트를 확인했고 `./gradlew test`, `./gradlew build` 성공. 실제 OpenAI smoke는 현재 프로젝트가 `gpt-4o-mini` 접근 권한 없음(403)으로 차단.
- 수정: `ChatService.create`에 트랜잭션 적용, `docs/STATE.md` 신규 작성.

## P3 대화 (feat/chat)
- 지시: SPEC §4.2 대화 생성 중 비스트리밍 POST /api/chats, A3/A9, 30분 규칙 테스트 3건.
- 결과: `./gradlew cleanTest test`, `./gradlew build`, curl smoke(health→signup→login→chat 201) 통과. OpenAI 직접 호출 200, 앱 응답 201 확인.
- 수정: Gradle daemon이 이전 OpenAI env를 잡고 있어 `--no-daemon`으로 smoke 재실행. `docs/AGENTS.md ` 추적 경로를 정상 `docs/AGENTS.md`로 정리 예정.

## P4 조회/삭제 (feat/chat-query-p4)
- 지시: SPEC §4.2의 스레드 그룹 목록과 스레드 삭제. MEMBER는 자기 것만, ADMIN은 전체 조회, 삭제는 소유자만.
- 결과: `./gradlew test`, `./gradlew build`, curl smoke(스레드 그룹 200, 타인 삭제 403, admin 전체 조회 200, 소유자 삭제 204) 통과.
- 수정: 원격 `feat/chat-query`의 별도 README 커밋을 덮지 않도록 P4 PR 브랜치를 `feat/chat-query-p4`로 분리. 로컬 smoke DB의 기존 admin hash가 현재 env와 달라 DB만 보정.
