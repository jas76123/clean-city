import type { AnalyticsOverview, ComplaintFilter, ComplaintStatus } from '@/api/types'
import { CATEGORY_META, CATEGORY_ORDER, DISTRICTS, SORT_OPTIONS } from '@/lib/complaintMeta'

interface Props {
  filter: ComplaintFilter
  overview: AnalyticsOverview | undefined
  onChange: (next: ComplaintFilter) => void
}

interface Chip {
  key: string
  label: string
  count: number | undefined
  active: boolean
  apply: (f: ComplaintFilter) => ComplaintFilter
}

const CHIP_BASE =
  'rounded-full border px-3 py-1 text-sm transition-colors disabled:opacity-50'

export function ComplaintFilters({ filter, overview, onChange }: Props) {
  const statusChip = (
    status: ComplaintStatus,
    label: string,
    count: number | undefined,
  ): Chip => ({
    key: status,
    label,
    count,
    active: filter.status === status && !filter.slaBreached,
    apply: (f) => ({ ...f, status, slaBreached: false, page: 0 }),
  })

  const chips: Chip[] = [
    {
      key: 'ALL',
      label: 'Все',
      count: overview?.total,
      active: filter.status === null && !filter.slaBreached,
      apply: (f) => ({ ...f, status: null, slaBreached: false, page: 0 }),
    },
    statusChip('NEW', 'В обработке', overview?.new),
    statusChip('IN_PROGRESS', 'В работе', overview?.inProgress),
    statusChip('RESOLVED', 'Решено', overview?.resolved),
    statusChip('REJECTED', 'Отклонена', overview?.rejected),
    statusChip('DUPLICATE', 'Дубликат', overview?.duplicate),
    {
      key: 'SLA',
      label: '⚠ SLA',
      count: overview?.slaBreachCount,
      active: filter.slaBreached,
      apply: (f) => ({ ...f, status: null, slaBreached: true, page: 0 }),
    },
  ]

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-2">
        {chips.map((c) => (
          <button
            key={c.key}
            type="button"
            onClick={() => onChange(c.apply(filter))}
            className={`${CHIP_BASE} ${
              c.active
                ? 'border-slate-800 bg-slate-800 text-white'
                : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50'
            }`}
          >
            {c.label}
            {c.count !== undefined ? ` ${c.count}` : ''}
          </button>
        ))}
      </div>
      <div className="flex flex-wrap gap-3">
        <select
          aria-label="Категория"
          className="rounded border border-slate-300 px-2 py-1 text-sm"
          value={filter.category ?? ''}
          onChange={(e) =>
            onChange({
              ...filter,
              category: (e.target.value || null) as ComplaintFilter['category'],
              page: 0,
            })
          }
        >
          <option value="">Все категории</option>
          {CATEGORY_ORDER.map((c) => (
            <option key={c} value={c}>
              {CATEGORY_META[c].icon} {CATEGORY_META[c].label}
            </option>
          ))}
        </select>
        <select
          aria-label="Район"
          className="rounded border border-slate-300 px-2 py-1 text-sm"
          value={filter.district ?? ''}
          onChange={(e) => onChange({ ...filter, district: e.target.value || null, page: 0 })}
        >
          <option value="">Все районы</option>
          {DISTRICTS.map((d) => (
            <option key={d} value={d}>
              {d}
            </option>
          ))}
        </select>
        <select
          aria-label="Сортировка"
          className="rounded border border-slate-300 px-2 py-1 text-sm"
          value={filter.sort}
          onChange={(e) =>
            onChange({ ...filter, sort: e.target.value as ComplaintFilter['sort'], page: 0 })
          }
        >
          {SORT_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      </div>
    </div>
  )
}
