import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AnnouncementItem } from './AnnouncementItem'
import type { Announcement } from '@/api/types'

function ann(over: Partial<Announcement> = {}): Announcement {
  return {
    id: 1,
    title: 'Заголовок',
    body: 'Текст объявления',
    iconStyle: 'INFO',
    category: null,
    districts: [],
    authorId: 1,
    publishedAt: '2026-05-20T09:00:00Z',
    expiresAt: null,
    ...over,
  }
}

describe('AnnouncementItem', () => {
  it('рендерит заголовок, текст и районы', () => {
    render(<AnnouncementItem announcement={ann()} unpublishing={false} onUnpublish={vi.fn()} />)
    expect(screen.getByText('Заголовок')).toBeInTheDocument()
    expect(screen.getByText('Текст объявления')).toBeInTheDocument()
    expect(screen.getByText(/Все районы/)).toBeInTheDocument()
  })

  it('снятие требует инлайн-подтверждения', async () => {
    const onUnpublish = vi.fn()
    render(
      <AnnouncementItem announcement={ann({ id: 5 })} unpublishing={false} onUnpublish={onUnpublish} />,
    )
    await userEvent.click(screen.getByRole('button', { name: /снять с публикации/i }))
    expect(onUnpublish).not.toHaveBeenCalled()
    expect(screen.getByText(/точно снять/i)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /^да$/i }))
    expect(onUnpublish).toHaveBeenCalledWith(5)
  })

  it('«Отмена» возвращает исходную кнопку', async () => {
    render(<AnnouncementItem announcement={ann()} unpublishing={false} onUnpublish={vi.fn()} />)
    await userEvent.click(screen.getByRole('button', { name: /снять с публикации/i }))
    await userEvent.click(screen.getByRole('button', { name: /отмена/i }))
    expect(screen.getByRole('button', { name: /снять с публикации/i })).toBeInTheDocument()
  })
})
