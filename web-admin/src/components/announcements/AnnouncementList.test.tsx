import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { AnnouncementList } from './AnnouncementList'
import type { Announcement } from '@/api/types'

function ann(over: Partial<Announcement> = {}): Announcement {
  return {
    id: 1,
    title: 'Заголовок',
    body: 'Текст',
    iconStyle: 'INFO',
    category: null,
    districts: [],
    authorId: 1,
    publishedAt: '2026-05-20T09:00:00Z',
    expiresAt: null,
    ...over,
  }
}

describe('AnnouncementList', () => {
  it('показывает empty state на пустом списке', () => {
    render(<AnnouncementList items={[]} unpublishingId={null} onUnpublish={vi.fn()} />)
    expect(screen.getByText(/объявлений пока нет/i)).toBeInTheDocument()
  })

  it('рендерит элементы списка', () => {
    render(
      <AnnouncementList
        items={[ann({ id: 1, title: 'Первое' }), ann({ id: 2, title: 'Второе' })]}
        unpublishingId={null}
        onUnpublish={vi.fn()}
      />,
    )
    expect(screen.getByText('Первое')).toBeInTheDocument()
    expect(screen.getByText('Второе')).toBeInTheDocument()
  })
})
