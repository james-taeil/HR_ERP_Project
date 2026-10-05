import { useState } from 'react'
import { employeeSearchUrl } from './employeeSearch.js'

const initial = { query: '', workplaceId: '', departmentId: '', employmentStatus: '', employmentType: '',
  hireDateFrom: '', hireDateTo: '', position: '', limit: 50 }

export default function EmployeeSearch() {
  const [filters, setFilters] = useState(initial)
  const [items, setItems] = useState([])
  const [nextCursor, setNextCursor] = useState(null)
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)

  const update = ({ target }) => setFilters(current => ({ ...current, [target.name]: target.value }))
  const load = async (cursor = null, append = false) => {
    setBusy(true); setMessage('')
    try {
      const response = await fetch(employeeSearchUrl(filters, cursor))
      const body = await response.json()
      if (!response.ok) throw new Error(body.message || '사원 목록을 불러오지 못했습니다.')
      setItems(current => append ? [...current, ...body.items] : body.items)
      setNextCursor(body.nextCursor)
      if (!append && body.items.length === 0) setMessage('검색 결과가 없습니다.')
    } catch (error) { setMessage(error.message) } finally { setBusy(false) }
  }
  const submit = event => { event.preventDefault(); load() }

  return <>
    <header><p className="eyebrow">HR ERP · 인사관리</p><h1>사원 검색</h1>
      <p>소속과 재직 조건을 조합해 권한 범위 안의 사원을 조회합니다.</p></header>
    <form onSubmit={submit} onChange={update}>
      <section><div className="grid">
        <label><span>이름 또는 사번</span><input name="query" value={filters.query} maxLength="100" /></label>
        <label><span>사업장 ID</span><input name="workplaceId" type="number" min="1" value={filters.workplaceId} /></label>
        <label><span>부서 ID</span><input name="departmentId" type="number" min="1" value={filters.departmentId} /></label>
        <label><span>재직 상태</span><select name="employmentStatus" value={filters.employmentStatus}>
          <option value="">전체</option><option>ACTIVE</option><option>ON_LEAVE</option><option>SUSPENDED</option><option>TERMINATED</option>
        </select></label>
        <label><span>고용형태</span><select name="employmentType" value={filters.employmentType}>
          <option value="">전체</option>{['REGULAR','CONTRACT','DAILY','PART_TIME','DISPATCHED','FREELANCER'].map(v => <option key={v}>{v}</option>)}
        </select></label>
        <label><span>직위</span><input name="position" value={filters.position} maxLength="100" /></label>
        <label><span>입사일 시작</span><input name="hireDateFrom" type="date" value={filters.hireDateFrom} /></label>
        <label><span>입사일 종료</span><input name="hireDateTo" type="date" min={filters.hireDateFrom} value={filters.hireDateTo} /></label>
      </div></section>
      <button type="submit" disabled={busy}>{busy ? '검색 중…' : '검색'}</button>
    </form>
    <p className="status" aria-live="polite">{message}</p>
    {items.length > 0 && <section aria-label="사원 검색 결과"><div className="table-wrap"><table>
      <thead><tr><th>사번</th><th>성명</th><th>입사일</th><th>소속</th><th>직위</th><th>상태</th></tr></thead>
      <tbody>{items.map(item => <tr key={item.id}><td>{item.employeeNumber}</td><td>{item.name}</td><td>{item.hireDate}</td>
        <td>{item.workplaceId} / {item.departmentId}</td><td>{item.position}</td><td>{item.employmentStatus}</td></tr>)}</tbody>
    </table></div></section>}
    {nextCursor != null && <button type="button" disabled={busy} onClick={() => load(nextCursor, true)}>다음 페이지</button>}
  </>
}
