import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { ComplaintsPage } from './ComplaintsPage'
import { Toaster } from 'sonner'
import type { Complaint } from '@/api/types'

const BASE = 'http://localhost:8081'

function complaint(id: number, over: Partial<Complaint> = {}): Complaint {
  return {
    id, authorId: 1, category: 'GARBAGE', title: `Жалоба ${id}`, description: 'описание',
    latitude: 43.6, longitude: 39.7, address: 'ул. Тест', district: 'Центральный',
    status: 'NEW', photos: [], votesCount: 5, userVoted: false,
    createdAt: '2026-05-20T09:00:00Z', updatedAt: '2026-05-20T09:00:00Z',
    statusHistory: [], slaBreached: false, ...over,
  }
}

const overview = {
  total: 2, new: 2, inProgress: 0, resolved: 0, rejected: 0, duplicate: 0,
  today: 0, week: 0, slaBreachCount: 0,
}

const server = setupServer(
  http.get(`${BASE}/complaints`, () =>
    HttpResponse.json({ items: [complaint(1), complaint(2)], page: 0, size: 20, total: 2 }),
  ),
  http.get(`${BASE}/complaints/:id`, ({ params }) =>
    HttpResponse.json(complaint(Number(params.id))),
  ),
  http.get(`${BASE}/analytics/overview`, () => HttpResponse.json(overview)),
  http.patch(`${BASE}/complaints/:id/status`, ({ params }) =>
    HttpResponse.json(complaint(Number(params.id), { status: 'IN_PROGRESS' })),
  ),
)

beforeAll(() => server.listen())
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <><ComplaintsPage /><Toaster /></>
    </QueryClientProvider>,
  )
}

describe('ComplaintsPage', () => {
  it('загружает и показывает список жалоб', async () => {
    renderPage()
    expect(await screen.findByText('Жалоба 1')).toBeInTheDocument()
    expect(screen.getByText('Жалоба 2')).toBeInTheDocument()
  })

  it('клик по строке открывает деталь-панель с действиями', async () => {
    renderPage()
    await userEvent.click(await screen.findByText('Жалоба 1'))
    expect(await screen.findByRole('button', { name: /Принять в работу/ })).toBeInTheDocument()
  })

  it('смена статуса с комментарием показывает toast успеха', async () => {
    renderPage()
    await userEvent.click(await screen.findByText('Жалоба 1'))
    await userEvent.click(await screen.findByRole('button', { name: /Принять в работу/ }))
    await userEvent.type(screen.getByLabelText(/комментарий/i), 'Беру в работу')
    await userEvent.click(screen.getByRole('button', { name: /подтвердить/i }))
    await waitFor(() => expect(screen.getByText('Статус изменён')).toBeInTheDocument())
  })
})
