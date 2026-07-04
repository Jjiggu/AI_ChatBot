# AI_LOG — 지시 → 결과 → 수정한 것

## P1 스캐폴딩 (PR #1)
- 지시: compose(Postgres 15.8) + 엔티티 4종/인덱스/유니크 제약 + Clock 빈 + 헬스체크, Gradle Kotlin DSL 변환.
- 결과: 게이트(build + health 200) 1회 통과. Builder가 환경 충돌 2건(호스트 postgres 5432 선점, 무관 컨테이너의 8080 점유) 자체 발견·해결 — 첫 헬스체크 200이 다른 앱 응답이었던 것을 잡아냄.
- 수정: 없음. 시작 전 패키지 org.ims → com.example.chatbot 정리(회사 식별 정보 제거, 루트 커밋 amend).
