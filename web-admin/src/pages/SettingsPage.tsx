import { useAuth } from '@/auth/AuthContext'
import { TeamSection } from './settings/TeamSection'
import { AuditLogSection } from './settings/AuditLogSection'
import { ExportSection } from './settings/ExportSection'

export function SettingsPage() {
  const { user } = useAuth()
  const role = user?.role ?? 'OPERATOR'

  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-2xl font-semibold">Настройки</h1>
      <TeamSection currentRole={role} />
      <ExportSection />
      <AuditLogSection />
    </div>
  )
}
