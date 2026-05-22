import { Sparkline } from '@/components/dashboard/Sparkline'

interface TrendCardProps {
  title: string
  value: string
  sub: string
  /** Серия для спарклайна. Если не задана — спарклайн не рисуется. */
  series?: number[]
}

export function TrendCard({ title, value, sub, series }: TrendCardProps) {
  return (
    <div className="rounded-xl border bg-white p-4">
      <div className="text-xs text-slate-500">{title}</div>
      <div className="mt-1 text-2xl font-semibold text-slate-900">{value}</div>
      <div className="text-xs text-slate-400">{sub}</div>
      {series && series.length > 0 && <Sparkline values={series} />}
    </div>
  )
}
