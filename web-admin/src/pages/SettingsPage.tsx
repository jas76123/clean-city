import { useAuth } from '@/auth/AuthContext'
import { TeamSection } from './settings/TeamSection'
// Журнал событий временно скрыт — секция готова, но решено не показывать
// до отдельной договорённости. Раскомментировать импорт + render ниже.
// import { AuditLogSection } from './settings/AuditLogSection'
// Экспорт отчётов в PDF временно скрыт — компонент готов и протестирован.
// Раскомментировать импорт + render ниже когда нужно вернуть.
// import { ExportSection } from './settings/ExportSection'

export function SettingsPage() {
  const { user } = useAuth()
  const role = user?.role ?? 'OPERATOR'

  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-2xl font-semibold">Настройки</h1>
      <TeamSection currentRole={role} />
      {/* <ExportSection /> */}
      {/* <AuditLogSection /> */}
    </div>
  )
}
