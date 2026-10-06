import test from 'node:test'
import assert from 'node:assert/strict'
import { analyticsUrl, employmentLabels, tenureLabels } from './hrAnalytics.js'

test('builds an inclusive analytics period URL', () => {
  assert.equal(analyticsUrl('2026-01-01', '2026-12-31'), '/api/hr/analytics?from=2026-01-01&to=2026-12-31')
})

test('rejects invalid and reversed analytics periods', () => {
  assert.equal(analyticsUrl('2026-12-31', '2026-01-01'), null)
  assert.equal(analyticsUrl('bad', '2026-01-01'), null)
})

test('exposes all fixed distribution labels', () => {
  assert.equal(Object.keys(employmentLabels).length, 6)
  assert.equal(Object.keys(tenureLabels).length, 5)
})
