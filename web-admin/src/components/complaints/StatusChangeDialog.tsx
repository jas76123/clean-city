import { useState } from 'react'
import type { ChangeStatusRequest, Complaint, ComplaintStatus } from '@/api/types'
import { STATUS_META } from '@/lib/complaintMeta'
import { Button } from '@/components/ui/button'
import { DuplicatePicker } from './DuplicatePicker'

const MAX_COMMENT = 2000

interface Props {
  complaint: Complaint
  toStatus: ComplaintStatus
  submitting: boolean
  onClose: () => void
  onSubmit: (req: ChangeStatusRequest) => void
}

export function StatusChangeDialog({ complaint, toStatus, submitting, onClose, onSubmit }: Props) {
  const [comment, setComment] = useState('')
  const [duplicateOfId, setDuplicateOfId] = useState<number | null>(null)
  const isDuplicate = toStatus === 'DUPLICATE'

  const tooLong = comment.length > MAX_COMMENT
  const canSubmit =
    comment.trim().length > 0 && !tooLong && (!isDuplicate || duplicateOfId != null) && !submitting

  function submit() {
    if (!canSubmit) return
    onSubmit({
      toStatus,
      comment: comment.trim(),
      duplicateOfId: isDuplicate ? (duplicateOfId as number) : undefined,
    })
  }

  return (
    <div
      onClick={onClose}
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
    >
      <div
        role="dialog"
        onClick={(e) => e.stopPropagation()}
        className="w-full max-w-md rounded-lg bg-white p-5 shadow-xl"
      >
        <h3 className="text-lg font-semibold text-slate-800">
          Сменить статус → {STATUS_META[toStatus].label}
        </h3>
        <p className="mt-1 text-sm text-slate-500">«{complaint.title}»</p>

        {isDuplicate && (
          <div className="mt-3">
            <div className="mb-1 text-sm font-medium text-slate-700">Оригинал жалобы</div>
            <DuplicatePicker
              lat={complaint.latitude}
              lon={complaint.longitude}
              category={complaint.category}
              excludeId={complaint.id}
              selectedId={duplicateOfId}
              onSelect={setDuplicateOfId}
            />
          </div>
        )}

        <div className="mt-3">
          <label htmlFor="status-comment" className="mb-1 block text-sm font-medium text-slate-700">
            Комментарий (обязательно)
          </label>
          <textarea
            id="status-comment"
            value={comment}
            maxLength={MAX_COMMENT}
            onChange={(e) => setComment(e.target.value)}
            rows={4}
            className="w-full rounded border border-slate-300 p-2 text-sm"
          />
          <div className="text-right text-xs text-slate-400">
            {comment.length} / {MAX_COMMENT}
          </div>
        </div>

        <div className="mt-4 flex justify-end gap-2">
          <Button variant="outline" onClick={onClose} disabled={submitting}>
            Отмена
          </Button>
          <Button onClick={submit} disabled={!canSubmit}>
            {submitting ? 'Отправка…' : 'Подтвердить'}
          </Button>
        </div>
      </div>
    </div>
  )
}
