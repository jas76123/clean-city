import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AnnouncementForm } from './AnnouncementForm'
import { toEndOfDayIso } from '@/lib/dateUtils'

describe('AnnouncementForm', () => {
  it('кнопка «Опубликовать» заблокирована при пустых полях', () => {
    render(<AnnouncementForm submitting={false} onSubmit={vi.fn()} />)
    expect(screen.getByRole('button', { name: /опубликовать/i })).toBeDisabled()
  })

  it('кнопка активируется, когда заполнены заголовок и текст', async () => {
    render(<AnnouncementForm submitting={false} onSubmit={vi.fn()} />)
    await userEvent.type(screen.getByLabelText(/заголовок/i), 'Вывоз мусора')
    await userEvent.type(screen.getByLabelText(/текст объявления/i), 'Подробности')
    expect(screen.getByRole('button', { name: /опубликовать/i })).toBeEnabled()
  })

  it('submit всегда передаёт пустой districts — рассылка по всем жителям', async () => {
    const onSubmit = vi.fn()
    render(<AnnouncementForm submitting={false} onSubmit={onSubmit} />)
    await userEvent.type(screen.getByLabelText(/заголовок/i), 'Заголовок')
    await userEvent.type(screen.getByLabelText(/текст объявления/i), 'Текст')
    await userEvent.click(screen.getByRole('button', { name: /опубликовать/i }))
    expect(onSubmit).toHaveBeenCalledWith({
      title: 'Заголовок',
      body: 'Текст',
      iconStyle: 'INFO',
      districts: [],
    })
  })

  it('кнопка блокируется и показывается ошибка, если срок раньше сегодня', async () => {
    const onSubmit = vi.fn()
    render(<AnnouncementForm submitting={false} onSubmit={onSubmit} />)
    await userEvent.type(screen.getByLabelText(/заголовок/i), 'Заголовок')
    await userEvent.type(screen.getByLabelText(/текст объявления/i), 'Текст')
    await userEvent.type(screen.getByLabelText(/срок действия/i), '2020-01-01')
    expect(screen.getByRole('button', { name: /опубликовать/i })).toBeDisabled()
    expect(screen.getByText(/не раньше сегодняшнего дня/i)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /опубликовать/i }))
    expect(onSubmit).not.toHaveBeenCalled()
  })
})

describe('toEndOfDayIso', () => {
  it('конвертирует дату в конец дня с offset Сочи', () => {
    expect(toEndOfDayIso('2026-05-25')).toBe('2026-05-25T23:59:59+03:00')
  })
})
