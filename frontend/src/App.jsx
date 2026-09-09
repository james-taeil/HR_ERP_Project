import { useState } from 'react'
import './App.css'
import { buildRegistrationPayload, employmentTypes } from './features/employee/employeeForm.js'

const initialForm = {
  name: '', birthDate: '', phone: '', hireDate: '', employmentType: 'REGULAR',
  workplaceId: '', departmentId: '', position: '', probationEndDate: '', foreignWorker: false,
  nationality: '', visaType: '', stayFrom: '', stayUntil: '', alienRegistrationNumber: '',
}

function Field({ label, name, error, children, ...props }) {
  const errorId = `${name}-error`
  return (
    <label>
      <span>{label}</span>
      {children ?? <input name={name} aria-describedby={error ? errorId : undefined} {...props} />}
      {error && <small id={errorId} role="alert">{error}</small>}
    </label>
  )
}

function App() {
  const [form, setForm] = useState(initialForm)
  const [idempotencyKey, setIdempotencyKey] = useState(() => crypto.randomUUID())
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)

  const update = ({ target }) => setForm(current => ({
    ...current,
    [target.name]: target.type === 'checkbox' ? target.checked : target.value,
  }))

  const submit = async event => {
    event.preventDefault()
    setBusy(true)
    setMessage('')
    try {
      const response = await fetch('/api/hr/employees', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(buildRegistrationPayload(form, idempotencyKey)),
      })
      const body = await response.json()
      if (!response.ok) throw new Error(body.message || '등록에 실패했습니다.')
      setMessage(`등록되었습니다. 사번: ${body.employeeNumber}`)
      setForm(initialForm)
      setIdempotencyKey(crypto.randomUUID())
    } catch (error) {
      setMessage(error.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <main>
      <header>
        <p className="eyebrow">HR ERP · 인사관리</p>
        <h1>사원 등록</h1>
        <p>필수 정보를 입력하면 사번과 입사 서류 체크리스트가 함께 생성됩니다.</p>
      </header>
      <form onSubmit={submit} onChange={update}>
        <section aria-labelledby="basic-heading">
          <h2 id="basic-heading">기본 정보</h2>
          <div className="grid">
            <Field label="성명" name="name" value={form.name} required maxLength="100" />
            <Field label="생년월일" name="birthDate" type="date" value={form.birthDate} required />
            <Field label="연락처" name="phone" type="tel" value={form.phone} required maxLength="30" />
            <Field label="입사일" name="hireDate" type="date" value={form.hireDate} required />
            <Field label="고용형태" name="employmentType">
              <select name="employmentType" value={form.employmentType} onChange={update} required>
                {employmentTypes.map(type => <option key={type.value} value={type.value}>{type.label}</option>)}
              </select>
            </Field>
            <Field label="직위" name="position" value={form.position} required maxLength="100" />
            <Field label="사업장 ID" name="workplaceId" type="number" min="1" value={form.workplaceId} required />
            <Field label="부서 ID" name="departmentId" type="number" min="1" value={form.departmentId} required />
            <Field label="수습 종료일" name="probationEndDate" type="date" min={form.hireDate} value={form.probationEndDate} />
          </div>
        </section>
        <section aria-labelledby="foreign-heading">
          <h2 id="foreign-heading">외국인 근로자</h2>
          <label className="check">
            <input name="foreignWorker" type="checkbox" checked={form.foreignWorker} onChange={update} />
            외국인 근로자입니다
          </label>
          {form.foreignWorker && <div className="grid foreign-fields">
            <Field label="국적" name="nationality" value={form.nationality} required />
            <Field label="체류자격" name="visaType" value={form.visaType} required />
            <Field label="체류 시작일" name="stayFrom" type="date" value={form.stayFrom} required />
            <Field label="체류 종료일" name="stayUntil" type="date" min={form.stayFrom} value={form.stayUntil} required />
            <Field label="외국인등록번호" name="alienRegistrationNumber" value={form.alienRegistrationNumber}
              inputMode="numeric" autoComplete="off" required pattern="[0-9-]{13,14}" />
          </div>}
        </section>
        <button type="submit" disabled={busy}>{busy ? '등록 중…' : '사원 등록'}</button>
        <p className="status" aria-live="polite">{message}</p>
      </form>
    </main>
  )
}

export default App
