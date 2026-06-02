import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import {
  useModerationSummaryQuery,
  useWarnMutation,
  useBanMutation,
  useUnbanMutation,
} from '@/hooks/moderationQueries'

interface Props {
  authorId: number
  complaintId: number
}

export function ModerationPanel({ authorId, complaintId }: Props) {
  const summary = useModerationSummaryQuery(authorId)
  const warn = useWarnMutation(authorId)
  const ban = useBanMutation(authorId)
  const unban = useUnbanMutation(authorId)
  const [reason, setReason] = useState('')

  if (summary.isLoading || !summary.data) return null
  const s = summary.data

  return (
    <div className="space-y-2 border-t pt-3">
      <div className="flex items-center gap-2 text-sm font-medium text-slate-700">
        Модерация автора
        {s.flagged && (
          <Badge className="bg-amber-100 text-amber-800">
            ⚠ {s.rejectedCountSinceWarning} отклонённых
          </Badge>
        )}
        {s.isWarned && (
          <Badge className="bg-slate-100 text-slate-600">предупреждён</Badge>
        )}
        {s.isBanned && (
          <Badge className="bg-red-100 text-red-700">заблокирован</Badge>
        )}
      </div>

      {!s.isBanned && (
        <>
          <label
            htmlFor="mod-reason"
            className="sr-only"
          >
            Причина
          </label>
          <textarea
            id="mod-reason"
            className="w-full rounded border p-2 text-sm"
            rows={2}
            placeholder="Причина (обязательно для предупреждения и блокировки)"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          />
        </>
      )}

      <div className="flex flex-wrap gap-2">
        {!s.isBanned && (
          <>
            <Button
              variant="outline"
              disabled={!reason.trim() || warn.isPending}
              onClick={() =>
                warn.mutate({ reason, complaintId }, { onSuccess: () => setReason('') })
              }
            >
              Предупредить
            </Button>
            <Button
              variant="destructive"
              disabled={!reason.trim() || ban.isPending}
              onClick={() => ban.mutate({ reason }, { onSuccess: () => setReason('') })}
            >
              Заблокировать
            </Button>
          </>
        )}
        {s.isBanned && (
          <Button
            variant="outline"
            disabled={unban.isPending}
            onClick={() => unban.mutate()}
          >
            Разблокировать
          </Button>
        )}
      </div>
    </div>
  )
}
