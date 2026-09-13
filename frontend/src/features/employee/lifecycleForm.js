export const toPositiveId = value => {
  const number = Number(value)
  return Number.isSafeInteger(number) && number > 0 ? number : null
}

export function appointmentPayload(form, key) {
  const payload = {
    idempotencyKey: key,
    effectiveDate: form.effectiveDate,
    type: form.type,
    reason: form.reason,
  }
  if (form.type === 'DEPARTMENT_CHANGE') payload.departmentId = toPositiveId(form.departmentId)
  if (form.type === 'WORKPLACE_CHANGE') {
    payload.workplaceId = toPositiveId(form.workplaceId)
    payload.departmentId = toPositiveId(form.departmentId)
  }
  if (form.type === 'POSITION_CHANGE') payload.position = form.position
  if (form.type === 'STATUS_CHANGE') payload.status = form.status
  if (form.evidenceFileId) payload.evidenceFileId = toPositiveId(form.evidenceFileId)
  return payload
}
