import test from 'node:test'
import assert from 'node:assert/strict'
import { buildRecordPayload, recordSections, recordError } from './recordForm.js'

test('family flags preserve an explicit non-deductible dependent', () => {
  const payload = buildRecordPayload(recordSections[0], {
    name: '테스트', relationship: '자녀', birthDate: '2020-01-01',
    cohabiting: true, dependent: true, disabled: false, deductionEligible: false,
  }, 'key', 2)
  assert.equal(payload.data.dependent, true)
  assert.equal(payload.data.deductionEligible, false)
  assert.equal(payload.version, 2)
})
test('optional dates and evidence IDs become null, not zero', () => {
  const payload = buildRecordPayload(recordSections[1], { institution: '학교', startDate: '2020-01-01', endDate: '', evidenceFileId: '' }, 'key')
  assert.equal(payload.data.endDate, null)
  assert.equal(payload.data.evidenceFileId, null)
  assert.equal('version' in payload, false)
})
test('rejects reversed dates and unsafe numeric IDs', () => {
  assert.throws(() => buildRecordPayload(recordSections[2], { startDate: '2020-01-02', endDate: '2020-01-01' }, 'key'))
  assert.throws(() => buildRecordPayload(recordSections[1], { evidenceFileId: '9007199254740993' }, 'key'))
  assert.throws(() => buildRecordPayload(recordSections[1], { evidenceFileId: '-1' }, 'key'))
})
test('certification may have no expiration or expire on acquisition day', () => {
  assert.equal(buildRecordPayload(recordSections[3], { acquiredDate: '2020-01-01' }, 'key').data.expiresOn, null)
  assert.doesNotThrow(() => buildRecordPayload(recordSections[3], { acquiredDate: '2020-01-01', expiresOn: '2020-01-01' }, 'key'))
})
test('retry keeps the key and only section fields are submitted', () => {
  const data = { name: '자격', employeeNumber: 'immutable', issuer: '기관', acquiredDate: '2020-01-01' }
  assert.deepEqual(buildRecordPayload(recordSections[3], data, 'retry'), buildRecordPayload(recordSections[3], data, 'retry'))
  assert.equal('employeeNumber' in buildRecordPayload(recordSections[3], data, 'retry').data, false)
  assert.match(recordError(409), /충돌/)
  assert.match(recordError(503), /연결/)
})
