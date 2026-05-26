import type { TrendsResponse } from '@/api/types'

interface Props { trends: TrendsResponse }

function pathFromSeries(
  series: { bucketStart: string; value: number }[],
  maxV: number,
  width: number,
  height: number,
): string {
  if (series.length === 0) return ''
  if (series.length === 1) {
    const y = height - (series[0].value / Math.max(maxV, 1)) * height
    return `M 0 ${y} L ${width} ${y}`
  }
  const stepX = width / (series.length - 1)
  return series
    .map((p, i) => {
      const x = i * stepX
      const y = height - (p.value / Math.max(maxV, 1)) * height
      return `${i === 0 ? 'M' : 'L'} ${x.toFixed(2)} ${y.toFixed(2)}`
    })
    .join(' ')
}

export function TrendCard({ trends }: Props) {
  const createdSeries = trends.createdSeries ?? []
  const resolvedSeries = trends.resolvedSeries ?? []
  if (createdSeries.length === 0 && resolvedSeries.length === 0) {
    return (
      <div className="rounded-xl border border-slate-200 bg-white p-4">
        <div className="mb-1 text-sm font-medium text-slate-700">Динамика: создано vs закрыто</div>
        <div className="py-6 text-center text-sm text-slate-400">Нет данных за выбранный период</div>
      </div>
    )
  }
  const maxV = Math.max(
    ...createdSeries.map(p => p.value),
    ...resolvedSeries.map(p => p.value),
    1,
  )
  const W = 600
  const H = 200
  const createdPath = pathFromSeries(createdSeries, maxV, W, H)
  const resolvedPath = pathFromSeries(resolvedSeries, maxV, W, H)
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4">
      <div className="mb-2 text-sm font-medium text-slate-700">Динамика: создано vs закрыто</div>
      <svg viewBox={`0 0 ${W} ${H}`} className="w-full h-auto">
        <path
          data-testid="trend-line-created"
          d={createdPath}
          stroke="#f87171"
          fill="none"
          strokeWidth={2}
        />
        <path
          data-testid="trend-line-resolved"
          d={resolvedPath}
          stroke="#34d399"
          fill="none"
          strokeWidth={2}
        />
      </svg>
      <div className="mt-2 flex gap-4 text-xs text-slate-600">
        <span className="flex items-center gap-1">
          <span className="inline-block h-2 w-3 rounded-sm bg-rose-400" /> Создано
        </span>
        <span className="flex items-center gap-1">
          <span className="inline-block h-2 w-3 rounded-sm bg-emerald-400" /> Закрыто
        </span>
      </div>
    </div>
  )
}
