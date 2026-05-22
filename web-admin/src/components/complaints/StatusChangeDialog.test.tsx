import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StatusChangeDialog } from './StatusChangeDialog'
import type { Complaint } from '@/api/types'

const complaint: Complaint = {
  id: 7, authorId: 1, category: 'GARBAGE', title: 'Свалка', description: 'd',
  latitude: 43.6, longitude: 39.7, address: 'ул. Тест', district: 'Центральный',
  status: 'NEW', photos: [], votesCount: 3, userVoted: false,
  createdAt: '2026-05-20T09:00:00Z', updatedAt: '2026-05-20T09:00:00Z',
  statusHistory: [], slaBreached: false,
}

function renderDialog(props: Partial<React.ComponentProps<typeof StatusChangeDialog>> = {}) {
  const qc = new QueryClient()
  return render(
    <QueryClientProvider client={qc}>
      <StatusChangeDialog
        complaint={complaint}
        toStatus="IN_PROGRESS"
        onClose={vi.fn()}
        onSubmit={vi.fn()}
        submitting={false}
        {...props}
      />
    </QueryClientProvider>,
  )
}

describe('StatusChangeDialog', () => {
  it('кнопка подтверждения disabled при пустом комментарии', () => {
    renderDialog()
    expect(screen.getByRole('button', { name: /подтвердить/i })).toBeDisabled()
  })

  it('после ввода комментария submit шлёт верный body', async () => {
    const onSubmit = vi.fn()
    renderDialog({ onSubmit })
    await userEvent.type(screen.getByLabelText(/комментарий/i), 'Приняли в работу')
    await userEvent.click(screen.getByRole('button', { name: /подтвердить/i }))
    expect(onSubmit).toHaveBeenCalledWith({
      toStatus: 'IN_PROGRESS',
      comment: 'Приняли в работу',
      duplicateOfId: undefined,
    })
  })
})
