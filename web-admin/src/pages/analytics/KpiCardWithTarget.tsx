interface Props {
  label: string
  value: number | null
  unit?: string
  target: number
  direction: 'higher-better' | 'lower-better'
  /** Optional href: if provided, card is wrapped in <a> */
  href?: string
}

function colorFor(
  value: number | null,
  target: number,
  direction: 'higher-better' | 'lower-better',
): 'good' | 'warn' | 'bad' {
  if (value === null) return 'warn'
  if (direction === 'higher-better') {
    if (value >= target) return 'good'
    if (value >= target * 0.75) return 'warn'
    return 'bad'
  }
  // lower-better
  if (value <= target) return 'good'
  if (value <= target * 2) return 'warn'
  return 'bad'
}

const TONE_CLASSES: Record<'good' | 'warn' | 'bad', string> = {
  good: 'border-emerald-200 bg-emerald-50',
  warn: 'border-amber-200 bg-amber-50',
  bad: 'border-rose-200 bg-rose-50',
}

const TONE_TEXT: Record<'good' | 'warn' | 'bad', string> = {
  good: 'text-emerald-700',
  warn: 'text-amber-700',
  bad: 'text-rose-700',
}

export function KpiCardWithTarget({ label, value, unit = '', target, direction, href }: Props) {
  const tone = colorFor(value, target, direction)
  const arrow = direction === 'higher-better' ? '≥' : '≤'
  const body = (
    <div
      data-testid="kpi-card-with-target"
      className={`kpi-card kpi-card--${tone} rounded-xl border p-4 ${TONE_CLASSES[tone]}`}
    >
      <div className="text-xs text-slate-500">{label}</div>
      <div className={`mt-1 text-2xl font-semibold ${TONE_TEXT[tone]}`}>
        {value === null ? '—' : `${value.toFixed(1)}${unit}`}
      </div>
      <div className="mt-1 text-xs text-slate-500">цель {arrow} {target}{unit}</div>
    </div>
  )
  return href ? <a href={href} className="block hover:opacity-90 transition-opacity">{body}</a> : body
}
