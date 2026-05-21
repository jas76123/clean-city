import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { ProtectedRoute } from './ProtectedRoute'
import { AuthContextTestProvider } from './testUtils'

function renderAt(status: 'loading' | 'authenticated' | 'unauthenticated') {
  return render(
    <AuthContextTestProvider status={status}>
      <MemoryRouter initialEntries={['/secret']}>
        <Routes>
          <Route element={<ProtectedRoute />}>
            <Route path="/secret" element={<div>SECRET</div>} />
          </Route>
          <Route path="/login" element={<div>LOGIN PAGE</div>} />
        </Routes>
      </MemoryRouter>
    </AuthContextTestProvider>,
  )
}

describe('ProtectedRoute', () => {
  it('unauthenticated → редирект на /login', () => {
    renderAt('unauthenticated')
    expect(screen.getByText('LOGIN PAGE')).toBeInTheDocument()
    expect(screen.queryByText('SECRET')).not.toBeInTheDocument()
  })

  it('loading → показывает индикатор загрузки', () => {
    renderAt('loading')
    expect(screen.getByText(/загрузка/i)).toBeInTheDocument()
  })

  it('authenticated → рендерит защищённый контент', () => {
    renderAt('authenticated')
    expect(screen.getByText('SECRET')).toBeInTheDocument()
  })
})
