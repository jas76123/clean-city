interface StatusPipelineProps {
  breakdown: Record<string, number>
}

const STAGES = [
  { key: 'NEW', label: 'В обработке', color: 'text-amber-600' },
  { key: 'IN_PROGRESS', label: 'В работе', color: 'text-blue-600' },
  { key: 'RESOLVED', label: 'Решено', color: 'text-emerald-600' },
  { key: 'REJECTED', label: 'Отклонено', color: 'text-slate-500' },
  { key: 'DUPLICATE', label: 'Дубликаты', color: 'text-slate-500' },
] as const

export function StatusPipeline({ breakdown }: StatusPipelineProps) {
  return (
    <div className="rounded-xl border bg-white p-4">
      <div className="mb-3 text-sm font-medium text-slate-700">Распределение за 30 дней</div>
      <div className="grid grid-cols-5 gap-2">
        {STAGES.map((s) => (
          <div key={s.key} className="text-center">
            <div className={`text-2xl font-semibold ${s.color}`}>{breakdown[s.key] ?? 0}</div>
            <div className="text-xs text-slate-500">{s.label}</div>
          </div>
        ))}
      </div>
    </div>
  )
}
