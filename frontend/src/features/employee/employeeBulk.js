import { csrfToken, platformError } from '../platform/platformApi.js'

export function validationForm(file) {
  const form = new FormData()
  form.append('file', file)
  return form
}

export async function employeeBulkRequest(path, form, fetcher = fetch) {
  const headers = {}
  const token = csrfToken()
  if (token) headers['X-XSRF-TOKEN'] = token
  const response = await fetcher(`/api/hr/employees/bulk/${path}`, {
    method: 'POST', credentials: 'same-origin', headers, body: form,
  })
  const data = await response.json().catch(() => null)
  if (!response.ok) throw new Error(platformError(response.status, data))
  return data
}

export function confirmationForm(validationToken, file) {
  const form = new FormData()
  form.append('validationToken', validationToken)
  form.append('file', file)
  return form
}
