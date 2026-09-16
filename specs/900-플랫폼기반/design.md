# Design: 900 플랫폼 기반 — 인증·권한·조직

> 근거: `specs/900-플랫폼기반/spec.md` FR-001~021, `specs/000-product/design.md`  
> 범위: 계정·세션, 서버 권한, 회사·사업장·부서, 공통 코드와 연도별 설정  
> 티켓: #17

## 1. 목표와 제외

이번 단계는 010 인사관리의 조건부 900 포트를 실제 구현으로 교체할 수 있는 최소 플랫폼 기반을 만든다.

포함:
- 계정과 사원의 1:1 연결, 로그인·로그아웃·세션 만료
- 역할 기반 기능 권한, 조직 범위, 본인 데이터 접근
- 회사 1개, 사업장과 시점 이력 부서
- 직위·직책·직무 공통 코드와 연도별 설정 이력
- 권한 변경 및 로그인 이력

제외:
- FR-007 2단계 인증 구현
- FR-009 비밀번호 재설정 구현
- FR-022 이후 결재·알림·파일·AI·배치·운영 기능
- 외부 SSO, Redis, JWT, 메시지 브로커

FR-007과 FR-009는 후속 설계를 막지 않도록 계정 모델과 공개 계약의 확장 지점만 보존한다.

## 2. 구조

기존 모듈러 모놀리스 안에 `platform` 모듈을 둔다.

```text
platform/
  account/        계정·로그인·세션
  authorization/  역할·기능 권한·조직 범위
  organization/   회사·사업장·부서 시점 이력
  reference/      공통 코드·연도별 설정
  api/            REST DTO와 공통 보안 필터
```

각 기능은 API → application → domain → infrastructure 방향을 유지한다. 010은 900의 JPA 엔티티나 Repository를 참조하지 않고 공개 포트만 호출한다.

## 3. 인증과 세션

### 3.1 계정

`accounts`는 `employee_id`를 유일하게 보유한다. 사용자명은 대소문자를 정규화한 값으로 유일하다. 비밀번호는 Spring Security `PasswordEncoder`의 bcrypt 해시만 저장하고 평문, 복호화 키, 원문 로그를 남기지 않는다.

계정 상태:
- `ACTIVE`: 로그인 가능
- `LOCKED`: 잠금 만료 전 로그인 불가
- `DISABLED`: 로그인 불가

퇴사 계정 비활성 요청은 010이 공개 포트로 전달한다. 적용일 다음 날 00:00 Asia/Seoul부터 `DISABLED`가 되며 활성 세션도 무효화한다.

### 3.2 로그인

`POST /api/platform/auth/login`

입력: 사용자명, 비밀번호.  
성공: 사용자 요약과 세션 만료 시각.  
실패: 계정 없음, 비밀번호 불일치, 비활성 계정을 외부에는 동일한 `AUTHENTICATION_FAILED`로 응답한다.

내부에서는 성공·실패, 시각, IP, User-Agent를 로그인 이력에 기록한다. 연속 실패 횟수와 잠금 시간은 환경 설정으로 주입한다. 구체 기본값은 구현 전 제품 결정으로 확정하며 코드 상수로 숨기지 않는다.

### 3.3 세션

JWT 대신 서버 저장형 불투명 세션을 사용한다. 무작위 토큰 원문은 `Secure`, `HttpOnly`, `SameSite=Lax` 쿠키로만 전달하고 DB에는 SHA-256 다이제스트만 저장한다. 이 방식은 전체 로그아웃, 퇴사 차단, 권한 변경의 다음 요청 반영을 별도 블랙리스트 없이 지원한다.

보호된 쓰기 요청은 Spring Security CSRF 보호를 적용한다.

- `POST /api/platform/auth/logout`: 현재 세션 무효화
- `POST /api/platform/auth/logout-all`: 계정의 모든 세션 무효화
- 모든 보호 요청: 만료·취소·계정 상태를 다시 확인
- 권한 변경: 세션에 권한 스냅샷을 저장하지 않고 다음 요청에 현재 역할을 조회

## 4. 권한 모델

### 4.1 데이터 모델

- `permissions`: `HR_EMPLOYEE_READ`, `HR_EMPLOYEE_WRITE` 같은 안정된 기능 코드
- `roles`: 역할명과 활성 상태
- `role_permissions`: 역할과 기능 권한 연결
- `account_roles`: 계정과 역할 연결, 유효 시작·종료 시각
- `organization_scopes`: 계정 또는 역할의 범위 유형과 조직 ID
- `authorization_change_logs`: 변경자, 대상, 변경 전후, 시각을 삽입 전용으로 보존

조직 범위 유형:
- `SELF`: 본인
- `DEPARTMENT`: 지정 부서
- `DEPARTMENT_TREE`: 지정 부서와 하위 부서
- `WORKPLACE`: 지정 사업장
- `COMPANY`: 회사 전체

기능 권한과 조직 범위는 별도로 판정한다. 기능 권한이 있어도 대상 사원이 조직 범위를 벗어나면 거부한다.

### 4.2 서버 강제

Spring Security의 인증 필터가 세션을 확인하고 application 계층의 `AuthorizationService`가 기능 코드·조직 범위·본인 여부를 함께 판정한다. React의 메뉴 숨김은 편의 기능일 뿐 보안 판정이 아니다.

010 연결 계약:

```java
interface AuthorizationPort {
    void require(String permission, long targetEmployeeId);
}

interface OrganizationReader {
    OrganizationSnapshot requireValidAssignment(
        long workplaceId, long departmentId, LocalDate asOf);
}
```

기존 010 포트 이름이 다르면 의미를 유지한 최소 어댑터만 추가한다. 권한 또는 조직 조회 실패는 fail-closed로 거부한다.

## 5. 조직과 기준정보

### 5.1 회사와 사업장

단일 회사만 허용한다. 회사와 사업장은 삭제 대신 활성 기간을 가진다. 사업장에는 사업자등록번호, 소재지, 업종, 개시일을 저장한다. 사업자등록번호는 정규화된 값으로 유일하다.

### 5.2 부서 시점 이력

부서의 안정 식별자와 버전 행을 분리한다.

- `departments`: 변경되지 않는 부서 ID
- `department_versions`: 이름, 코드, 상위 부서, 사업장, 유효 시작일·종료일

같은 부서의 유효기간은 겹칠 수 없다. 이동·명칭 변경·폐지는 기존 버전을 수정하지 않고 종료한 뒤 새 버전을 추가한다. `asOf`가 없으면 회사 시간대의 오늘을 사용한다. 트리 조회는 대상 시점에 유효한 버전만 조합한다.

순환 참조, 자기 자신을 상위 부서로 지정, 다른 사업장의 상위 부서 연결을 거부한다. 소속 사원이 남은 부서 폐지는 010 조회 포트를 통해 거부하고 이동 대상 ID를 반환한다.

### 5.3 공통 코드

`code_groups`와 `codes`로 직위·직책·직무를 관리한다. 업무 데이터가 참조 중인 코드는 삭제하지 않고 비활성화한다. 사용 여부 판정은 도메인별 공개 포트로 확인한다.

### 5.4 연도별 설정

`annual_settings`는 설정 종류, 적용 연도, 적용 범위, 버전, 값(JSON), 근거 출처, 확인자, 생성 시각을 보존한다. 변경은 새 버전을 추가하며 기존 행을 수정·삭제하지 않는다. 대상 연도 값이 없으면 이전 연도 값을 대신 사용하지 않는다.

## 6. REST API

- `POST /api/platform/auth/login`
- `POST /api/platform/auth/logout`
- `POST /api/platform/auth/logout-all`
- `GET /api/platform/auth/me`
- `POST/GET/PATCH /api/platform/accounts`
- `POST/GET/PATCH /api/platform/roles`
- `PUT /api/platform/accounts/{id}/roles`
- `PUT /api/platform/accounts/{id}/organization-scopes`
- `POST/GET/PATCH /api/platform/workplaces`
- `POST /api/platform/departments`
- `POST /api/platform/departments/{id}/versions`
- `GET /api/platform/departments/tree?asOf=YYYY-MM-DD`
- `POST/GET/PATCH /api/platform/code-groups`
- `POST/GET/PATCH /api/platform/codes`
- `POST/GET /api/platform/annual-settings`

목록 API는 커서 기반 페이지네이션을 사용한다. 오류는 기존 공통 오류 형식을 재사용하고 모든 응답의 추적 ID 규칙을 유지한다.

## 7. 트랜잭션과 동시성

- 로그인 실패 횟수 갱신과 잠금 판정은 계정 행 잠금 안에서 수행한다.
- 역할·권한 변경과 변경 로그 기록은 하나의 트랜잭션이다.
- 부서 현재 버전 종료와 새 버전 추가는 하나의 트랜잭션이다.
- 조직 유효기간 겹침은 사전 검사와 DB 고유 보조키/잠금으로 동시 요청까지 막는다.
- 연도별 설정 새 버전과 변경 이력은 하나의 트랜잭션이다.
- 감사·권한 변경 로그 저장 실패 시 대상 변경도 실패한다.

## 8. 보안

- 비밀번호와 세션 원문을 로그·응답·DB에 남기지 않는다.
- 로그인 응답 시간 차이로 계정 존재 여부가 드러나지 않도록 더미 해시 검증을 수행한다.
- 쿠키 인증에는 CSRF 보호를 적용한다.
- 관리자용 계정·권한·조직 API는 별도 관리 권한으로 보호한다.
- IP와 User-Agent는 로그인 이력 목적에 필요한 범위로 제한하며 보존 정책 확정 전 자동 삭제를 구현하지 않는다.
- 오류 응답에 스택 추적, SQL, 내부 클래스명을 노출하지 않는다.

## 9. FR·AC 추적

| 요구사항 | 설계 근거 | 검증 |
|---|---|---|
| FR/AC-001~006 | 3장 | 해시 비노출, 동일 실패 응답, 잠금, 만료, 현재·전체 로그아웃 |
| FR/AC-007 | 후속 범위 | 2단계 인증 활성 필드와 경계만 보존 |
| FR/AC-008 | 3.1, 3.3 | 퇴사 다음 날 비활성 및 세션 종료 |
| FR/AC-009 | 후속 범위 | 재설정 토큰 구현은 별도 티켓 |
| FR/AC-010 | 3.2 | 성공·실패 로그인 이력 |
| FR/AC-011~016 | 4장 | 역할·기능·조직·본인·민감 권한 서버 판정과 변경 이력 |
| FR/AC-017~018 | 5.1~5.2 | 사업장 관리, 부서 시점 버전과 트리 |
| FR/AC-019 | 5.3 | 공통 코드와 사용 중 삭제 거부 |
| FR/AC-020~021 | 5.4 | 대상 연도 조회, 새 버전 이력 |

## 10. 검증 전략

- Testcontainers MySQL 8.4로 Flyway 재실행과 Hibernate validate를 확인한다.
- 계정 없음/오류 비밀번호의 상태·본문이 같은지 확인한다.
- 동시 로그인 실패가 잠금 기준을 우회하지 못하는지 확인한다.
- 만료·현재 로그아웃·전체 로그아웃·퇴사 비활성 세션을 확인한다.
- 기능 권한과 조직 범위의 허용·거부 조합을 전수 확인한다.
- 여러 기준일의 부서 트리, 기간 겹침, 순환, 사업장 불일치를 확인한다.
- 권한 변경 로그 실패 시 변경 전체가 롤백되는지 확인한다.
- 비밀번호·세션 토큰·스택 추적이 DB와 로그·응답에 노출되지 않는지 확인한다.

## 11. 구현 전 확정할 값

다음 값은 환경 설정 항목까지 설계하되 실제 기본값은 구현 티켓에서 사용자가 확정한다.

- 비밀번호 최소 길이·문자 조합·재사용 금지 개수
- 로그인 실패 잠금 횟수와 잠금 시간
- 세션 유효 시간과 유휴 시간
- 로그인·권한 변경 이력 보존 기간

이 값이 없어도 조직·권한 데이터 모델과 공개 포트 구현은 먼저 진행할 수 있다.
