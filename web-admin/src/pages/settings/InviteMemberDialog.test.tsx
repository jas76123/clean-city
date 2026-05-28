import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { InviteMemberDialog } from './InviteMemberDialog'

describe('InviteMemberDialog', () => {
  it('disables submit while fullName is empty', () => {
    render(
      <InviteMemberDialog
        open
        onSubmit={() => {}}
        onCancel={() => {}}
      />,
    )
    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'a@b.c' } })
    const submit = screen.getByRole('button', { name: /пригласить/i })
    expect(submit).toBeDisabled()
  })

  it('disables submit while email is empty', () => {
    render(
      <InviteMemberDialog
        open
        onSubmit={() => {}}
        onCancel={() => {}}
      />,
    )
    fireEvent.change(screen.getByLabelText(/фио/i), { target: { value: 'Иван Иванов' } })
    const submit = screen.getByRole('button', { name: /пригласить/i })
    expect(submit).toBeDisabled()
  })

  it('submits with email, fullName, role', () => {
    const onSubmit = vi.fn()
    render(
      <InviteMemberDialog
        open
        onSubmit={onSubmit}
        onCancel={() => {}}
      />,
    )
    fireEvent.change(screen.getByLabelText(/email/i), { target: { value: 'a@b.c' } })
    fireEvent.change(screen.getByLabelText(/фио/i), { target: { value: '  Иван Иванов  ' } })
    fireEvent.click(screen.getByRole('button', { name: /пригласить/i }))
    expect(onSubmit).toHaveBeenCalledWith('a@b.c', 'Иван Иванов', 'OPERATOR')
  })
})
