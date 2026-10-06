import test from 'node:test'
import assert from 'node:assert/strict'
import { workerRosterUrl } from './workerRoster.js'

test('creates a roster URL only for a safe positive employee ID', () => {
  assert.equal(workerRosterUrl('7'), '/api/hr/employees/7/worker-roster.pdf')
  for (const value of ['', '0', '-1', '1.5', '9007199254740992']) assert.equal(workerRosterUrl(value), null)
})
