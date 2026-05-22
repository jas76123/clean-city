import { useState } from 'react'
import type { ComplaintPhoto } from '@/api/types'

const BROKEN = 'data:image/svg+xml,%3Csvg xmlns=%22http://www.w3.org/2000/svg%22/%3E'

export function PhotoGallery({ photos }: { photos: ComplaintPhoto[] }) {
  const [zoom, setZoom] = useState<string | null>(null)
  if (photos.length === 0) {
    return <div className="text-sm text-slate-400">Фото нет</div>
  }
  return (
    <>
      <div className="flex flex-wrap gap-2">
        {photos.map((p) => (
          <img
            key={p.id}
            src={p.thumbUrl}
            alt="Фото жалобы"
            onClick={() => setZoom(p.photoUrl)}
            onError={(e) => {
              ;(e.target as HTMLImageElement).src = BROKEN
              ;(e.target as HTMLImageElement).title = 'фото недоступно'
            }}
            className="h-20 w-20 cursor-pointer rounded object-cover"
          />
        ))}
      </div>
      {zoom && (
        <div
          onClick={() => setZoom(null)}
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-8"
        >
          <img src={zoom} alt="Фото жалобы" className="max-h-full max-w-full rounded" />
        </div>
      )}
    </>
  )
}
