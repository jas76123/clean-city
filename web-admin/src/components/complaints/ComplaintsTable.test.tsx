import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ComplaintsTable } from './ComplaintsTable'
import type { Complaint } from '@/api/types'

function complaint(id: number, over: Partial<Complaint> = {}): Complaint {
  return {
    id, authorId: 1, category: 'GARBAGE', title: `Жалоба ${id}`, description: 'd',
    latitude: 43.6, longitude: 39.7, address: 'ул. Тест', district: 'Центральный',
    status: 'NEW', photos: [], votesCount: 12, userVoted: false,
    createdAt: '2026-05-20T09:00:00Z', updatedAt: '2026-05-20T09:00:00Z',
    statusHistory: [], slaBreached: false, ...over,
  }
}

describe('ComplaintsTable', () => {
  it('рендерит строки и клик вызывает onSelect', async () => {
    const onSelect = vi.fn()
    render(
      <ComplaintsTable items={[complaint(1), complaint(2)]} selectedId={null} onSelect={onSelect} />,
    )
    await userEvent.click(screen.getByText('Жалоба 1'))
    expect(onSelect).toHaveBeenCalledWith(1)
  })

  it('показывает empty state на пустом списке', () => {
    render(<ComplaintsTable items={[]} selectedId={null} onSelect={vi.fn()} />)
    expect(screen.getByText(/ничего не нашлось/i)).toBeInTheDocument()
  })
})
