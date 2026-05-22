interface SparklineProps {
  /** Серия значений. Последний столбик подсвечивается. */
  values: number[]
}

export function Sparkline({ values }: SparklineProps) {
  const max = Math.max(1, ...values)
  return (
    <div className="mt-2 flex h-10 items-end gap-1">
      {values.map((v, i) => (
        <div
          key={i}
          className={`flex-1 rounded-sm ${i === values.length - 1 ? 'bg-emerald-500' : 'bg-emerald-200'}`}
          style={{ height: `${Math.max(4, (v / max) * 100)}%` }}
        />
      ))}
    </div>
  )
}
