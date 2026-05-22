interface HBarProps {
  label: string
  /** Доля заполнения 0..1. */
  fraction: number
  /** Число справа. */
  value: number | string
  /** Tailwind-класс цвета полосы, напр. 'bg-red-500'. */
  colorClass?: string
}

export function HBar({ label, fraction, value, colorClass = 'bg-emerald-500' }: HBarProps) {
  const pct = Math.max(2, Math.min(100, fraction * 100))
  return (
    <div className="flex items-center gap-3 py-1">
      <span className="w-28 shrink-0 truncate text-xs text-slate-600">{label}</span>
      <div className="h-2 flex-1 rounded bg-slate-100">
        <div className={`h-full rounded ${colorClass}`} style={{ width: `${pct}%` }} />
      </div>
      <span className="w-10 shrink-0 text-right text-xs font-semibold text-slate-700">{value}</span>
    </div>
  )
}
