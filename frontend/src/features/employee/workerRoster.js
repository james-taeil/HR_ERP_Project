export function workerRosterUrl(employeeId) {
  const value = Number(employeeId)
  if (!/^[1-9]\d*$/.test(String(employeeId)) || !Number.isSafeInteger(value)) return null
  return `/api/hr/employees/${value}/worker-roster.pdf`
}
