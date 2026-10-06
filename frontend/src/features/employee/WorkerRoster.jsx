import { useState } from 'react'
import { workerRosterUrl } from './workerRoster.js'

export default function WorkerRoster() {
  const [employeeId, setEmployeeId] = useState('')
  const url = workerRosterUrl(employeeId)
  return <>
    <header><p className="eyebrow">HR ERP · 인사관리</p><h1>근로자 명부</h1>
      <p>현행 법정 서식 기준의 근로자 명부 PDF를 생성합니다.</p></header>
    <section aria-labelledby="worker-roster-download"><h2 id="worker-roster-download">명부 다운로드</h2>
      <label><span>사원 ID</span><input type="number" min="1" value={employeeId}
        onChange={event => setEmployeeId(event.target.value)} /></label>
      {url && <p><a href={url} download>PDF 다운로드</a></p>}
    </section>
  </>
}
