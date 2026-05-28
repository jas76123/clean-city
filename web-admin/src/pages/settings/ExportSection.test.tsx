import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ExportSection } from './ExportSection'
import * as analyticsApi from '@/api/analytics'

vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}))

describe('ExportSection', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    global.URL.createObjectURL = vi.fn(() => 'blob:test-url')
    global.URL.revokeObjectURL = vi.fn()
  })

  it('renders only the active monthly report card', () => {
    render(<ExportSection />)
    expect(screen.getByText(/Сводный отчёт за месяц/i)).toBeInTheDocument()
    expect(screen.queryByText(/Реестр жалоб/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/Отчёт по SLA/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/Голосование жителей/i)).not.toBeInTheDocument()
    expect(screen.queryByText('Скоро')).not.toBeInTheDocument()
  })

  it('calls downloadMonthlyReport on active button click', async () => {
    const spy = vi
      .spyOn(analyticsApi, 'downloadMonthlyReport')
      .mockResolvedValue({ blob: new Blob(['x']), filename: 'cleancity-monthly-report-2026-04.pdf' })
    render(<ExportSection />)
    fireEvent.click(screen.getByRole('button', { name: /Скачать PDF/i }))
    await waitFor(() => expect(spy).toHaveBeenCalledOnce())
  })

  it('disables button while loading and re-enables after', async () => {
    let resolveFn: (v: { blob: Blob; filename: string }) => void = () => {}
    vi.spyOn(analyticsApi, 'downloadMonthlyReport').mockReturnValue(
      new Promise((res) => {
        resolveFn = res
      }),
    )
    render(<ExportSection />)
    const btn = screen.getByRole('button', { name: /Скачать PDF/i })
    fireEvent.click(btn)
    await waitFor(() => expect(btn).toBeDisabled())
    resolveFn({ blob: new Blob(['x']), filename: 'x.pdf' })
    await waitFor(() => expect(btn).not.toBeDisabled())
  })

  it('shows error toast on failure', async () => {
    vi.spyOn(analyticsApi, 'downloadMonthlyReport').mockRejectedValue(new Error('boom'))
    const { toast } = await import('sonner')
    render(<ExportSection />)
    fireEvent.click(screen.getByRole('button', { name: /Скачать PDF/i }))
    await waitFor(() => expect(toast.error).toHaveBeenCalled())
  })
})
