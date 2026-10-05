import test from 'node:test'
import assert from 'node:assert/strict'
import { confirmationForm, employeeBulkRequest, validationForm } from './employeeBulk.js'

test('validation and confirmation preserve the selected file', async () => {
  const file = new Blob(['xlsx'])
  assert.equal(await validationForm(file).get('file').text(), 'xlsx')
  const confirmation = confirmationForm('token-1', file)
  assert.equal(confirmation.get('validationToken'), 'token-1')
  assert.equal(await confirmation.get('file').text(), 'xlsx')
})

test('bulk writes include the CSRF token without overriding multipart content type', async () => {
  const originalDocument = globalThis.document
  globalThis.document = { cookie: 'XSRF-TOKEN=token%2Bvalue' }
  try {
    const result = await employeeBulkRequest('validations', new FormData(), async (url, request) => {
      assert.equal(url, '/api/hr/employees/bulk/validations')
      assert.equal(request.credentials, 'same-origin')
      assert.equal(request.headers['X-XSRF-TOKEN'], 'token+value')
      assert.equal(request.headers['Content-Type'], undefined)
      return { ok: true, json: async () => ({ validRows: 1 }) }
    })
    assert.equal(result.validRows, 1)
  } finally {
    globalThis.document = originalDocument
  }
})
