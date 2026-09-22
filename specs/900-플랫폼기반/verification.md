# Verification: 900 플랫폼 기반 FR-001~021

> 검증 기준: `spec.md`, `design.md`, `tasks.md` T-900-001~012
> 검증 환경: Java 21, MySQL 8.4.11, React 19, Vite 8

## 요구사항 대조

| 요구사항 | 판정 | 구현·검증 근거 | 남은 사항 |
|---|---|---|---|
| FR/AC-001 | 충족 | `AccountServiceTest`, `AuthenticationMysqlTest`가 bcrypt 해시 저장과 로그인 성공을 검증한다. | 없음 |
| FR/AC-002 | 충족 | `AuthenticationServiceTest`, `AuthenticationControllerTest`, `AuthenticationMysqlTest`가 없는 계정과 잘못된 비밀번호의 동일 응답을 검증한다. | 없음 |
| FR/AC-003 | 충족 | `AuthenticationMysqlTest.concurrentFailuresCannotBypassFiveAttemptLock`이 5회 동시 실패 잠금을 검증한다. | 잠금 알림 채널은 FR-031 이후 범위다. |
| FR/AC-004 | 충족 | `PasswordPolicyTest`, `AccountServiceTest`가 설정 기반 최소 12자와 최근 5개 재사용 금지를 검증한다. | 확정 운영값에 따라 문자 종류 조합은 강제하지 않는다. |
| FR/AC-005 | 충족 | `SessionServiceTest`가 절대·유휴 만료와 접근 시각 갱신을 검증한다. | 없음 |
| FR/AC-006 | 충족 | `AuthenticationMysqlTest`가 현재·전체 로그아웃과 모든 세션 무효화를 검증한다. | 없음 |
| FR/AC-007 | 후속 | 계정 모델의 확장 경계만 보존했다. `design.md`의 명시적 제외 범위다. | 2단계 인증 설계·구현 티켓 필요 |
| FR/AC-008 | 충족 | `PlatformAccountAccessScheduler`가 퇴사일 다음 날 00:00 Asia/Seoul을 예약하고, 로그인·세션·권한 조회가 효력 시각부터 차단한다. 단위 테스트와 전체 MySQL 회귀를 통과했다. | 없음 |
| FR/AC-009 | 후속 | 재설정 토큰은 `design.md`의 명시적 제외 범위다. | 비밀번호 재설정 설계·구현 티켓 필요 |
| FR/AC-010 | 충족 | `AuthenticationMysqlTest`가 성공·실패, 시각, IP, User-Agent 이력 저장을 검증한다. | 3년 자동 파기는 후속 운영 범위다. |
| FR/AC-011 | 충족 | `AuthorizationMysqlTest`가 역할·기능 권한 부여와 다음 요청 반영을 검증한다. | 없음 |
| FR/AC-012 | 충족 | `AuthorizationMysqlTest`가 부서·하위 부서·사업장·회사 범위 조합과 범위 밖 제외를 검증한다. | 없음 |
| FR/AC-013 | 충족 | `AuthorizationMysqlTest`가 SELF 범위의 본인 허용과 타인 거부를 검증한다. | 없음 |
| FR/AC-014 | 충족 | 민감 권한을 별도 `PermissionCode`와 `sensitive_operation`으로 관리하며 테스트가 일반 권한과의 분리를 확인한다. | 없음 |
| FR/AC-015 | 충족 | `AuthorizationRollbackMysqlTest`가 변경 로그 실패 시 역할 변경 전체 롤백을 검증한다. | 없음 |
| FR/AC-016 | 충족 | Spring Security와 `PlatformAuthorizationChecker`가 UI와 무관하게 API에서 인증·기능·조직 범위를 강제한다. 직접 API 거부 테스트를 통과했다. | 없음 |
| FR/AC-017 | 충족 | `OrganizationMysqlTest`가 단일 회사, 사업자등록번호 정규화·유일성, 사업장 필드를 검증한다. | 없음 |
| FR/AC-018 | 충족 | `OrganizationMysqlTest`가 부서 버전, 과거 시점, 순환·사업장 불일치, 동시 변경을 검증한다. | 없음 |
| FR/AC-019 | 충족 | `ReferenceMysqlTest`가 그룹·코드 유일성, 활성 필터, 물리 삭제 미제공과 비활성화를 검증한다. | 실제 010 직위 필드의 코드 참조 전환은 별도 스펙이 필요하다. |
| FR/AC-020 | 충족 | `ReferenceMysqlTest`가 종류·연도·범위 조회와 누락 연도 명시적 실패를 검증한다. | 없음 |
| FR/AC-021 | 충족 | `ReferenceMysqlTest`가 기존 행을 변경하지 않는 새 버전과 확인자·시각 이력을 검증한다. | 없음 |

## 010 통합 포트

| 포트 | 실제 어댑터 | 판정 |
|---|---|---|
| `CurrentActorProvider` | `SecurityCurrentActorProvider` | 인증 세션의 계정 ID를 반환하고 그 외에는 fail-closed |
| `AuthorizationChecker` | `PlatformAuthorizationChecker` | 등록·기록카드·발령·퇴사 읽기/쓰기를 기능 권한과 조직 범위로 판정 |
| `OrganizationReader` | `PlatformOrganizationReader` | 회사·사업장·부서의 현재 유효성을 확인 |
| `OrganizationSnapshotReader` | `PlatformOrganizationSnapshotReader` | 기준일 조직 이력과 호출자 범위를 적용 |
| `AccountAccessScheduler` | `PlatformAccountAccessScheduler` | 퇴사일 다음 날 서울 자정부터 로그인·세션·권한을 차단 |

`FileStorage`, `RecordAudit`, `NotificationSender`, `LifecycleTaskPublisher`는 FR-001~021의 구현 대상이 아니므로 기존 fail-closed 대역을 유지한다. 해당 기능은 FR-031 이후의 설계·구현과 함께 교체한다.

## 실행 결과

- 백엔드: `bash gradlew clean test build` — MySQL 8.4.11에서 87개 테스트, 실패·오류·건너뜀 0.
- 프론트엔드: `pnpm test` — 14개 테스트, 실패 0.
- 프론트엔드: `pnpm lint`, `pnpm build` — 통과.
- 정적 검사: `git diff --check` 통과.
- 노출 검사: 저장소 변경분에 비밀번호·세션 원문·개인정보·API 키·로컬 절대경로가 없음을 확인했다.

## 최종 판정

T-900-001~012의 확정 구현 범위는 통과했다. FR/AC-007과 FR/AC-009는 설계 문서에 명시된 후속 범위이므로 플랫폼 전체 FR-001~021이 모두 구현 완료된 것은 아니다. 운영 완료 선언 전 2단계 인증과 비밀번호 재설정을 별도 티켓으로 구현해야 한다.
