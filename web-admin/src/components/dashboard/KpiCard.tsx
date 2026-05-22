interface KpiCardProps {
  label: string
  value: string
  /** Текущее и предыдущее числовое значение — для расчёта дельты. */
  current?: number | null
  previous?: number | null
  /** true → снижение показателя считается хорошим (зелёным). Напр. время решения. */
  lowerIsBetter?: boolean
}

export function KpiCard({ label, value, current, previous, lowerIsBetter }: KpiCardProps) {
  const delta =
    current != null && previous != null && previous !== 0
      ? Math.round(((current - previous) / previous) * 100)
      : null
  const good = delta == null ? false : lowerIsBetter ? delta < 0 : delta > 0
  const deltaClass = good ? 'bg-emerald-100 text-emerald-700' : 'bg-red-100 text-red-700'

  return (
    <div className="rounded-xl border bg-white p-4">
      <div className="flex items-start justify-between">
        <span className="text-2xl font-semibold text-slate-900">{value}</span>
        {delta != null && (
          <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${deltaClass}`}>
            {delta > 0 ? '+' : ''}
            {delta}%
          </span>
        )}
      </div>
      <div className="mt-1 text-xs text-slate-500">{label}</div>
    </div>
  )
}
