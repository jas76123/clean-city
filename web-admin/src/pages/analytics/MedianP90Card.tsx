interface Props {
  label: string
  median: number | null
  p90: number | null
}

export function MedianP90Card({ label, median, p90 }: Props) {
  return (
    <div
      data-testid="median-p90-card"
      className="kpi-card kpi-card--neutral rounded-xl border border-slate-200 bg-white p-4"
    >
      <div className="text-xs text-slate-500">{label}</div>
      <div className="mt-1 text-2xl font-semibold text-slate-900">
        {median === null ? '—' : `${median.toFixed(1)}ч`}
        <span className="ml-2 text-sm font-normal text-slate-500">
          / p90 {p90 === null ? '—' : `${p90.toFixed(1)}ч`}
        </span>
      </div>
      <div className="mt-1 text-xs text-slate-400">медиана · 90-й перцентиль</div>
    </div>
  )
}
