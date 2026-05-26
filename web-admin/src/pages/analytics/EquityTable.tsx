import type { DistrictStat } from '@/api/types'

interface Props { items: DistrictStat[] }

function toneFor(slaPct: number | null | undefined): { row: string; sla: string } {
  if (slaPct === null || slaPct === undefined) {
    return { row: 'equity-row border-t border-slate-100', sla: 'text-slate-500' }
  }
  if (slaPct >= 80) {
    return { row: 'equity-row equity-row--good border-t border-emerald-100 bg-emerald-50/40', sla: 'text-emerald-700' }
  }
  if (slaPct >= 60) {
    return { row: 'equity-row equity-row--warn border-t border-amber-100 bg-amber-50/40', sla: 'text-amber-700' }
  }
  return { row: 'equity-row equity-row--bad border-t border-rose-100 bg-rose-50/40', sla: 'text-rose-700' }
}

export function EquityTable({ items }: Props) {
  if (items.length === 0) {
    return <div className="rounded-xl border border-slate-200 bg-white p-4 text-sm text-slate-500">
      Нет данных по районам
    </div>
  }
  return (
    <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white">
      <table className="equity-table w-full text-sm">
        <thead className="bg-slate-50 text-left text-xs font-medium text-slate-500">
          <tr>
            <th className="px-3 py-2">Район</th>
            <th className="px-3 py-2">Жалоб</th>
            <th className="px-3 py-2">Медиана решения</th>
            <th className="px-3 py-2">% within SLA</th>
          </tr>
        </thead>
        <tbody>
          {items.map(d => {
            const tone = toneFor(d.slaCompliancePct)
            return (
              <tr key={d.district} className={tone.row}>
                <td className="px-3 py-2 text-slate-900">{d.label}</td>
                <td className="px-3 py-2 text-slate-700">{d.count}</td>
                <td className="px-3 py-2 text-slate-700">
                  {d.medianResolutionHours == null ? '—' : `${d.medianResolutionHours.toFixed(1)}ч`}
                </td>
                <td className={`px-3 py-2 font-medium ${tone.sla}`}>
                  {d.slaCompliancePct == null ? '—' : `${d.slaCompliancePct.toFixed(0)}%`}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
