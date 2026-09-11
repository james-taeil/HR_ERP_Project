export const employmentTypes = [
  ['REGULAR', '정규직'], ['CONTRACT', '계약직'], ['DAILY', '일용직'],
  ['PART_TIME', '단시간'], ['DISPATCHED', '파견'], ['FREELANCER', '프리랜서'],
].map(([value, label]) => ({ value, label }))

export function buildRegistrationPayload(form, idempotencyKey) {
  const payload = {
    ...form,
    idempotencyKey,
    workplaceId: Number(form.workplaceId),
    departmentId: Number(form.departmentId),
    probationEndDate: form.probationEndDate || null,
  }
  if (!form.foreignWorker) {
    Object.assign(payload, {
      nationality: null, visaType: null, stayFrom: null, stayUntil: null, alienRegistrationNumber: null,
    })
  }
  return payload
}
