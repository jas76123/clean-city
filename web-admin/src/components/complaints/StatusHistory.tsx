import type { StatusChange } from '@/api/types'
import { STATUS_META } from '@/lib/complaintMeta'

function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('ru-RU', {
    day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit',
  })
}

export function StatusHistory({ history }: { history: StatusChange[] }) {
  if (history.length === 0) {
    return <div className="text-sm text-slate-400">История пуста</div>
  }
  return (
    <ul className="space-y-2">
      {history.map((h, i) => (
        <li key={i} className="border-l-2 border-slate-200 pl-3 text-sm">
          <div className="font-medium text-slate-700">
            {h.fromStatus ? `${STATUS_META[h.fromStatus].label} → ` : ''}
            {STATUS_META[h.toStatus].label}
          </div>
          <div className="text-slate-600">{h.comment}</div>
          <div className="text-xs text-slate-400">
            {h.changedByName ?? 'Администратор'} · {formatDateTime(h.createdAt)}
          </div>
        </li>
      ))}
    </ul>
  )
}
