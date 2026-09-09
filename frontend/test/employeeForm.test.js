import assert from 'node:assert/strict'
import test from 'node:test'
import { buildRegistrationPayload, employmentTypes } from '../src/features/employee/employeeForm.js'

test('exposes exactly the six employment types', () => {
  assert.deepEqual(employmentTypes.map(type => type.value), [
    'REGULAR', 'CONTRACT', 'DAILY', 'PART_TIME', 'DISPATCHED', 'FREELANCER',
  ])
})

test('removes conditional foreign fields for a domestic employee', () => {
  const payload = buildRegistrationPayload({ workplaceId: '10', departmentId: '20', foreignWorker: false }, 'same-key')
  assert.equal(payload.workplaceId, 10)
  assert.equal(payload.departmentId, 20)
  assert.equal(payload.alienRegistrationNumber, null)
  assert.equal(payload.idempotencyKey, 'same-key')
})
