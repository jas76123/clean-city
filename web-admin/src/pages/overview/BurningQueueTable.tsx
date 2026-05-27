import { Link } from 'react-router-dom'
import type { BurningComplaintItem, ProblemCategory } from '@/api/types'
import { CATEGORY_META, DISTRICT_CODE_LABEL } from '@/lib/complaintMeta'

interface Props {
  items: BurningComplaintItem[]
}

function categoryLabel(code: string): string {
  return CATEGORY_META[code as ProblemCategory]?.label ?? code
}

function districtLabel(code: string | null): string {
  if (!code) return '—'
  return DISTRICT_CODE_LABEL[code] ?? code
}

function formatDeadline(seconds: number): string {
  const abs = Math.abs(seconds)
  const prefix = seconds < 0 ? '-' : '+'
  if (abs >= 86400) {
    const days = Math.floor(abs / 86400)
    const hours = Math.floor((abs % 86400) / 3600)
    return `${prefix}${days}д ${hours}ч`
  }
  const hours = Math.floor(abs / 3600)
  const mins = Math.floor((abs % 3600) / 60)
  return `${prefix}${hours}ч ${mins}м`
}

export function BurningQueueTable({ items }: Props) {
  if (items.length === 0) {
    return <div className="rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-700">
      Нет срочных жалоб — всё в порядке
    </div>
  }
  return (
    <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white">
      <table className="burning-table w-full text-sm">
        <thead className="bg-slate-50 text-left text-xs font-medium text-slate-500">
          <tr>
            <th className="px-3 py-2">ID</th>
            <th className="px-3 py-2">Жалоба</th>
            <th className="px-3 py-2">Район</th>
            <th className="px-3 py-2">Категория</th>
            <th className="px-3 py-2">До крайнего срока</th>
          </tr>
        </thead>
        <tbody>
          {items.map(item => {
            const overdue = item.secondsToDeadline < 0
            return (
              <tr
                key={item.id}
                data-testid="burning-row"
                className={
                  overdue
                    ? 'burning-row burning-row--overdue border-t border-rose-100 bg-rose-50'
                    : 'burning-row border-t border-slate-100'
                }
              >
                <td className="px-3 py-2 text-slate-400">#{item.id}</td>
                <td className="px-3 py-2">
                  <Link to={`/complaints/${item.id}`} className="text-slate-900 hover:underline">
                    {item.title}
                  </Link>
                </td>
                <td className="px-3 py-2 text-slate-600">{districtLabel(item.districtCode)}</td>
                <td className="px-3 py-2 text-slate-600">{categoryLabel(item.category)}</td>
                <td className={`px-3 py-2 font-medium ${overdue ? 'text-rose-700' : 'text-slate-700'}`}>
                  {formatDeadline(item.secondsToDeadline)}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
