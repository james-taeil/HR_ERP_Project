const allowed = ['workplaceId', 'departmentId', 'employmentStatus', 'employmentType',
  'hireDateFrom', 'hireDateTo', 'position', 'query', 'afterId', 'limit']

export function employeeSearchUrl(filters, cursor = null) {
  const params = new URLSearchParams()
  for (const key of allowed) {
    const value = key === 'afterId' ? cursor : filters[key]
    if (value !== undefined && value !== null && value !== '') params.set(key, String(value))
  }
  const query = params.toString()
  return `/api/hr/employees${query ? `?${query}` : ''}`
}
