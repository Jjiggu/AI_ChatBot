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
