interface StatusPipelineProps {
  newCount: number
  inProgressCount: number
  resolvedCount: number
  rejectedCount: number
  duplicateCount: number
}

const STAGES = [
  { key: 'newCount', label: 'В обработке', color: 'text-amber-600' },
  { key: 'inProgressCount', label: 'В работе', color: 'text-blue-600' },
  { key: 'resolvedCount', label: 'Решено', color: 'text-emerald-600' },
  { key: 'rejectedCount', label: 'Отклонено', color: 'text-slate-500' },
  { key: 'duplicateCount', label: 'Дубликаты', color: 'text-slate-500' },
] as const

export function StatusPipeline(props: StatusPipelineProps) {
  return (
    <div className="rounded-xl border bg-white p-4">
      <div className="mb-3 text-sm font-medium text-slate-700">Распределение за месяц</div>
      <div className="grid grid-cols-5 gap-2">
        {STAGES.map((s) => (
          <div key={s.key} className="text-center">
            <div className={`text-2xl font-semibold ${s.color}`}>{props[s.key]}</div>
            <div className="text-xs text-slate-500">{s.label}</div>
          </div>
        ))}
      </div>
    </div>
  )
}
