import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from './AuthContext'
import { AppLayout } from '@/components/layout/AppLayout'

export function ProtectedRoute() {
  const { status } = useAuth()

  if (status === 'loading') {
    return (
      <div className="flex h-screen items-center justify-center text-slate-500">
        Загрузка…
      </div>
    )
  }
  if (status === 'unauthenticated') {
    return <Navigate to="/login" replace />
  }
  return (
    <AppLayout>
      <Outlet />
    </AppLayout>
  )
}
