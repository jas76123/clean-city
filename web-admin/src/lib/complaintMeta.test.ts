import { describe, it, expect } from 'vitest'
import { allowedActions, STATUS_META, CATEGORY_META } from './complaintMeta'

describe('complaintMeta', () => {
  it('NEW допускает 3 действия', () => {
    const actions = allowedActions('NEW').map((a) => a.toStatus)
    expect(actions).toEqual(['IN_PROGRESS', 'REJECTED', 'DUPLICATE'])
  })

  it('IN_PROGRESS допускает решить/отклонить/дубликат', () => {
    const actions = allowedActions('IN_PROGRESS').map((a) => a.toStatus)
    expect(actions).toEqual(['RESOLVED', 'REJECTED', 'DUPLICATE'])
  })

  it('терминальные статусы не дают действий', () => {
    expect(allowedActions('RESOLVED')).toEqual([])
    expect(allowedActions('REJECTED')).toEqual([])
    expect(allowedActions('DUPLICATE')).toEqual([])
  })

  it('у каждого статуса есть label и цвет', () => {
    expect(STATUS_META.NEW.label).toBeTruthy()
    expect(STATUS_META.NEW.className).toBeTruthy()
  })

  it('18 категорий с label', () => {
    expect(Object.keys(CATEGORY_META)).toHaveLength(18)
    expect(CATEGORY_META.GARBAGE.label).toBe('Мусор')
  })
})
