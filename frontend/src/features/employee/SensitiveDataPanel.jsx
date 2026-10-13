import { useEffect, useState } from 'react'

export default function SensitiveDataPanel({ employeeId }) {
  const [masked, setMasked] = useState(null)
  const [form, setForm] = useState({ residentRegistrationNumber: '', bankAccountNumber: '' })
  const [message, setMessage] = useState('')
  useEffect(() => {
    let active = true
    fetch(`/api/hr/employees/${employeeId}/sensitive-data`).then(async response => {
      if (!active) return
      if (response.ok) setMasked(await response.json())
      else if (response.status !== 404) setMessage('민감정보를 불러오지 못했습니다.')
    })
    return () => { active = false }
  }, [employeeId])
  const save = async event => {
    event.preventDefault(); setMessage('')
    const response = await fetch(`/api/hr/employees/${employeeId}/sensitive-data`, {
      method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(form),
    })
    if (!response.ok) { setMessage('민감정보 저장에 실패했습니다.'); return }
    setMasked(await response.json()); setForm({ residentRegistrationNumber: '', bankAccountNumber: '' })
    setMessage('암호화해 저장했습니다.')
  }
  return <section><h2>민감정보</h2>
    <dl className="grid"><div><dt>주민등록번호</dt><dd>{masked?.residentRegistrationNumber ?? '미등록'}</dd></div>
      <div><dt>계좌번호</dt><dd>{masked?.bankAccountNumber ?? '미등록'}</dd></div></dl>
    <form onSubmit={save}><div className="grid">
      <label><span>주민등록번호</span><input value={form.residentRegistrationNumber} required pattern="[0-9-]{13,14}"
        autoComplete="off" onChange={event => setForm(current => ({ ...current, residentRegistrationNumber: event.target.value }))} /></label>
      <label><span>계좌번호</span><input value={form.bankAccountNumber} required pattern="[0-9-]{6,40}"
        autoComplete="off" onChange={event => setForm(current => ({ ...current, bankAccountNumber: event.target.value }))} /></label>
    </div><button type="submit">민감정보 저장</button></form><p role="status">{message}</p>
  </section>
}
