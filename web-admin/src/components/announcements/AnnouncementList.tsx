import { AnnouncementItem } from './AnnouncementItem'
import type { Announcement } from '@/api/types'

interface Props {
  items: Announcement[]
  unpublishingId: number | null
  onUnpublish: (id: number) => void
}

export function AnnouncementList({ items, unpublishingId, onUnpublish }: Props) {
  if (items.length === 0) {
    return <div className="p-6 text-center text-sm text-slate-400">Объявлений пока нет</div>
  }
  return (
    <div className="flex flex-col gap-2.5">
      {items.map((a) => (
        <AnnouncementItem
          key={a.id}
          announcement={a}
          unpublishing={unpublishingId === a.id}
          onUnpublish={onUnpublish}
        />
      ))}
    </div>
  )
}
