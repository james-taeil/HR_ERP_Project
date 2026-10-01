# 010 인사관리 FR-022~024 검증표

검증일: 2026-10-02

| 요구사항 | 구현·검증 근거 | 판정 |
|---|---|---|
| FR/AC-022 복합 검색 | 사업장·부서·재직 상태·고용형태·입사일 구간·직위를 선택적 AND로 결합하는 MySQL 통합 테스트 | 통과 |
| FR/AC-023 부분 일치 | 성명·사번 OR 부분 일치, `%`, `_`, `!` 문자 escape 통합 테스트 | 통과 |
| FR/AC-024 커서 목록 | ID 오름차순 `afterId`, `limit+1`, `nextCursor`, 전체 COUNT 없는 다음 페이지 통합·API·React 테스트 | 통과 |
| NFR-001 10만 건 1초 | MySQL 8.4에 100,000건을 적재하고 마지막 50건 커서 조회를 1초 제한으로 실행 | 통과 |
| P-5 서버 권한 | `HR_RECORD_READ`와 SELF·부서·부서 트리·사업장·회사 범위를 SQL 전에 해석하고 범위 밖 사원이 페이징에 섞이지 않음을 검증 | 통과 |
| 개인정보 최소화 | 목록에서 생년월일·연락처·민감정보를 제외 | 통과 |

## 실행 결과

- 기능 MySQL 8.4: `EmployeeSearchMysqlTest` 3개, `AuthorizationMysqlTest` 4개 성공.
- Backend 전체: 95개 중 94개 성공. 기존 `AuthenticationMysqlTest.scheduledTerminationAccessBlocksLoginSessionAndPermissionFromSeoulEffectiveDay`의 JDBC `TIMESTAMP`·JVM 시간대 차이 1개는 이번 변경과 무관하게 재현됐다.
- Frontend: 15 tests, lint, production build 성공.

## 범위 경계

FR-020~021은 900 알림·파일 저장소 연결 후 별도 티켓으로 구현한다. FR-025 이후 일괄 등록·명부·통계는 이번 목록 검색 범위에 포함하지 않는다.
