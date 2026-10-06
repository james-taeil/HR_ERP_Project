export const employmentLabels = {
  REGULAR: '정규직', CONTRACT: '계약직', DAILY: '일용직', PART_TIME: '단시간',
  DISPATCHED: '파견', FREELANCER: '프리랜서',
}

export const tenureLabels = {
  UNDER_1_YEAR: '1년 미만', YEARS_1_TO_3: '1~3년', YEARS_3_TO_5: '3~5년',
  YEARS_5_TO_10: '5~10년', YEARS_10_PLUS: '10년 이상',
}

export function analyticsUrl(from, to) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(from) || !/^\d{4}-\d{2}-\d{2}$/.test(to) || from > to) return null
  return `/api/hr/analytics?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`
}
