import { useState } from 'react'
import { toast } from 'sonner'
import type { ChangeStatusRequest, ComplaintFilter, ComplaintStatus } from '@/api/types'
import {
  useComplaintsQuery,
  useComplaintQuery,
  useOverviewQuery,
  useChangeStatusMutation,
} from '@/hooks/complaintQueries'
import { extractApiError } from '@/api/errors'
import { ComplaintFilters } from '@/components/complaints/ComplaintFilters'
import { ComplaintsTable } from '@/components/complaints/ComplaintsTable'
import { ComplaintsPagination } from '@/components/complaints/ComplaintsPagination'
import { ComplaintDetailPanel } from '@/components/complaints/ComplaintDetailPanel'
import { StatusChangeDialog } from '@/components/complaints/StatusChangeDialog'

const INITIAL_FILTER: ComplaintFilter = {
  status: null, slaBreached: false, category: null, district: null, sort: 'date', page: 0,
}
const PAGE_SIZE = 20

export function ComplaintsPage() {
  const [filter, setFilter] = useState<ComplaintFilter>(INITIAL_FILTER)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [actionStatus, setActionStatus] = useState<ComplaintStatus | null>(null)

  const list = useComplaintsQuery(filter)
  const overview = useOverviewQuery()
  const detail = useComplaintQuery(selectedId)
  const mutation = useChangeStatusMutation()

  function submitStatusChange(req: ChangeStatusRequest) {
    if (selectedId == null) return
    mutation.mutate(
      { id: selectedId, req },
      {
        onSuccess: () => {
          toast.success('Статус изменён')
          setActionStatus(null)
        },
        onError: (err) => {
          const { code, message } = extractApiError(err)
          toast.error(message)
          if (code !== 'UNKNOWN') setActionStatus(null)
        },
      },
    )
  }

  return (
    <div className="flex h-full gap-4 p-4">
      <div className="flex flex-1 flex-col">
        <ComplaintFilters filter={filter} overview={overview.data} onChange={setFilter} />
        <div className="mt-3 flex-1 overflow-auto rounded border bg-white">
          {list.isError ? (
            <div className="p-6 text-center text-sm text-red-600">
              Не удалось загрузить список.{' '}
              <button onClick={() => list.refetch()} className="underline">
                Повторить
              </button>
            </div>
          ) : list.isLoading ? (
            <div className="p-6 text-center text-sm text-slate-400">Загрузка…</div>
          ) : (
            <ComplaintsTable
              items={list.data?.items ?? []}
              selectedId={selectedId}
              onSelect={setSelectedId}
            />
          )}
        </div>
        <ComplaintsPagination
          page={filter.page}
          size={PAGE_SIZE}
          total={list.data?.total ?? 0}
          onPage={(page) => setFilter((f) => ({ ...f, page }))}
        />
      </div>

      <div className="w-[420px] shrink-0 self-start rounded border bg-white">
        <ComplaintDetailPanel
          complaint={detail.data}
          isLoading={detail.isLoading && selectedId != null}
          isError={detail.isError}
          onAction={setActionStatus}
        />
      </div>

      {actionStatus && detail.data && (
        <StatusChangeDialog
          complaint={detail.data}
          toStatus={actionStatus}
          submitting={mutation.isPending}
          onClose={() => setActionStatus(null)}
          onSubmit={submitStatusChange}
        />
      )}
    </div>
  )
}
