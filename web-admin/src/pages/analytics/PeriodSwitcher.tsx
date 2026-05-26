import type { AnalyticsPeriod } from '@/api/types'

interface PeriodSwitcherProps {
  value: AnalyticsPeriod
  onChange: (p: AnalyticsPeriod) => void
}

const OPTIONS: { value: AnalyticsPeriod; label: string }[] = [
  { value: 'WEEK', label: 'Неделя' },
  { value: 'MONTH', label: 'Месяц' },
  { value: 'QUARTER', label: 'Квартал' },
  { value: 'YEAR', label: 'Год' },
  { value: 'ALL', label: 'Всё время' },
]

export function PeriodSwitcher({ value, onChange }: PeriodSwitcherProps) {
  return (
    <div className="inline-flex rounded-lg border bg-white p-0.5">
      {OPTIONS.map((o) => (
        <button
          key={o.value}
          onClick={() => onChange(o.value)}
          className={`rounded-md px-3 py-1 text-xs font-medium ${
            value === o.value ? 'bg-emerald-600 text-white' : 'text-slate-600'
          }`}
        >
          {o.label}
        </button>
      ))}
    </div>
  )
}
