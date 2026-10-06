# 010 인사관리 FR-027 검증표

검증일: 2026-10-06

| 요구사항 | 구현·검증 근거 | 판정 |
|---|---|---|
| FR/AC-027 법정 항목 | 별지 제16호서식의 성명·성별·생년월일·주소·전화번호·업무·고용/계약·퇴직/해고·경력 항목을 조합하는 MySQL 통합 테스트 | 통과 |
| PDF 계약 | A4 단일 페이지, 한글 포함 글꼴, `application/pdf`, attachment, `no-store` 컨트롤러 테스트와 렌더 확인 | 통과 |
| 누락값 보호 | 기존 데이터의 미확정 성별 또는 빈 주소에 409를 반환하는 통합 테스트 | 통과 |
| 접근 통제와 감사 | `HR_RECORD_READ` 조직 범위 검사와 `WORKER_ROSTER_PDF` 열람 감사 검증 | 통과 |
| 입력 경로 | 단건·XLSX 등록에 성별과 주소를 추가하고 17열 양식의 선택 목록·날짜 형식·예시 렌더를 검증 | 통과 |
| 사용자 화면 | 사원 ID 검증, 안전한 다운로드 URL 생성, 오류 표시 프런트엔드 테스트 | 통과 |

## 실행 결과

- 기능 MySQL 8.4: `WorkerRosterMysqlTest`, `WorkerRosterControllerTest`, `EmployeeBulkMysqlTest` 성공.
- Backend 전체 108건 중 이번 범위 107건 성공. 기존 `AuthenticationMysqlTest.scheduledTerminationAccessBlocksLoginSessionAndPermissionFromSeoulEffectiveDay`의 서울 기준일 회귀 1건은 별도 결함으로 남아 있다.
- Frontend: 18건 테스트, lint, production build 성공.
- 대표 PDF를 150 DPI로 렌더해 표 항목·한글·값 표시를 확인했고 PDF 정보에서 A4 1페이지와 비암호화를 확인했다.

## 범위 경계

PDF는 요청 시 현재 확정 데이터를 조합하며 장기 보존하지 않는다. 과거 시점 증빙과 전자서명은 별도 문서 발급 범위다.
