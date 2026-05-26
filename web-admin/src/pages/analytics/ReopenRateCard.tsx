import { KpiCardWithTarget } from './KpiCardWithTarget'

interface Props {
  reopenRate: number      // 0..1
  reopenCount: number
  resolvedCount: number
  target: number          // in percent, e.g. 10
}

export function ReopenRateCard({ reopenRate, reopenCount, resolvedCount, target }: Props) {
  return (
    <KpiCardWithTarget
      label={`Reopen rate · ${reopenCount}/${resolvedCount}`}
      value={resolvedCount === 0 ? null : reopenRate * 100}
      unit="%"
      target={target}
      direction="lower-better"
    />
  )
}
