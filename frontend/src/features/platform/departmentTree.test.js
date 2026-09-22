import assert from 'node:assert/strict'
import test from 'node:test'
import { nestDepartments } from './departmentTree.js'

test('nests historical department snapshot under its current parent', () => {
  const tree = nestDepartments([
    { departmentId: 2, parentId: 1, name: 'Child' },
    { departmentId: 1, parentId: null, name: 'Root' },
    { departmentId: 3, parentId: 90, name: 'Orphan' },
  ])
  assert.deepEqual(tree.map(node => node.name), ['Root', 'Orphan'])
  assert.equal(tree[0].children[0].name, 'Child')
})
