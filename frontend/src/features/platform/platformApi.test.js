import assert from 'node:assert/strict'
import test from 'node:test'
import { csrfToken, platformError, platformList, platformRequest, positiveId, query } from './platformApi.js'

test('reads the CSRF cookie and sends it with a protected write', async () => {
  assert.equal(csrfToken('other=a; XSRF-TOKEN=a%2Bb'), 'a+b')
  globalThis.document = { cookie: 'XSRF-TOKEN=a%2Bb' }
  let request
  try {
    const result = await platformRequest('/roles', {
      method: 'POST', body: { roleCode: 'HR' },
      fetcher: async (url, options) => {
        request = { url, ...options }
        return { status: 201, ok: true, json: async () => ({ id: 1 }) }
      },
    })
    assert.equal(request.url, '/api/platform/roles')
    assert.equal(request.credentials, 'same-origin')
    assert.equal(request.headers['X-XSRF-TOKEN'], 'a+b')
    assert.deepEqual(JSON.parse(request.body), { roleCode: 'HR' })
    assert.deepEqual(result, { id: 1 })
  } finally { delete globalThis.document }
})

test('maps authentication and authorization failures without trusting server detail', async () => {
  assert.match(platformError(401, { message: 'secret' }), /로그인/)
  assert.match(platformError(403, { message: 'secret' }), /권한/)
  await assert.rejects(platformRequest('/roles', {
    fetcher: async () => ({ status: 403, ok: false, json: async () => ({ message: 'secret' }) }),
  }), /권한/)
})

test('validates identifiers and encodes query parameters', () => {
  assert.equal(positiveId('12'), 12)
  assert.equal(positiveId('0'), null)
  assert.equal(positiveId('1.2'), null)
  assert.equal(query('/annual-settings', { type: 'A B', year: 2026 }), '/annual-settings?type=A+B&year=2026')
})

test('loads cursor-based administrative lists without dropping later pages', async () => {
  const paths = []
  const rows = await platformList('/roles', async path => {
    paths.push(path)
    return path.includes('afterId=0')
      ? Array.from({ length: 50 }, (_, index) => ({ id: index + 1 }))
      : [{ id: 51 }]
  })
  assert.equal(rows.length, 51)
  assert.match(paths[1], /afterId=50/)
})
