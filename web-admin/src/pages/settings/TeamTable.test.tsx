import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { TeamTable } from './TeamTable'
import type { TeamMemberDto } from '@/api/types'

const member: TeamMemberDto = {
  id: 1,
  email: 'a@b.c',
  fullName: 'Иван Иванов',
  role: 'OPERATOR',
  status: 'ACTIVE',
  createdAt: '2026-05-28T12:00:00Z',
  lastLoginAt: '2026-05-28T11:00:00Z',
  invitedAt: null,
}

describe('TeamTable', () => {
  it('shows empty state when list is empty', () => {
    render(
      <TeamTable
        status="active"
        members={[]}
        currentRole="ADMIN"
        onAction={() => {}}
      />,
    )
    expect(screen.getByText(/нет активных сотрудников/i)).toBeInTheDocument()
  })

  it('renders dash for null lastLoginAt and invitedAt', () => {
    const pending: TeamMemberDto = { ...member, status: 'PENDING', lastLoginAt: null, invitedAt: null }
    render(
      <TeamTable
        status="pending"
        members={[pending]}
        currentRole="ADMIN"
        onAction={() => {}}
      />,
    )
    expect(screen.getByText('—')).toBeInTheDocument()
  })

  it('shows fullName in row', () => {
    render(
      <TeamTable
        status="active"
        members={[member]}
        currentRole="ADMIN"
        onAction={() => {}}
      />,
    )
    expect(screen.getByText('Иван Иванов')).toBeInTheDocument()
  })

  it('does not render district column', () => {
    render(
      <TeamTable
        status="active"
        members={[member]}
        currentRole="ADMIN"
        onAction={() => {}}
      />,
    )
    expect(screen.queryByRole('columnheader', { name: /район/i })).toBeNull()
  })
})
