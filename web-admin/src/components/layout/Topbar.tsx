import { useLocation } from 'react-router-dom'
import { useAuth } from '@/auth/AuthContext'
import { NAV_ITEMS } from './navItems'
import { Button } from '@/components/ui/button'

export function Topbar() {
  const { user, logout } = useAuth()
  const location = useLocation()
  const current = NAV_ITEMS.find((i) => location.pathname.startsWith(i.path))

  return (
    <header className="flex h-14 items-center justify-between border-b border-slate-200 bg-white px-6">
      <h1 className="text-base font-semibold text-slate-800">
        {current?.label ?? 'Чистый Город'}
      </h1>
      <div className="flex items-center gap-4">
        <div className="text-right">
          <div className="text-sm font-medium text-slate-800">
            {user?.fullName ?? user?.email}
          </div>
          <div className="text-xs text-slate-400">{user?.role}</div>
        </div>
        <Button variant="outline" size="sm" onClick={() => void logout()}>
          Выход
        </Button>
      </div>
    </header>
  )
}
