import test from 'node:test'
import assert from 'node:assert/strict'
import { appointmentPayload, toPositiveId } from './lifecycleForm.js'

test('appointment payload contains only fields used by its type', () => {
  const payload = appointmentPayload({
    effectiveDate: '2026-09-14', type: 'WORKPLACE_CHANGE', reason: '전근',
    workplaceId: '2', departmentId: '3', position: '무시', status: 'ON_LEAVE', evidenceFileId: '',
  }, 'key')
  assert.deepEqual(payload, {
    idempotencyKey: 'key', effectiveDate: '2026-09-14', type: 'WORKPLACE_CHANGE',
    reason: '전근', workplaceId: 2, departmentId: 3,
  })
})

test('unsafe IDs never become API IDs', () => {
  assert.equal(toPositiveId('0'), null)
  assert.equal(toPositiveId('9007199254740992'), null)
})
