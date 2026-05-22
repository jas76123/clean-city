import type { Complaint } from '@/api/types'
import { CATEGORY_META } from '@/lib/complaintMeta'
import { StatusBadge } from './StatusBadge'

interface Props {
  items: Complaint[]
  selectedId: number | null
  onSelect: (id: number) => void
  onResetFilters?: () => void
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit' })
}

export function ComplaintsTable({ items, selectedId, onSelect, onResetFilters }: Props) {
  if (items.length === 0) {
    return (
      <div className="py-12 text-center text-sm text-slate-400">
        Под выбранные фильтры ничего не нашлось
        {onResetFilters && (
          <div>
            <button
              type="button"
              onClick={onResetFilters}
              className="mt-2 text-sm text-blue-600 underline"
            >
              Сбросить фильтры
            </button>
          </div>
        )}
      </div>
    )
  }
  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="border-b text-left text-xs uppercase text-slate-400">
          <th className="px-3 py-2">Проблема</th>
          <th className="px-3 py-2">Район</th>
          <th className="px-3 py-2">Категория</th>
          <th className="px-3 py-2">Дата</th>
          <th className="px-3 py-2">Голоса</th>
          <th className="px-3 py-2">Статус</th>
        </tr>
      </thead>
      <tbody>
        {items.map((c) => (
          <tr
            key={c.id}
            onClick={() => onSelect(c.id)}
            className={`cursor-pointer border-b hover:bg-slate-50 ${
              c.id === selectedId ? 'bg-slate-100' : ''
            }`}
          >
            <td className="px-3 py-2 font-medium text-slate-800">{c.title}</td>
            <td className="px-3 py-2 text-slate-600">{c.district ?? '—'}</td>
            <td className="px-3 py-2 text-slate-600">
              {CATEGORY_META[c.category].icon} {CATEGORY_META[c.category].label}
            </td>
            <td className="px-3 py-2 text-slate-600">{formatDate(c.createdAt)}</td>
            <td className="px-3 py-2 text-slate-600">{c.votesCount}</td>
            <td className="px-3 py-2">
              <StatusBadge status={c.status} slaBreached={c.slaBreached} />
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
