import type { DistrictStat } from '@/api/types'
import { HBar } from '@/components/dashboard/HBar'

interface TopDistrictsProps {
  stats: DistrictStat[]
}

const COLORS = ['bg-red-500', 'bg-amber-500', 'bg-sky-500', 'bg-emerald-500']

export function TopDistricts({ stats }: TopDistrictsProps) {
  const top = stats.slice(0, 4)
  const max = Math.max(1, ...top.map((s) => s.count))
  return (
    <div className="rounded-xl border bg-white p-4">
      <div className="mb-3 text-sm font-medium text-slate-700">Районы — лидеры по жалобам</div>
      {top.length === 0 ? (
        <div className="py-6 text-center text-sm text-slate-400">Нет данных</div>
      ) : (
        top.map((s, i) => (
          <HBar
            key={s.district}
            label={s.label}
            fraction={s.count / max}
            value={s.count}
            colorClass={COLORS[i] ?? 'bg-slate-400'}
          />
        ))
      )}
    </div>
  )
}
