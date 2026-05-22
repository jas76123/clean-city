import type { VotesBucket } from '@/api/types'
import { HBar } from '@/components/dashboard/HBar'

interface VotesImpactCardProps {
  buckets: VotesBucket[]
}

const BUCKET_LABEL: Record<string, string> = {
  '50+': 'Жалобы с 50+ голосов',
  '10-49': 'Жалобы с 10–49 голосов',
  '1-9': 'Жалобы с 1–9 голосов',
  '0': 'Жалобы без голосов',
}

export function VotesImpactCard({ buckets }: VotesImpactCardProps) {
  const withData = buckets.filter((b) => b.avgResolutionHours != null)
  const max = Math.max(1, ...withData.map((b) => b.avgResolutionHours as number))
  return (
    <div className="rounded-xl border bg-white p-4">
      <div className="mb-1 text-sm font-medium text-slate-700">Голоса как сигнал приоритизации</div>
      <div className="mb-3 text-xs text-slate-400">Среднее время решения по числу голосов</div>
      {withData.length === 0 ? (
        <div className="py-6 text-center text-sm text-slate-400">Нет данных</div>
      ) : (
        <>
          {withData.map((b) => (
            <HBar
              key={b.bucket}
              label={BUCKET_LABEL[b.bucket] ?? b.bucket}
              fraction={(b.avgResolutionHours as number) / max}
              value={`${Math.round(b.avgResolutionHours as number)} ч`}
              colorClass="bg-sky-500"
            />
          ))}
          <div className="mt-3 rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-xs text-slate-600">
            💡 <strong className="text-emerald-700">Голосование жителей работает.</strong>{' '}
            Социальный сигнал помогает выявлять важные проблемы быстрее формального SLA.
          </div>
        </>
      )}
    </div>
  )
}
