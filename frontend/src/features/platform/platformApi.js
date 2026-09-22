const base = '/api/platform'

export function csrfToken(cookie = globalThis.document?.cookie ?? '') {
  const value = cookie.split(';').map(part => part.trim()).find(part => part.startsWith('XSRF-TOKEN='))
  return value ? decodeURIComponent(value.slice('XSRF-TOKEN='.length)) : null
}

export function platformError(status, body) {
  if (status === 401) return '로그인이 만료되었습니다. 다시 로그인해 주세요.'
  if (status === 403) return '이 작업을 수행할 권한이 없습니다.'
  if (status === 404) return '요청한 자료를 찾을 수 없습니다.'
  if (status === 409) return '다른 자료와 충돌합니다. 입력값을 확인해 주세요.'
  if (status >= 500) return '서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.'
  return body?.message || '요청을 처리하지 못했습니다.'
}

export async function platformRequest(path, { method = 'GET', body, fetcher = fetch } = {}) {
  const headers = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (!['GET', 'HEAD'].includes(method)) {
    const token = csrfToken()
    if (token) headers['X-XSRF-TOKEN'] = token
  }
  const response = await fetcher(`${base}${path}`, {
    method, credentials: 'same-origin', headers,
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
  })
  if (response.status === 204) return null
  const data = await response.json().catch(() => null)
  if (response.status === 401 && globalThis.window) {
    window.dispatchEvent(new Event('platform:unauthorized'))
  }
  if (!response.ok) throw Object.assign(new Error(platformError(response.status, data)), { status: response.status })
  return data
}

export function positiveId(value) {
  return /^[1-9]\d*$/.test(String(value)) && Number.isSafeInteger(Number(value)) ? Number(value) : null
}

export function query(path, params) {
  return `${path}?${new URLSearchParams(params).toString()}`
}

export async function platformList(path, requester = platformRequest) {
  if (path === '/permissions') return requester(path)
  const result = []
  let afterId = 0
  for (;;) {
    const page = await requester(`${path}${path.includes('?') ? '&' : '?'}afterId=${afterId}&limit=50`)
    result.push(...page)
    if (page.length < 50 || page.at(-1)?.id <= afterId) return result
    afterId = page.at(-1).id
  }
}
