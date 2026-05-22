import { useDuplicatesQuery } from '@/hooks/complaintQueries'
import { CATEGORY_META } from '@/lib/complaintMeta'
import type { ProblemCategory } from '@/api/types'

interface Props {
  lat: number
  lon: number
  category: ProblemCategory
  excludeId: number
  selectedId: number | null
  onSelect: (id: number) => void
}

export function DuplicatePicker({ lat, lon, category, excludeId, selectedId, onSelect }: Props) {
  const { data, isLoading, isError } = useDuplicatesQuery(true, lat, lon, category)

  if (isLoading) return <div className="text-sm text-slate-400">Поиск кандидатов…</div>
  if (isError) return <div className="text-sm text-red-600">Не удалось загрузить кандидатов</div>

  const items = (data?.items ?? []).filter((c) => c.id !== excludeId)
  if (items.length === 0) {
    return (
      <div className="text-sm text-slate-500">
        Поблизости активных жалоб категории «{CATEGORY_META[category].label}» нет
      </div>
    )
  }
  return (
    <ul className="max-h-48 space-y-1 overflow-y-auto">
      {items.map((c) => (
        <li key={c.id}>
          <button
            type="button"
            onClick={() => onSelect(c.id)}
            className={`w-full rounded border px-2 py-1 text-left text-sm ${
              c.id === selectedId
                ? 'border-slate-800 bg-slate-100'
                : 'border-slate-200 hover:bg-slate-50'
            }`}
          >
            <span className="font-medium">#{c.id}</span> {c.title}
            <span className="text-slate-400"> · {Math.round(c.distanceMeters)} м</span>
          </button>
        </li>
      ))}
    </ul>
  )
}
