import { useState } from 'react'
import { analyticsUrl, employmentLabels, tenureLabels } from './hrAnalytics.js'

const today = new Date().toISOString().slice(0, 10)
const yearStart = `${today.slice(0, 4)}-01-01`

function Distribution({ title, values, labels }) {
  return <section><h2>{title}</h2><ul className="record-list">
    {Object.entries(labels).map(([key, label]) => <li key={key}><strong>{label}</strong> {values?.[key] ?? 0}명</li>)}
  </ul></section>
}

export default function HrAnalytics() {
  const [from, setFrom] = useState(yearStart)
  const [to, setTo] = useState(today)
  const [data, setData] = useState(null)
  const [message, setMessage] = useState('')
  const load = async event => {
    event.preventDefault(); setMessage('')
    const url = analyticsUrl(from, to)
    if (!url) { setMessage('기간을 확인하세요.'); return }
    try {
      const response = await fetch(url); const body = await response.json()
      if (!response.ok) throw new Error(body.message || '통계를 불러오지 못했습니다.')
      setData(body)
    } catch (error) { setMessage(error.message) }
  }
  return <>
    <header><p className="eyebrow">HR ERP · 인사관리</p><h1>인사 통계</h1>
      <p>조회 종료일을 기준으로 재직 현황과 월별 입퇴사 추이를 확인합니다.</p></header>
    <form onSubmit={load}><div className="grid">
      <label><span>시작일</span><input type="date" value={from} onChange={event => setFrom(event.target.value)} required /></label>
      <label><span>종료일</span><input type="date" value={to} onChange={event => setTo(event.target.value)} required /></label>
    </div><button type="submit">통계 조회</button><p className="status" aria-live="polite">{message}</p></form>
    {data && <><section><h2>기준일 현황</h2><p><strong>{data.asOf}</strong> 기준 재직자 <strong>{data.activeEmployees}명</strong></p></section>
      <Distribution title="고용형태별 분포" values={data.employmentTypes} labels={employmentLabels} />
      <Distribution title="근속 분포" values={data.tenureBands} labels={tenureLabels} />
      <section><h2>월별 입퇴사 추이</h2><table><thead><tr><th>월</th><th>입사</th><th>퇴사</th></tr></thead><tbody>
        {data.trends.map(item => <tr key={item.month}><td>{item.month}</td><td>{item.hires}</td><td>{item.departures}</td></tr>)}
      </tbody></table></section></>}
  </>
}
