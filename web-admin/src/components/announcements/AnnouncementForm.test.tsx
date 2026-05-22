import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AnnouncementForm } from './AnnouncementForm'

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

  it('при выключенном «Все районы» нужен хотя бы один отмеченный район', async () => {
    render(<AnnouncementForm submitting={false} onSubmit={vi.fn()} />)
    await userEvent.type(screen.getByLabelText(/заголовок/i), 'Заголовок')
    await userEvent.type(screen.getByLabelText(/текст объявления/i), 'Текст')
    await userEvent.click(screen.getByLabelText(/все районы/i)) // снять галочку «Все»
    expect(screen.getByRole('button', { name: /опубликовать/i })).toBeDisabled()
    await userEvent.click(screen.getByLabelText('Адлерский'))
    expect(screen.getByRole('button', { name: /опубликовать/i })).toBeEnabled()
  })

  it('submit передаёт собранный запрос (по умолчанию все районы)', async () => {
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
})
