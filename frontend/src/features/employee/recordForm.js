export const recordSections = [
  { key: 'familyMembers', path: 'family-members', title: '가족', fields: [
    ['name', '성명', 'text', true, 100], ['relationship', '관계', 'text', true, 50],
    ['birthDate', '생년월일', 'date', true], ['cohabiting', '동거', 'checkbox'],
    ['dependent', '부양가족', 'checkbox'], ['disabled', '장애', 'checkbox'],
    ['deductionEligible', '공제 대상', 'checkbox'],
  ] },
  { key: 'educations', path: 'educations', title: '학력', fields: [
    ['institution', '기관명', 'text', true, 200], ['major', '전공', 'text', false, 200],
    ['startDate', '시작일', 'date', true], ['endDate', '종료일', 'date'],
    ['evidenceFileId', '증빙 파일 ID', 'number'],
  ] },
  { key: 'careers', path: 'careers', title: '경력', fields: [
    ['institution', '기관명', 'text', true, 200], ['job', '직무', 'text', true, 200],
    ['startDate', '시작일', 'date', true], ['endDate', '종료일', 'date'],
    ['evidenceFileId', '증빙 파일 ID', 'number'],
  ] },
  { key: 'certifications', path: 'certifications', title: '자격', fields: [
    ['name', '자격명', 'text', true, 200], ['issuer', '발급기관', 'text', true, 200],
    ['acquiredDate', '취득일', 'date', true], ['expiresOn', '만료일', 'date'],
  ] },
]

export function buildRecordPayload(section, form, key, version) {
  const data = Object.fromEntries(section.fields.map(([name, , type]) => {
    const value = form[name]
    if (type === 'checkbox') return [name, value === true]
    if (type === 'number') {
      if (value === '' || value == null) return [name, null]
      const id = Number(value)
      if (!Number.isSafeInteger(id) || id <= 0) throw new Error('증빙 파일 ID를 확인하세요.')
      return [name, id]
    }
    return [name, value === '' || value == null ? null : value]
  }))
  const start = data.startDate ?? data.acquiredDate
  const end = data.endDate ?? data.expiresOn
  if (start && end && end < start) throw new Error('종료일은 시작일보다 빠를 수 없습니다.')
  return { idempotencyKey: key, ...(version == null ? {} : { version }), data }
}

export function recordError(status) {
  return ({ 400: '입력값을 확인해 주세요.', 403: '이 사원 자료에 접근할 권한이 없습니다.',
    404: '사원 또는 자료를 찾을 수 없습니다.',
    409: '다른 변경과 충돌했습니다. 새로 조회한 뒤 수정해 주세요.',
    503: '권한·파일·감사 기반이 아직 연결되지 않았습니다.' })[status] ?? '처리에 실패했습니다. 잠시 후 다시 시도해 주세요.'
}
