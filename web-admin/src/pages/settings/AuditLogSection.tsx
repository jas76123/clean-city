import { useQuery } from '@tanstack/react-query'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { recentAuditEvents } from '@/api/admin'
import { AuditTable } from './AuditTable'

export function AuditLogSection() {
  const { data, isLoading } = useQuery({
    queryKey: ['audit-log'],
    queryFn: () => recentAuditEvents(50),
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle>Журнал событий</CardTitle>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <div className="py-8 text-center text-sm text-muted-foreground">Загрузка…</div>
        ) : (
          <AuditTable entries={data ?? []} />
        )}
      </CardContent>
    </Card>
  )
}
