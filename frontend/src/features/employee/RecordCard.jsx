import { useRef, useState } from 'react'
import { buildRecordPayload, recordError, recordSections } from './recordForm.js'

async function request(url, options) {
  const response = await fetch(url, options)
  if (!response.ok) throw new Error(recordError(response.status))
  return response.json()
}

function RecordSection({ section, entries, employeeId, refresh }) {
  const [editing, setEditing] = useState(null)
  const [form, setForm] = useState({})
  const [key, setKey] = useState('')
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const locked = useRef(false)
  const heading = useRef(null)
  const begin = entry => {
    setEditing(entry ?? { id: null })
    setForm(entry?.data ?? {})
    setKey(crypto.randomUUID())
    setMessage('')
  }
  const save = async event => {
    event.preventDefault()
    if (locked.current) return
    locked.current = true
    setBusy(true)
    setMessage('')
    try {
      const payload = buildRecordPayload(section, form, key, editing.version)
      await request(`/api/hr/employees/${employeeId}/${section.path}${editing.id == null ? '' : `/${editing.id}`}`, {
        method: editing.id == null ? 'POST' : 'PUT',
        headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload),
      })
      setEditing(null)
      setMessage('저장했습니다.')
      heading.current?.focus()
      await refresh()
    } catch (error) { setMessage(error.message) }
    finally { locked.current = false; setBusy(false) }
  }
  return <section aria-labelledby={`${section.key}-heading`}>
    <h2 id={`${section.key}-heading`} tabIndex={-1} ref={heading}>{section.title}</h2>
    {entries.length === 0 ? <p>등록된 자료가 없습니다.</p> : <ul className="record-list">
      {entries.map(entry => <li key={entry.id}>
        <dl>{section.fields.map(([name, label, type]) => <div key={name}>
          <dt>{label}</dt><dd>{type === 'checkbox' ? (entry.data[name] ? '예' : '아니요') : (entry.data[name] ?? '—')}</dd>
        </div>)}</dl>
        <button type="button" disabled={busy} onClick={() => begin(entry)}
          aria-label={`${section.title} ${entry.data.name ?? entry.data.institution} 수정`}>수정</button>
      </li>)}
    </ul>}
    <button type="button" disabled={busy} onClick={() => begin(null)}>{section.title} 추가</button>
    {editing && <form onSubmit={save} className="record-editor" aria-label={`${section.title} 편집`}>
      <fieldset disabled={busy} className="grid">
        <legend>{section.title} {editing.id == null ? '추가' : '수정'}</legend>
        {section.fields.map(([name, label, type, required, maxLength]) => <label key={name} className={type === 'checkbox' ? 'check' : undefined}>
          <span>{label}</span>
          <input name={name} type={type} required={required} maxLength={maxLength}
            checked={type === 'checkbox' ? Boolean(form[name]) : undefined}
            value={type === 'checkbox' ? undefined : form[name] ?? ''}
            min={type === 'number' ? 1 : name === 'endDate' ? form.startDate : name === 'expiresOn' ? form.acquiredDate : undefined}
            step={type === 'number' ? 1 : undefined}
            onChange={({ target }) => {
              setForm(current => ({ ...current, [name]: type === 'checkbox' ? target.checked : target.value }))
              setKey(crypto.randomUUID())
            }} />
        </label>)}
      </fieldset>
      {section.key === 'familyMembers' && <p>공제 대상 여부는 확인된 판정값을 입력하세요. 자동 세법 판정이 아닙니다.</p>}
      {(section.key === 'educations' || section.key === 'careers') && <p>파일 업로드는 미연결입니다. 증빙 ID를 지정하면 서버의 접근권한·안전 검사를 통과해야 저장됩니다.</p>}
      <div className="actions">
        <button type="submit" disabled={busy}>{busy ? '저장 중…' : '저장'}</button>
        <button type="button" disabled={busy} onClick={() => { setEditing(null); heading.current?.focus() }}>취소</button>
      </div>
    </form>}
    <p role="status">{message}</p>
  </section>
}

export default function RecordCard() {
  const [input, setInput] = useState('')
  const [card, setCard] = useState(null)
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const sequence = useRef(0)
  const activeEmployee = useRef(null)
  const load = async (employeeId, refreshing = false) => {
    if (refreshing && activeEmployee.current !== String(employeeId)) return
    activeEmployee.current = String(employeeId)
    const current = ++sequence.current
    setBusy(true)
    setMessage('')
    if (!refreshing) setCard(null)
    try {
      const result = await request(`/api/hr/employees/${employeeId}/record`)
      if (current === sequence.current) setCard(result)
    } catch (error) {
      if (current === sequence.current) { setCard(null); setMessage(error.message) }
    }
    finally { if (current === sequence.current) setBusy(false) }
  }
  return <div className="record-card">
    <h1>인사기록카드</h1>
    <form onSubmit={event => {
      event.preventDefault()
      if (!/^[1-9][0-9]*$/.test(input) || !Number.isSafeInteger(Number(input))) {
        setMessage('올바른 사원 ID를 입력해 주세요.'); return
      }
      load(input)
    }}>
      <label>사원 ID (사번 아님)<input value={input} onChange={e => setInput(e.target.value)}
        inputMode="numeric" pattern="[1-9][0-9]*" required /></label>
      <button disabled={busy}>{busy ? '조회 중…' : '기록카드 조회'}</button>
    </form>
    <p role="status">{message}</p>
    {card && <div key={card.employee.id} className="record-sections">
      <section aria-labelledby="record-employee-heading">
        <h2 id="record-employee-heading">인적사항</h2>
        <dl className="grid">{[
          ['사번', card.employee.employeeNumber], ['성명', card.employee.name],
          ['생년월일', card.employee.birthDate], ['연락처', card.employee.phone],
          ['입사일', card.employee.hireDate], ['고용형태', card.employee.employmentType],
          ['사업장 ID', card.employee.workplaceId], ['부서 ID', card.employee.departmentId],
          ['직위', card.employee.position], ['수습 종료일', card.employee.probationEndDate ?? '—'],
          ['외국인 여부', card.employee.foreignWorker ? '예' : '아니요'],
        ].map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>
      </section>
      {recordSections.map(section => <RecordSection key={section.key} section={section}
        entries={card[section.key]} employeeId={card.employee.id} refresh={() => load(card.employee.id, true)} />)}
      <section><h2>발령 이력</h2><p>아직 구현하지 않은 영역입니다.</p></section>
      <section><h2>계약 이력</h2><p>아직 구현하지 않은 영역입니다.</p></section>
    </div>}
  </div>
}
