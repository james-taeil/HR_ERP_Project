import { useEffect, useState } from 'react'
import { platformRequest } from './platformApi.js'

export default function PlatformAccess({ children }) {
  const [actor, setActor] = useState(null)
  const [loading, setLoading] = useState(true)
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let active = true
    platformRequest('/auth/me').then(result => { if (active) setActor(result) })
      .catch(error => { if (active && error.status !== 401) setMessage(error.message) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [])
  useEffect(() => {
    const expired = () => setActor(null)
    window.addEventListener('platform:unauthorized', expired)
    return () => window.removeEventListener('platform:unauthorized', expired)
  }, [])

  const login = async event => {
    event.preventDefault()
    const element = event.currentTarget
    const form = new FormData(element)
    setBusy(true)
    setMessage('')
    try {
      await platformRequest('/auth/login', { method: 'POST', body: {
        username: form.get('username'), password: form.get('password'),
      } })
      setActor(await platformRequest('/auth/me'))
      element.reset()
    } catch (error) {
      setMessage(error.status === 401 ? '아이디 또는 비밀번호를 확인해 주세요.' : error.message)
    } finally { setBusy(false) }
  }

  const logout = async all => {
    setBusy(true)
    setMessage('')
    try {
      await platformRequest(all ? '/auth/logout-all' : '/auth/logout', { method: 'POST' })
      setActor(null)
    } catch (error) { setMessage(error.message) }
    finally { setBusy(false) }
  }

  if (loading) return <main><p role="status">로그인 상태 확인 중…</p></main>
  if (!actor) return <main className="login-page">
    <header><p className="eyebrow">HR ERP · 플랫폼</p><h1>로그인</h1></header>
    <form onSubmit={login}>
      <label>아이디<input name="username" autoComplete="username" required maxLength="100" /></label>
      <label>비밀번호<input name="password" type="password" autoComplete="current-password" required maxLength="200" /></label>
      <button disabled={busy}>{busy ? '확인 중…' : '로그인'}</button>
    </form>
    <p role="alert">{message}</p>
  </main>
  return <>
    <div className="session-bar"><span>{actor.username} 로그인 중</span>
      <button type="button" disabled={busy} onClick={() => logout(false)}>로그아웃</button>
      <button type="button" disabled={busy} onClick={() => logout(true)}>모든 기기 로그아웃</button>
    </div>
    {message && <p role="alert">{message}</p>}
    {children}
  </>
}
