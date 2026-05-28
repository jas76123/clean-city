import { useAuth } from '@/auth/AuthContext'
import { ExportSection } from './settings/ExportSection'
import { TeamSection } from './settings/TeamSection'
// Журнал событий временно скрыт — секция готова, но решено не показывать
// до отдельной договорённости. Раскомментировать импорт + render ниже.
// import { AuditLogSection } from './settings/AuditLogSection'

export function SettingsPage() {
  const { user } = useAuth()
  const role = user?.role ?? 'OPERATOR'

  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-2xl font-semibold">Настройки</h1>
      <TeamSection currentRole={role} />
      <ExportSection />
      {/* <AuditLogSection /> */}
    </div>
  )
}
