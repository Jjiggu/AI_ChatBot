# CONVENTIONS.md — Git 작업 규칙

3시간 솔로 + AI 에이전트 협업 상황에 맞춘 최소 규칙입니다.
규칙의 목적은 형식이 아니라 **작업 흐름이 히스토리에 드러나게 하는 것**입니다.

## 브랜치 전략 — Trunk-based

- `main`: 항상 빌드 가능 상태 유지.
- 단계(P1~P6)마다 단명 브랜치 1개: `feat/scaffolding`, `feat/auth`,
  `feat/chat`, `feat/chat-query`, `feat/feedback`, `docs/manual`
- 단계 완료 → PR → **squash merge** → 브랜치 삭제. 리뷰어 없는 셀프 머지 허용
  (단, Codex 리뷰를 받은 단계는 PR 본문에 리뷰 반영 내역 기록).

## 커밋 컨벤션 — Conventional Commits

```
<type>(<scope>): <subject>
```

- type: `feat` `fix` `test` `docs` `chore` `refactor`
- scope: `auth` `chat` `thread` `feedback` `infra` `docs` 중 하나
- subject: 한국어 허용, 50자 이내, 무엇이 아니라 **왜**가 드러나면 가점
- 예:
  - `docs(docs): SPEC 초안 — 구현 전 설계 우선`
  - `feat(auth): JWT 발급 및 인증 필터`
  - `feat(chat): 스레드 30분 규칙 — 유저 최근 chat 기준 (SPEC A3)`
  - `test(thread): 30분 경계값 테스트 3건`
- 금지: 여러 단계를 뭉친 커밋, `wip`, 빌드 깨진 상태의 main 머지.

## PR 컨벤션

제목은 커밋 컨벤션과 동일 형식. 본문 템플릿:

```markdown
## 목적
(이 단계가 SPEC의 어느 요구를 구현하는지 1–2줄)

## 주요 결정
(SPEC 가정 번호 참조. 예: A3 해석에 따라 유저별 30분 판단)

## 검증
(실행한 게이트: 빌드 / 테스트 / curl 시나리오 결과)

## 리뷰 반영
(Codex 리뷰를 받았다면: 지적 → 반영 여부와 사유)
```

## 에이전트별 주의

- **Builder**: 커밋은 만들되 push/merge는 Orchestrator 승인 후.
- **Orchestrator**: merge 전 게이트(AGENTS.md §6) 미통과 시 merge 금지.
- 커밋 저자 정보에 회사 식별 가능한 이메일을 쓰지 않는다.
