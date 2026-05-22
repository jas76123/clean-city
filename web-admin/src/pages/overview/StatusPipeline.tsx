interface StatusPipelineProps {
  newCount: number
  inProgress: number
  resolved: number
}

const STAGES = [
  { key: 'new', label: 'В обработке', color: 'text-amber-600' },
  { key: 'inProgress', label: 'В работе', color: 'text-blue-600' },
  { key: 'resolved', label: 'Решено', color: 'text-emerald-600' },
] as const

export function StatusPipeline({ newCount, inProgress, resolved }: StatusPipelineProps) {
  const values = { new: newCount, inProgress, resolved }
  return (
    <div className="rounded-xl border bg-white p-4">
      <div className="mb-3 text-sm font-medium text-slate-700">Распределение по статусам</div>
      <div className="grid grid-cols-3 gap-4">
        {STAGES.map((s) => (
          <div key={s.key} className="text-center">
            <div className={`text-2xl font-semibold ${s.color}`}>{values[s.key]}</div>
            <div className="text-xs text-slate-500">{s.label}</div>
          </div>
        ))}
      </div>
    </div>
  )
}
