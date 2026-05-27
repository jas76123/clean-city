import type { Complaint, ComplaintStatus } from '@/api/types'
import { CATEGORY_META } from '@/lib/complaintMeta'
import { allowedActions } from '@/lib/complaintMeta'
import { Button } from '@/components/ui/button'
import { StatusBadge } from './StatusBadge'
import { PhotoGallery } from './PhotoGallery'
import { StatusHistory } from './StatusHistory'

interface Props {
  complaint: Complaint | undefined
  isLoading: boolean
  isError: boolean
  onAction: (toStatus: ComplaintStatus) => void
}

function yandexMapsUrl(lat: number, lon: number): string {
  return `https://yandex.ru/maps/?pt=${lon},${lat}&z=17&l=map`
}

export function ComplaintDetailPanel({ complaint, isLoading, isError, onAction }: Props) {
  if (isError) {
    return <div className="p-6 text-sm text-red-600">Не удалось загрузить детали жалобы</div>
  }
  if (isLoading || !complaint) {
    return (
      <div className="flex h-full items-center justify-center p-6 text-center text-sm text-slate-400">
        {isLoading ? 'Загрузка…' : 'Кликни по строке — справа откроется детальная карточка'}
      </div>
    )
  }

  const actions = allowedActions(complaint.status)

  return (
    <div className="space-y-4 p-5">
      <div>
        <div className="flex items-center gap-2">
          <StatusBadge status={complaint.status} />
          {complaint.slaBreached && <StatusBadge status={complaint.status} slaBreached />}
        </div>
        <h2 className="mt-2 text-lg font-semibold text-slate-800">{complaint.title}</h2>
        <div className="text-sm text-slate-500">
          {CATEGORY_META[complaint.category].label}
        </div>
      </div>

      <PhotoGallery photos={complaint.photos} />

      <div className="text-sm text-slate-700">{complaint.description}</div>

      <div className="text-sm text-slate-600">
        <div className="font-medium text-slate-700">Местоположение</div>
        <div>
          {complaint.address}
          {complaint.district ? ` · ${complaint.district} р-н` : ''}
        </div>
        <div className="text-xs text-slate-400">
          {complaint.latitude.toFixed(5)}, {complaint.longitude.toFixed(5)}
        </div>
        <a
          href={yandexMapsUrl(complaint.latitude, complaint.longitude)}
          target="_blank"
          rel="noreferrer"
          className="text-xs text-blue-600 underline"
        >
          Открыть в Яндекс.Картах
        </a>
      </div>

      <div className="text-sm text-slate-600">
        <span className="font-medium text-slate-700">Голоса:</span> {complaint.votesCount}
      </div>

      <div>
        <div className="mb-1 text-sm font-medium text-slate-700">История статусов</div>
        <StatusHistory history={complaint.statusHistory} />
      </div>

      {actions.length > 0 && (
        <div className="flex flex-wrap gap-2 border-t pt-3">
          {actions.map((a) => (
            <Button key={a.toStatus} onClick={() => onAction(a.toStatus)}>
              {a.label}
            </Button>
          ))}
        </div>
      )}
    </div>
  )
}
