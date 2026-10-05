# 010 인사관리 FR-017~019 검증표

검증일: 2026-09-25

| 요구사항 | 구현·검증 근거 | 판정 |
|---|---|---|
| FR/AC-017 근로계약 | 계약·수습 기간, 근무 장소, 주당 근로분, 약정 임금 저장; 사원 행 잠금과 경계일 포함 겹침·동시성 테스트 | 조건부 통과 — 실제 감사 저장소는 900 연결 필요 |
| FR/AC-018 임금 항목 | 네 분류와 금액·과세·통상임금 필수 계약, DB 제약과 API 검증 | 조건부 통과 — 실제 감사 저장소는 900 연결 필요 |
| FR/AC-019 적용일 이력 | 적용일별 append-only 등록, 동일 중복 거부, 기존 이력 불변·멱등 재시도 테스트 | 통과 |
| FR/AC-003 계약 영역 | 인사기록카드의 빈 계약 배열을 실제 계약 projection으로 교체 | 통과 |

## 범위 경계

FR-020 계약 만료 60일 전 알림은 900 실제 알림 저장소, FR-021 PDF·열람 확인은 900 파일 저장소 연결 후에 별도 티켓으로 구현한다. 근로계약과 임금 이력은 수정·삭제 API를 제공하지 않는다.

## 실행 결과

- 기능 MySQL 8.4 통합: `ContractMysqlTest` 4개 성공.
- Backend 전체: 91개 중 90개 성공. 기존 `AuthenticationMysqlTest.scheduledTerminationAccessBlocksLoginSessionAndPermissionFromSeoulEffectiveDay`의 JDBC `TIMESTAMP`·JVM 시간대 해석이 9시간 어긋나 1개 실패했으며 이번 변경 파일과 무관하다.
- Frontend: 14 tests, lint, production build 성공.
