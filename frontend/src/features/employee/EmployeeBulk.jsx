import { useState } from 'react'
import { confirmationForm, employeeBulkRequest, validationForm } from './employeeBulk.js'

export default function EmployeeBulk() {
  const [file, setFile] = useState(null)
  const [validation, setValidation] = useState(null)
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)

  const choose = event => { setFile(event.target.files?.[0] ?? null); setValidation(null); setMessage('') }
  const validate = async event => {
    event.preventDefault(); if (!file) return
    setBusy(true); setMessage('')
    try {
      const result = await employeeBulkRequest('validations', validationForm(file))
      setValidation(result)
      setMessage(result.errorRows === 0 ? '모든 행이 검증을 통과했습니다.' : `${result.errorRows}개 행을 수정해야 합니다.`)
    } catch (error) { setMessage(error.message) } finally { setBusy(false) }
  }
  const confirm = async () => {
    setBusy(true); setMessage('')
    try {
      const result = await employeeBulkRequest('confirmations', confirmationForm(validation.validationToken, file))
      setMessage(`${result.registeredRows}명을 등록했습니다.`); setValidation(null); setFile(null)
    } catch (error) { setMessage(error.message) } finally { setBusy(false) }
  }

  return <>
    <header><p className="eyebrow">HR ERP · 인사관리</p><h1>사원 일괄 등록</h1>
      <p>지정 양식의 모든 행을 검증한 뒤 오류가 없을 때만 등록합니다.</p></header>
    <p><a href="/api/hr/employees/bulk/template" download>사원 일괄 등록 양식 다운로드</a></p>
    <form onSubmit={validate}>
      <label><span>XLSX 파일</span><input type="file" accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" onChange={choose} required /></label>
      <button type="submit" disabled={busy || !file}>{busy ? '처리 중…' : '전체 행 검증'}</button>
    </form>
    <p className="status" aria-live="polite">{message}</p>
    {validation && <section aria-labelledby="bulk-result"><h2 id="bulk-result">검증 결과</h2>
      <p>전체 {validation.totalRows}행 · 정상 {validation.validRows}행 · 오류 {validation.errorRows}행</p>
      {validation.errors.length > 0 && <table><thead><tr><th>행</th><th>오류</th></tr></thead>
        <tbody>{validation.errors.map((error, index) => <tr key={`${error.rowNumber}-${index}`}><td>{error.rowNumber}</td><td>{error.message}</td></tr>)}</tbody></table>}
      {validation.errorRows === 0 && <button type="button" disabled={busy} onClick={confirm}>등록 확정</button>}
    </section>}
  </>
}
