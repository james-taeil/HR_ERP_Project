export function nestDepartments(departments) {
  const nodes = new Map(departments.map(department => [department.departmentId, { ...department, children: [] }]))
  const roots = []
  for (const node of nodes.values()) {
    const parent = nodes.get(node.parentId)
    if (parent && parent !== node) parent.children.push(node)
    else roots.push(node)
  }
  return roots
}
