import { useState } from 'react'
import { appointmentPayload, toPositiveId } from './lifecycleForm.js'

const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul' }).format(new Date())
const initial = { effectiveDate: today, type: 'DEPARTMENT_CHANGE', reason: '', workplaceId: '',
  departmentId: '', position: '', status: 'ON_LEAVE', evidenceFileId: '' }

async function json(response) {
  const body = await response.json()
  if (!response.ok) throw new Error(body.message || '요청에 실패했습니다.')
  return body
}

export default function LifecyclePanel() {
  const [employeeId, setEmployeeId] = useState('')
  const [form, setForm] = useState(initial)
  const [appointmentKey, setAppointmentKey] = useState(() => crypto.randomUUID())
  const [terminationKey, setTerminationKey] = useState(() => crypto.randomUUID())
  const [terminationDate, setTerminationDate] = useState(today)
  const [reasonCode, setReasonCode] = useState('VOLUNTARY')
  const [certificateRequired, setCertificateRequired] = useState(false)
  const [appointments, setAppointments] = useState([])
  const [date, setDate] = useState(today)
  const [organization, setOrganization] = useState(null)
  const [headcount, setHeadcount] = useState(null)
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)

  const id = () => {
    const value = toPositiveId(employeeId)
    if (!value) throw new Error('올바른 사원 ID를 입력하세요.')
    return value
  }
  const run = async action => {
    setMessage('')
    setBusy(true)
    try { await action() } catch (error) { setMessage(error.message) }
    finally { setBusy(false) }
  }
  const updateAppointment = (field, value) => {
    setForm(current => ({ ...current, [field]: value }))
    setAppointmentKey(crypto.randomUUID())
  }
  const submitAppointment = event => {
    event.preventDefault()
    run(async () => {
      const response = await fetch(`/api/hr/employees/${id()}/appointments`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(appointmentPayload(form, appointmentKey)),
      })
      const saved = await json(response)
      setAppointmentKey(crypto.randomUUID())
      setMessage(saved.status === 'SCHEDULED' ? '발령을 예약했습니다.' : '발령을 적용했습니다.')
    })
  }
  const loadAppointments = () => run(async () => {
    setAppointments(await json(await fetch(`/api/hr/employees/${id()}/appointments`)))
  })
  const terminate = event => {
    event.preventDefault()
    run(async () => {
      await json(await fetch(`/api/hr/employees/${id()}/termination`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ idempotencyKey: terminationKey, terminationDate, reasonCode,
          separationCertificateRequired: certificateRequired }),
      }))
      setTerminationKey(crypto.randomUUID())
      setMessage('퇴사 처리를 확정했습니다.')
    })
  }
  const loadOrganization = () => run(async () => {
    const [org, counts] = await Promise.all([
      json(await fetch(`/api/hr/organization-chart?date=${date}`)),
      json(await fetch(`/api/hr/headcount?date=${date}`)),
    ])
    setOrganization(org); setHeadcount(counts)
  })

  return <>
    <header><p className="eyebrow">HR ERP · 인사관리</p><h1>재직·발령 관리</h1></header>
    <section>
      <label>사원 ID<input type="number" min="1" value={employeeId} onChange={e => {
        setEmployeeId(e.target.value); setAppointmentKey(crypto.randomUUID()); setTerminationKey(crypto.randomUUID())
      }} /></label>
      <button type="button" disabled={busy} onClick={loadAppointments}>발령 이력 조회</button>
      <ul className="record-list">{appointments.map(item => <li key={item.id}>
        {item.effectiveDate} · {item.type} · {item.appointmentStatus} · {item.reason}
      </li>)}</ul>
    </section>
    <form onSubmit={submitAppointment}>
      <section><h2>발령 등록</h2><div className="grid">
        <label>적용일<input type="date" value={form.effectiveDate} onChange={e => updateAppointment('effectiveDate', e.target.value)} required /></label>
        <label>종류<select value={form.type} onChange={e => updateAppointment('type', e.target.value)}>
          <option value="DEPARTMENT_CHANGE">부서 이동</option><option value="WORKPLACE_CHANGE">사업장 이동</option>
          <option value="POSITION_CHANGE">직위 변경</option><option value="STATUS_CHANGE">재직 상태 변경</option>
        </select></label>
        {form.type === 'WORKPLACE_CHANGE' && <label>새 사업장 ID<input type="number" min="1" value={form.workplaceId} onChange={e => updateAppointment('workplaceId', e.target.value)} required /></label>}
        {(form.type === 'DEPARTMENT_CHANGE' || form.type === 'WORKPLACE_CHANGE') && <label>새 부서 ID<input type="number" min="1" value={form.departmentId} onChange={e => updateAppointment('departmentId', e.target.value)} required /></label>}
        {form.type === 'POSITION_CHANGE' && <label>새 직위<input value={form.position} onChange={e => updateAppointment('position', e.target.value)} required /></label>}
        {form.type === 'STATUS_CHANGE' && <label>새 상태<select value={form.status} onChange={e => updateAppointment('status', e.target.value)}><option value="ACTIVE">재직</option><option value="ON_LEAVE">휴직</option><option value="SUSPENDED">정직</option></select></label>}
        <label>발령 사유<input value={form.reason} onChange={e => updateAppointment('reason', e.target.value)} required /></label>
        <label>근거 파일 ID<input type="number" min="1" value={form.evidenceFileId} onChange={e => updateAppointment('evidenceFileId', e.target.value)} /></label>
      </div></section><button type="submit" disabled={busy}>발령 저장</button>
    </form>
    <form onSubmit={terminate}>
      <section><h2>퇴사 확정</h2><div className="grid">
        <label>퇴사일<input type="date" value={terminationDate} onChange={e => { setTerminationDate(e.target.value); setTerminationKey(crypto.randomUUID()) }} required /></label>
        <label>퇴사 사유 코드<input value={reasonCode} pattern="[A-Z0-9_-]+" onChange={e => { setReasonCode(e.target.value); setTerminationKey(crypto.randomUUID()) }} required /></label>
        <label className="check"><input name="certificate" type="checkbox" checked={certificateRequired}
          onChange={e => { setCertificateRequired(e.target.checked); setTerminationKey(crypto.randomUUID()) }} />이직확인서 필요</label>
      </div></section><button type="submit" disabled={busy}>퇴사 확정</button>
    </form>
    <section><h2>기준일 조직·정원 현황</h2>
      <label>기준일<input type="date" value={date} onChange={e => setDate(e.target.value)} /></label>
      <button type="button" disabled={busy} onClick={loadOrganization}>현황 조회</button>
      {organization && <p>부서 {organization.departments.length}개 · 사원 {organization.employees.length}명</p>}
      <ul className="record-list">{headcount?.departments.map(item => <li key={item.departmentId}>
        부서 {item.departmentId}: 정원 {item.capacity} / 현원 {item.current} / 차이 {item.difference}
      </li>)}</ul>
    </section>
    <p className="status" aria-live="polite">{message}</p>
  </>
}
