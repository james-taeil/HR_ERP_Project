import test from 'node:test'
import assert from 'node:assert/strict'
import { employeeSearchUrl } from './employeeSearch.js'

test('combines filters and cursor without unknown fields', () => {
  const url = employeeSearchUrl({ departmentId: 7, employmentStatus: 'ACTIVE', query: '홍%', limit: 20,
    secret: 'never' }, 42)
  assert.equal(url, '/api/hr/employees?departmentId=7&employmentStatus=ACTIVE&query=%ED%99%8D%25&afterId=42&limit=20')
  assert.equal(url.includes('secret'), false)
})
