import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { Toaster } from 'sonner'
import { AnnouncementsPage } from './AnnouncementsPage'
import type { Announcement } from '@/api/types'

const BASE = 'http://localhost:8081'

function ann(id: number, over: Partial<Announcement> = {}): Announcement {
  return {
    id,
    title: `Объявление ${id}`,
    body: 'текст',
    iconStyle: 'INFO',
    category: null,
    districts: [],
    authorId: 1,
    publishedAt: '2026-05-20T09:00:00Z',
    expiresAt: null,
    ...over,
  }
}

const server = setupServer()
beforeAll(() => server.listen())
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <>
        <AnnouncementsPage />
        <Toaster />
      </>
    </QueryClientProvider>,
  )
}

describe('AnnouncementsPage', () => {
  it('загружает и показывает список объявлений', async () => {
    server.use(
      http.get(`${BASE}/announcements`, () =>
        HttpResponse.json({ items: [ann(1), ann(2)], total: 2 }),
      ),
    )
    renderPage()
    expect(await screen.findByText('Объявление 1')).toBeInTheDocument()
    expect(screen.getByText('Объявление 2')).toBeInTheDocument()
  })

  it('показывает empty state, когда объявлений нет', async () => {
    server.use(http.get(`${BASE}/announcements`, () => HttpResponse.json({ items: [], total: 0 })))
    renderPage()
    expect(await screen.findByText(/объявлений пока нет/i)).toBeInTheDocument()
  })

  it('показывает ошибку загрузки с кнопкой «Повторить»', async () => {
    server.use(http.get(`${BASE}/announcements`, () => new HttpResponse(null, { status: 500 })))
    renderPage()
    expect(await screen.findByText(/не удалось загрузить/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /повторить/i })).toBeInTheDocument()
  })

  it('успешная публикация показывает toast', async () => {
    server.use(
      http.get(`${BASE}/announcements`, () => HttpResponse.json({ items: [], total: 0 })),
      http.post(`${BASE}/announcements`, () => HttpResponse.json(ann(99), { status: 201 })),
    )
    renderPage()
    await userEvent.type(await screen.findByLabelText(/заголовок/i), 'Новое')
    await userEvent.type(screen.getByLabelText(/текст объявления/i), 'Текст объявления')
    await userEvent.click(screen.getByRole('button', { name: /опубликовать/i }))
    await waitFor(() =>
      expect(screen.getByText('Объявление опубликовано')).toBeInTheDocument(),
    )
  })
})
