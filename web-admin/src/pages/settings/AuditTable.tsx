import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import type { AuditEntryDto } from '@/api/types'
import { labelForAction } from './actionLabels'

type Props = { entries: AuditEntryDto[] }

function formatTime(iso: string): string {
  return new Date(iso).toLocaleString('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

function describeTarget(targetType: string | null, targetId: string | null): string {
  if (!targetType || !targetId) return '—'
  if (targetType === 'user') return `Пользователь #${targetId}`
  if (targetType === 'complaint') return `Жалоба #${targetId}`
  if (targetType === 'refresh_token') return `Refresh-токен #${targetId}`
  return `${targetType} #${targetId}`
}

export function AuditTable({ entries }: Props) {
  if (entries.length === 0) {
    return (
      <div className="py-8 text-center text-sm text-muted-foreground">
        События будут появляться по мере действий в системе.
      </div>
    )
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Время</TableHead>
          <TableHead>Кто</TableHead>
          <TableHead>Действие</TableHead>
          <TableHead>Объект</TableHead>
          <TableHead>IP</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {entries.map((e) => (
          <TableRow key={e.id}>
            <TableCell className="whitespace-nowrap">{formatTime(e.timestamp)}</TableCell>
            <TableCell>{e.actorEmail ?? 'Система'}</TableCell>
            <TableCell>{labelForAction(e.action)}</TableCell>
            <TableCell>{describeTarget(e.targetType, e.targetId)}</TableCell>
            <TableCell className="text-muted-foreground">{e.ip ?? '—'}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}
