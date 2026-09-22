import { useCallback, useEffect, useState } from 'react'
import { platformList, platformRequest, positiveId, query } from './platformApi.js'
import { nestDepartments } from './departmentTree.js'

const tabs = [
  ['roles', '역할·권한'], ['scopes', '계정 조직 범위'], ['organization', '사업장·부서'],
  ['codes', '공통 코드'], ['settings', '연도별 설정'],
]

function useList(path) {
  const [items, setItems] = useState([])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const load = useCallback(async () => {
    setBusy(true)
    setError('')
    try { setItems(await platformList(path)) }
    catch (failure) { setItems([]); setError(failure.message) }
    finally { setBusy(false) }
  }, [path])
  useEffect(() => { load() }, [load]) // oxlint-disable-line react/set-state-in-effect -- initial API synchronization
  return { items, error, busy, load }
}

function useAction(refresh) {
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const run = async (path, method, body, form) => {
    setBusy(true)
    setMessage('')
    try {
      await platformRequest(path, { method, body })
      form?.reset()
      setMessage('저장했습니다.')
      await refresh?.()
    } catch (error) { setMessage(error.message) }
    finally { setBusy(false) }
  }
  return { run, message, busy, setMessage }
}

function Status({ message }) { return <p className="status" role={message ? 'status' : undefined}>{message}</p> }

function DepartmentNodes({ nodes }) {
  return <ul className="admin-list">{nodes.map(dept => <li key={dept.departmentId}>
    <div>{dept.name} ({dept.code}) · ID {dept.departmentId} · 사업장 {dept.workplaceId}
      · {dept.effectiveFrom}~{dept.effectiveTo || '현재'} · 정원 {dept.capacity}</div>
    {dept.children.length > 0 && <DepartmentNodes nodes={dept.children} />}
  </li>)}</ul>
}

function Roles() {
  const roles = useList('/roles')
  const permissions = useList('/permissions')
  const action = useAction(roles.load)
  const save = event => {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    action.run('/roles', 'POST', {
      roleCode: data.get('code'), roleName: data.get('name'), permissions: data.getAll('permissions'),
    }, form)
  }
  const update = event => {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    const roleId = positiveId(data.get('roleId'))
    if (!roleId) return action.setMessage('올바른 역할 ID를 입력해 주세요.')
    action.run(`/roles/${roleId}`, 'PATCH', {
      roleName: data.get('roleName') || null,
      active: data.get('active') === 'true',
      ...(data.has('replacePermissions') ? { permissions: data.getAll('updatedPermissions') } : {}),
    }, form)
  }
  return <section aria-labelledby="roles-heading">
    <h2 id="roles-heading">역할·권한</h2>
    {roles.error && <p role="alert">{roles.error}</p>}
    {permissions.error && <p role="alert">{permissions.error}</p>}
    {roles.busy ? <p>역할을 불러오는 중…</p> : <ul className="admin-list">
      {roles.items.map(role => <li key={role.id}>{role.roleName} ({role.roleCode}) · {role.active ? '활성' : '비활성'}
        <small>{role.permissions.join(', ') || '권한 없음'}</small></li>)}
    </ul>}
    <form onSubmit={save}>
      <h3>역할 등록</h3>
      <div className="grid">
        <label>역할 코드<input name="code" required maxLength="100" /></label>
        <label>역할 이름<input name="name" required maxLength="100" /></label>
      </div>
      <fieldset><legend>기능 권한</legend><div className="permission-grid">
        {permissions.items.map(permission => <label className="check" key={permission.permissionCode}>
          <input type="checkbox" name="permissions" value={permission.permissionCode} />
          {permission.permissionCode}{permission.sensitiveOperation ? ' (민감 작업)' : ''}
        </label>)}
      </div></fieldset>
      <button disabled={action.busy || Boolean(permissions.error)}>역할 등록</button>
    </form>
    <form onSubmit={update} className="grid">
      <h3>역할 변경</h3>
      <label>역할 ID<input name="roleId" inputMode="numeric" pattern="[1-9][0-9]*" required /></label>
      <label>새 이름 (선택)<input name="roleName" maxLength="100" /></label>
      <label>상태<select name="active"><option value="true">활성</option><option value="false">비활성</option></select></label>
      <label className="check"><input type="checkbox" name="replacePermissions" />기능 권한도 아래 선택으로 교체</label>
      <fieldset><legend>교체할 기능 권한</legend><div className="permission-grid">
        {permissions.items.map(permission => <label className="check" key={permission.permissionCode}>
          <input type="checkbox" name="updatedPermissions" value={permission.permissionCode} />
          {permission.permissionCode}{permission.sensitiveOperation ? ' (민감 작업)' : ''}
        </label>)}
      </div></fieldset>
      <button disabled={action.busy || Boolean(roles.error)}>역할 변경</button>
    </form>
    <Status message={action.message} />
  </section>
}

function AccountScopes() {
  const roles = useList('/roles')
  const action = useAction()
  const [accountId, setAccountId] = useState('')
  const [assignedRoles, setAssignedRoles] = useState([])
  const [scopes, setScopes] = useState([{ scopeType: 'COMPANY', organizationId: '' }])
  const saveRoles = event => {
    event.preventDefault()
    if (!new FormData(event.currentTarget).has('acknowledge')) {
      return action.setMessage('기존 역할 전체 교체를 확인해 주세요.')
    }
    const id = positiveId(accountId)
    if (!id) return action.setMessage('올바른 계정 ID를 입력해 주세요.')
    action.run(`/accounts/${id}/roles`, 'PUT', {
      roles: assignedRoles.map(roleId => ({ roleId: Number(roleId), validFrom: new Date().toISOString(), validTo: null })),
    })
  }
  const saveScopes = event => {
    event.preventDefault()
    if (!new FormData(event.currentTarget).has('acknowledge')) {
      return action.setMessage('기존 조직 범위 전체 교체를 확인해 주세요.')
    }
    const id = positiveId(accountId)
    if (!id || scopes.some(scope => scope.scopeType !== 'SELF' && !positiveId(scope.organizationId))) {
      return action.setMessage('계정 ID와 조직 ID를 확인해 주세요.')
    }
    action.run(`/accounts/${id}/organization-scopes`, 'PUT', {
      scopes: scopes.map(scope => ({ scopeType: scope.scopeType,
        organizationId: scope.scopeType === 'SELF' ? null : positiveId(scope.organizationId) })),
    })
  }
  return <section aria-labelledby="scopes-heading">
    <h2 id="scopes-heading">계정 역할·조직 범위</h2>
    <p>현재 API는 계정 목록과 기존 배정을 제공하지 않습니다. 계정 ID를 확인해 입력하세요. 저장하면 해당 계정의 기존 배정 전체가 교체됩니다.</p>
    <label>계정 ID<input value={accountId} onChange={event => setAccountId(event.target.value)}
      inputMode="numeric" pattern="[1-9][0-9]*" required /></label>
    {roles.error && <p role="alert">{roles.error}</p>}
    <form onSubmit={saveRoles}>
      <h3>역할 배정</h3>
      <fieldset><legend>새 역할 전체 목록</legend><div className="permission-grid">
        {roles.items.filter(role => role.active).map(role => <label className="check" key={role.id}>
          <input type="checkbox" checked={assignedRoles.includes(String(role.id))} onChange={event =>
            setAssignedRoles(current => event.target.checked ? [...current, String(role.id)] : current.filter(id => id !== String(role.id)))} />
          {role.roleName}
        </label>)}
      </div></fieldset>
      <label className="check"><input type="checkbox" name="acknowledge" required />기존 역할 전체를 이 선택으로 교체합니다</label>
      <button disabled={action.busy || Boolean(roles.error)}>역할 전체 교체</button>
    </form>
    <form onSubmit={saveScopes}>
      <h3>조직 범위 배정</h3>
      {scopes.map((scope, index) => <div className="grid" key={index}>
        <label>범위 유형<select value={scope.scopeType} onChange={event => setScopes(current => current.map((item, i) =>
          i === index ? { ...item, scopeType: event.target.value } : item))}>
          {['SELF', 'DEPARTMENT', 'DEPARTMENT_TREE', 'WORKPLACE', 'COMPANY'].map(type => <option key={type}>{type}</option>)}
        </select></label>
        {scope.scopeType !== 'SELF' && <label>조직 ID<input value={scope.organizationId} inputMode="numeric"
          onChange={event => setScopes(current => current.map((item, i) => i === index ? { ...item, organizationId: event.target.value } : item))}
          required pattern="[1-9][0-9]*" /></label>}
        <button type="button" onClick={() => setScopes(current => current.filter((_, i) => i !== index))}>범위 제거</button>
      </div>)}
      <button type="button" onClick={() => setScopes(current => [...current, { scopeType: 'SELF', organizationId: '' }])}>범위 추가</button>
      <label className="check"><input type="checkbox" name="acknowledge" required />기존 조직 범위 전체를 이 목록으로 교체합니다</label>
      <button disabled={action.busy || Boolean(roles.error)}>범위 전체 교체</button>
    </form>
    <Status message={action.message} />
  </section>
}

function Organization() {
  const workplaces = useList('/workplaces')
  const action = useAction(workplaces.load)
  const [company, setCompany] = useState(null)
  const [companyError, setCompanyError] = useState('')
  const [asOf, setAsOf] = useState('')
  const [departments, setDepartments] = useState([])
  const [treeError, setTreeError] = useState('')
  const loadCompany = useCallback(async () => {
    try { setCompany(await platformRequest('/companies')); setCompanyError('') }
    catch (error) { setCompanyError(error.message) }
  }, [])
  const loadTree = useCallback(async () => {
    try { setDepartments(await platformRequest(asOf ? query('/departments/tree', { asOf }) : '/departments/tree')); setTreeError('') }
    catch (error) { setDepartments([]); setTreeError(error.message) }
  }, [asOf])
  useEffect(() => { loadCompany() }, [loadCompany]) // oxlint-disable-line react/set-state-in-effect -- initial API synchronization
  useEffect(() => { loadTree() }, [loadTree]) // oxlint-disable-line react/set-state-in-effect -- as-of API synchronization
  const saveWorkplace = event => {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    action.run('/workplaces', 'POST', { companyId: company.id, name: data.get('name'),
      registrationNumber: data.get('registrationNumber'), address: data.get('address'),
      industry: data.get('industry'), openedOn: data.get('openedOn') }, form)
  }
  const saveCompany = event => {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    action.run('/companies', 'POST', { name: data.get('name'), activeFrom: data.get('activeFrom') }, form).then(loadCompany)
  }
  const updateWorkplace = event => {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    const id = positiveId(data.get('id'))
    if (!id) return action.setMessage('올바른 사업장 ID를 입력해 주세요.')
    action.run(`/workplaces/${id}`, 'PATCH', { name: data.get('name') || null,
      address: data.get('address') || null, industry: data.get('industry') || null,
      activeTo: data.get('activeTo') || null }, form)
  }
  const saveDepartment = event => {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    action.run('/departments', 'POST', { code: data.get('code'), name: data.get('name'),
      workplaceId: Number(data.get('workplaceId')), parentId: data.get('parentId') ? Number(data.get('parentId')) : null,
      effectiveFrom: data.get('effectiveFrom'), capacity: Number(data.get('capacity')) }, form).then(loadTree)
  }
  const changeDepartment = event => {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    const id = positiveId(data.get('id'))
    if (!id) return action.setMessage('올바른 부서 ID를 입력해 주세요.')
    action.run(`/departments/${id}/versions`, 'POST', {
      name: data.get('name'), workplaceId: Number(data.get('workplaceId')),
      parentId: data.get('parentId') ? Number(data.get('parentId')) : null,
      effectiveFrom: data.get('effectiveFrom'), effectiveTo: data.get('effectiveTo') || null,
      capacity: Number(data.get('capacity')),
    }, form).then(loadTree)
  }
  const closeDepartment = event => {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    const id = positiveId(data.get('id'))
    if (!id) return action.setMessage('올바른 부서 ID를 입력해 주세요.')
    action.run(`/departments/${id}/closure`, 'PATCH', { effectiveTo: data.get('effectiveTo') }, form).then(loadTree)
  }
  return <section aria-labelledby="organization-heading">
    <h2 id="organization-heading">사업장·부서</h2>
    {companyError && <p role="alert">{companyError}</p>}
    {company && <p>회사: {company.name} (ID {company.id})</p>}
    {!company && <form onSubmit={saveCompany} className="grid">
      <label>회사 이름<input name="name" required maxLength="200" /></label>
      <label>시작일<input name="activeFrom" type="date" required /></label>
      <button disabled={action.busy}>회사 등록</button>
    </form>}
    {workplaces.error && <p role="alert">{workplaces.error}</p>}
    <h3>사업장</h3>
    <ul className="admin-list">{workplaces.items.map(place => <li key={place.id}>
      {place.name} · ID {place.id} · {place.registrationNumber} · {place.address} · {place.industry} · {place.openedOn}
    </li>)}</ul>
    {company && <form onSubmit={saveWorkplace} className="grid">
      <label>사업장 이름<input name="name" required maxLength="200" /></label>
      <label>사업자등록번호<input name="registrationNumber" required /></label>
      <label>주소<input name="address" required maxLength="500" /></label>
      <label>업종<input name="industry" required maxLength="200" /></label>
      <label>개시일<input name="openedOn" type="date" required /></label>
      <button disabled={action.busy}>사업장 등록</button>
    </form>}
    <form onSubmit={updateWorkplace} className="grid">
      <h3>사업장 변경</h3>
      <label>사업장 ID<input name="id" inputMode="numeric" pattern="[1-9][0-9]*" required /></label>
      <label>새 이름 (선택)<input name="name" maxLength="200" /></label>
      <label>새 주소 (선택)<input name="address" maxLength="500" /></label>
      <label>새 업종 (선택)<input name="industry" maxLength="200" /></label>
      <label>활성 종료일 (선택)<input name="activeTo" type="date" /></label>
      <button disabled={action.busy}>사업장 변경</button>
    </form>
    <h3>부서 트리</h3>
    <label>조회 기준일<input type="date" value={asOf} onChange={event => setAsOf(event.target.value)} /></label>
    {treeError && <p role="alert">{treeError}</p>}
    <DepartmentNodes nodes={nestDepartments(departments)} />
    <form onSubmit={saveDepartment} className="grid">
      <label>부서 코드<input name="code" required /></label>
      <label>부서 이름<input name="name" required maxLength="200" /></label>
      <label>사업장<select name="workplaceId" required defaultValue=""><option value="" disabled>선택</option>
        {workplaces.items.map(place => <option key={place.id} value={place.id}>{place.name}</option>)}
      </select></label>
      <label>상위 부서 ID (선택)<input name="parentId" inputMode="numeric" pattern="[1-9][0-9]*" /></label>
      <label>적용 시작일<input name="effectiveFrom" type="date" required /></label>
      <label>정원<input name="capacity" type="number" min="0" step="1" required /></label>
      <button disabled={action.busy || Boolean(workplaces.error)}>부서 등록</button>
    </form>
    <form onSubmit={changeDepartment} className="grid">
      <h3>부서 새 이력</h3>
      <label>부서 ID<input name="id" inputMode="numeric" pattern="[1-9][0-9]*" required /></label>
      <label>이름<input name="name" required maxLength="200" /></label>
      <label>사업장<select name="workplaceId" required defaultValue=""><option value="" disabled>선택</option>
        {workplaces.items.map(place => <option key={place.id} value={place.id}>{place.name}</option>)}
      </select></label>
      <label>상위 부서 ID (선택)<input name="parentId" inputMode="numeric" pattern="[1-9][0-9]*" /></label>
      <label>적용 시작일<input name="effectiveFrom" type="date" required /></label>
      <label>적용 종료일 (선택)<input name="effectiveTo" type="date" /></label>
      <label>정원<input name="capacity" type="number" min="0" step="1" required /></label>
      <button disabled={action.busy}>부서 이력 추가</button>
    </form>
    <form onSubmit={closeDepartment} className="grid">
      <h3>부서 폐지</h3>
      <label>부서 ID<input name="id" inputMode="numeric" pattern="[1-9][0-9]*" required /></label>
      <label>유효 종료일<input name="effectiveTo" type="date" required /></label>
      <button disabled={action.busy}>부서 폐지</button>
    </form>
    <Status message={action.message} />
  </section>
}

function Codes() {
  const groups = useList('/code-groups?includeInactive=true')
  const [selectedGroup, setSelectedGroup] = useState('')
  const groupAction = useAction(groups.load)
  const saveGroup = event => {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    groupAction.run('/code-groups', 'POST', { code: data.get('code'), name: data.get('name') }, form)
  }
  return <section aria-labelledby="codes-heading">
    <h2 id="codes-heading">공통 코드</h2>
    {groups.error && <p role="alert">{groups.error}</p>}
    <ul className="admin-list">{groups.items.map(group => <li key={group.id}>{group.name} ({group.code}) · {group.active ? '활성' : '비활성'}
      <button type="button" onClick={() => groupAction.run(`/code-groups/${group.id}`, 'PATCH', { active: !group.active })}>
        {group.active ? '비활성화' : '활성화'}</button>
    </li>)}</ul>
    <form onSubmit={saveGroup} className="grid">
      <label>그룹 코드<input name="code" required maxLength="50" /></label>
      <label>그룹 이름<input name="name" required maxLength="100" /></label>
      <button disabled={groupAction.busy || Boolean(groups.error)}>그룹 등록</button>
    </form>
    <form onSubmit={event => {
      event.preventDefault()
      const form = event.currentTarget
      const data = new FormData(form)
      const id = positiveId(data.get('id'))
      if (!id) return groupAction.setMessage('올바른 그룹 ID를 입력해 주세요.')
      groupAction.run(`/code-groups/${id}`, 'PATCH', { name: data.get('name') }, form)
    }} className="grid">
      <label>변경할 그룹 ID<input name="id" inputMode="numeric" pattern="[1-9][0-9]*" required /></label>
      <label>새 그룹 이름<input name="name" required maxLength="100" /></label>
      <button disabled={groupAction.busy || Boolean(groups.error)}>그룹 이름 변경</button>
    </form>
    <Status message={groupAction.message} />
    <h3>그룹별 코드</h3>
    <label>코드 그룹<select value={selectedGroup} onChange={event => setSelectedGroup(event.target.value)}>
      <option value="">선택</option>{groups.items.filter(group => group.active).map(group => <option key={group.id} value={group.id}>{group.name}</option>)}
    </select></label>
    {selectedGroup && <CodeItems key={selectedGroup} groupId={selectedGroup} />}
  </section>
}

function CodeItems({ groupId }) {
  const codes = useList(query('/codes', { groupId, includeInactive: true }))
  const action = useAction(codes.load)
  const save = event => {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    action.run('/codes', 'POST', { groupId: Number(groupId), code: data.get('code'), name: data.get('name') }, form)
  }
  const rename = event => {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    const id = positiveId(data.get('id'))
    if (!id) return action.setMessage('올바른 코드 ID를 입력해 주세요.')
    action.run(`/codes/${id}`, 'PATCH', { name: data.get('name') }, form)
  }
  return <>
    {codes.error && <p role="alert">{codes.error}</p>}
    <ul className="admin-list">{codes.items.map(code => <li key={code.id}>{code.name} ({code.code}) · {code.active ? '활성' : '비활성'}
      <button type="button" onClick={() => action.run(`/codes/${code.id}`, 'PATCH', { active: !code.active })}>
        {code.active ? '비활성화' : '활성화'}</button>
    </li>)}</ul>
    <form onSubmit={save} className="grid">
      <label>코드<input name="code" required maxLength="50" /></label>
      <label>이름<input name="name" required maxLength="100" /></label>
      <button disabled={action.busy || Boolean(codes.error)}>코드 등록</button>
    </form>
    <form onSubmit={rename} className="grid">
      <label>변경할 코드 ID<input name="id" inputMode="numeric" pattern="[1-9][0-9]*" required /></label>
      <label>새 코드 이름<input name="name" required maxLength="100" /></label>
      <button disabled={action.busy || Boolean(codes.error)}>코드 이름 변경</button>
    </form>
    <Status message={action.message} />
  </>
}

function AnnualSettings() {
  const [key, setKey] = useState({ type: '', year: String(new Date().getFullYear()), scopeType: 'COMPANY', scopeId: '' })
  const [current, setCurrent] = useState(null)
  const [history, setHistory] = useState([])
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const lookup = async event => {
    event?.preventDefault()
    if (!positiveId(key.scopeId)) return setMessage('올바른 범위 ID를 입력해 주세요.')
    setBusy(true)
    setMessage('')
    try {
      const params = { ...key }
      const [latest, versions] = await Promise.all([
        platformRequest(query('/annual-settings', params)),
        platformRequest(query('/annual-settings/history', params)),
      ])
      setCurrent(latest)
      setHistory(versions)
    } catch (error) { setCurrent(null); setHistory([]); setMessage(error.message) }
    finally { setBusy(false) }
  }
  const save = async event => {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    let value
    try { value = JSON.parse(data.get('value')) }
    catch { setMessage('설정값은 올바른 JSON이어야 합니다.'); return }
    if (!positiveId(key.scopeId)) { setMessage('올바른 범위 ID를 입력해 주세요.'); return }
    setBusy(true)
    setMessage('')
    try {
      await platformRequest('/annual-settings', { method: 'POST', body: {
        ...key, year: Number(key.year), scopeId: Number(key.scopeId), value,
        sourceReference: data.get('sourceReference'),
      } })
      form.reset()
      setMessage('새 버전을 저장했습니다. 조회 버튼으로 이력을 확인하세요.')
    } catch (error) { setMessage(error.message) }
    finally { setBusy(false) }
  }
  return <section aria-labelledby="settings-heading">
    <h2 id="settings-heading">연도별 설정</h2>
    <p>값은 대상 연도에만 적용됩니다. 변경하면 기존 행 대신 새 버전이 추가됩니다.</p>
    <form onSubmit={lookup} className="grid">
      <label>설정 종류<input value={key.type} onChange={event => setKey({ ...key, type: event.target.value })} required maxLength="100" /></label>
      <label>적용 연도<input type="number" min="2000" max="9999" value={key.year} onChange={event => setKey({ ...key, year: event.target.value })} required /></label>
      <label>범위 유형<input value={key.scopeType} onChange={event => setKey({ ...key, scopeType: event.target.value })} required maxLength="30" /></label>
      <label>범위 ID<input value={key.scopeId} onChange={event => setKey({ ...key, scopeId: event.target.value })} required inputMode="numeric" pattern="[1-9][0-9]*" /></label>
      <button disabled={busy}>설정 조회</button>
    </form>
    {current && <><h3>현재 버전 {current.version}</h3><pre>{JSON.stringify(current.value, null, 2)}</pre>
      <p>근거: {current.sourceReference} · 확인자 ID {current.verifiedBy} · {current.createdAt}</p></>}
    {history.length > 0 && <><h3>변경 이력</h3><ul className="admin-list">{history.map(item => <li key={item.id}>
      버전 {item.version} · {item.createdAt} · 확인자 ID {item.verifiedBy} · {item.sourceReference}
    </li>)}</ul></>}
    <form onSubmit={save}>
      <h3>새 버전 추가</h3>
      <label>설정값 (JSON)<textarea name="value" required rows="5" /></label>
      <label>근거 출처<input name="sourceReference" required maxLength="500" /></label>
      <button disabled={busy}>새 버전 저장</button>
    </form>
    <Status message={message} />
  </section>
}

export default function PlatformAdmin() {
  const [tab, setTab] = useState('roles')
  const panels = { roles: Roles, scopes: AccountScopes, organization: Organization, codes: Codes, settings: AnnualSettings }
  const Panel = panels[tab]
  return <>
    <header><p className="eyebrow">HR ERP · 플랫폼 관리</p><h1>관리 화면</h1>
      <p>권한 검사는 서버가 수행합니다. 권한이 없으면 각 화면에서 거부 결과를 표시합니다.</p></header>
    <nav className="actions" aria-label="플랫폼 관리 메뉴">
      {tabs.map(([id, label]) => <button key={id} type="button" aria-current={tab === id ? 'page' : undefined}
        onClick={() => setTab(id)}>{label}</button>)}
    </nav>
    <Panel />
  </>
}
