import type { VotesBucket } from '@/api/types'

interface Props { buckets: VotesBucket[] }

const BUCKET_LABEL: Record<string, string> = {
  '0': 'без голосов',
  '1-9': '1–9 голосов',
  '10-49': '10–49 голосов',
  '50+': '50+ голосов',
}

export function VotesImpactCard({ buckets }: Props) {
  const withData = buckets.filter(b => b.avgResolutionHours != null)
  const maxHours = Math.max(1, ...withData.map(b => b.avgResolutionHours as number))
  if (withData.length === 0) {
    return (
      <div className="rounded-xl border border-slate-200 bg-white p-4">
        <div className="mb-1 text-sm font-medium text-slate-700">Влияние голосов на скорость решения</div>
        <div className="py-6 text-center text-sm text-slate-400">Нет данных</div>
      </div>
    )
  }
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4">
      <div className="mb-1 text-sm font-medium text-slate-700">Влияние голосов на скорость решения</div>
      <div className="mb-3 text-xs text-slate-400">Среднее время решения по числу голосов</div>
      <div className="space-y-2">
        {withData.map(b => {
          const h = b.avgResolutionHours as number
          const widthPct = (h / maxHours) * 100
          return (
            <div key={b.bucket} className="flex items-center gap-3">
              <div className="w-32 shrink-0 text-xs text-slate-600">
                {BUCKET_LABEL[b.bucket] ?? b.bucket} · {b.count} жалоб
              </div>
              <div className="relative flex-1 h-5 rounded bg-slate-100">
                <div
                  data-testid="votes-bar"
                  className="h-5 rounded bg-sky-500"
                  style={{ width: `${widthPct}%` }}
                />
              </div>
              <div className="w-16 shrink-0 text-right text-xs text-slate-700 tabular-nums">
                {h.toFixed(1)}ч
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
