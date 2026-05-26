import { Link } from 'react-router-dom'
import type { BurningComplaintItem } from '@/api/types'

interface Props {
  items: BurningComplaintItem[]
}

function formatDeadline(seconds: number): string {
  const abs = Math.abs(seconds)
  const hours = Math.floor(abs / 3600)
  const mins = Math.floor((abs % 3600) / 60)
  const prefix = seconds < 0 ? '-' : '+'
  return `${prefix}${hours}ч ${mins}м`
}

export function BurningQueueTable({ items }: Props) {
  if (items.length === 0) {
    return <div className="rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-700">
      Нет горящих жалоб — все в порядке
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
            <th className="px-3 py-2">До дедлайна</th>
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
                <td className="px-3 py-2 text-slate-600">{item.districtCode ?? '—'}</td>
                <td className="px-3 py-2 text-slate-600">{item.category}</td>
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
