import type { Complaint } from '@/api/types'

interface TopVotedComplaintsProps {
  items: Complaint[]
}

export function TopVotedComplaints({ items }: TopVotedComplaintsProps) {
  const top = items.slice(0, 5)
  return (
    <div className="rounded-xl border bg-white p-4">
      <div className="mb-3 text-sm font-medium text-slate-700">Топ-5 по голосам жителей</div>
      {top.length === 0 ? (
        <div className="py-6 text-center text-sm text-slate-400">Нет данных</div>
      ) : (
        <ol className="flex flex-col gap-2">
          {top.map((c, i) => (
            <li key={c.id} className="flex items-center gap-3 border-b border-slate-100 pb-2 last:border-0">
              <span className="w-5 text-sm font-semibold text-emerald-600">{i + 1}</span>
              <span className="flex-1 truncate text-xs text-slate-700">{c.title}</span>
              <span className="text-xs font-semibold text-slate-500">{c.votesCount} ▲</span>
            </li>
          ))}
        </ol>
      )}
    </div>
  )
}
