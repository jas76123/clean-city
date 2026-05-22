import type { ComplaintStatus } from '@/api/types'
import { STATUS_META } from '@/lib/complaintMeta'

const BASE = 'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium'

export function StatusBadge({ status, slaBreached }: { status: ComplaintStatus; slaBreached?: boolean }) {
  if (slaBreached) {
    return <span className={`${BASE} bg-red-100 text-red-700`}>⚠ SLA</span>
  }
  const meta = STATUS_META[status]
  return <span className={`${BASE} ${meta.className}`}>{meta.label}</span>
}
