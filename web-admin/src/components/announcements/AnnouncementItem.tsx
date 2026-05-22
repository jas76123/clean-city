import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { ICON_STYLE_META, formatDistricts } from '@/lib/announcementMeta'
import { cn } from '@/lib/utils'
import type { Announcement } from '@/api/types'

interface Props {
  announcement: Announcement
  unpublishing: boolean
  onUnpublish: (id: number) => void
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('ru-RU', { day: 'numeric', month: 'long' })
}

export function AnnouncementItem({ announcement, unpublishing, onUnpublish }: Props) {
  const [confirming, setConfirming] = useState(false)
  const icon = ICON_STYLE_META[announcement.iconStyle]

  return (
    <div className="flex gap-3 rounded-lg border border-slate-200 p-3">
      <div
        className={cn(
          'flex size-8 shrink-0 items-center justify-center rounded-full text-sm font-semibold',
          icon.className,
        )}
        aria-hidden
      >
        {icon.glyph}
      </div>

      <div className="min-w-0 flex-1">
        <div className="text-sm font-semibold text-slate-800">{announcement.title}</div>
        <div className="mt-0.5 text-sm text-slate-600">{announcement.body}</div>
        <div className="mt-1 text-xs text-slate-400">
          Опубликовано: {formatDate(announcement.publishedAt)} · {formatDistricts(announcement.districts)}
        </div>
      </div>

      <div className="shrink-0 self-start">
        {confirming ? (
          <div className="flex items-center gap-2">
            <span className="text-xs text-slate-500">Точно снять?</span>
            <Button
              type="button"
              size="xs"
              variant="destructive"
              disabled={unpublishing}
              onClick={() => onUnpublish(announcement.id)}
            >
              Да
            </Button>
            <Button
              type="button"
              size="xs"
              variant="ghost"
              disabled={unpublishing}
              onClick={() => setConfirming(false)}
            >
              Отмена
            </Button>
          </div>
        ) : (
          <Button type="button" size="xs" variant="outline" onClick={() => setConfirming(true)}>
            Снять с публикации
          </Button>
        )}
      </div>
    </div>
  )
}
