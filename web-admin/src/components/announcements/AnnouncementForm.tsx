import { useState } from 'react'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { DISTRICTS } from '@/lib/complaintMeta'
import { ICON_STYLE_META, ICON_STYLE_ORDER } from '@/lib/announcementMeta'
import type { CreateAnnouncementRequest, IconStyle } from '@/api/types'
import { toEndOfDayIso } from '@/lib/dateUtils'

interface Props {
  submitting: boolean
  onSubmit: (req: CreateAnnouncementRequest) => void
}

export function AnnouncementForm({ submitting, onSubmit }: Props) {
  const [title, setTitle] = useState('')
  const [body, setBody] = useState('')
  const [iconStyle, setIconStyle] = useState<IconStyle>('INFO')
  const [allDistricts, setAllDistricts] = useState(true)
  const [districts, setDistricts] = useState<string[]>([])
  const [expiry, setExpiry] = useState('')

  const districtsMissing = !allDistricts && districts.length === 0
  const canSubmit =
    !submitting && title.trim().length > 0 && body.trim().length > 0 && !districtsMissing

  function toggleDistrict(d: string) {
    setDistricts((prev) => (prev.includes(d) ? prev.filter((x) => x !== d) : [...prev, d]))
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!canSubmit) return
    const req: CreateAnnouncementRequest = {
      title: title.trim(),
      body: body.trim(),
      iconStyle,
      districts: allDistricts ? [] : districts,
    }
    if (expiry) req.expiresAt = toEndOfDayIso(expiry)
    onSubmit(req)
  }

  return (
    <form onSubmit={handleSubmit}>
      <Card>
        <CardHeader className="border-b">
          <CardTitle>Новое объявление</CardTitle>
          <CardDescription>Будет опубликовано в приложении для горожан</CardDescription>
          <CardAction>
            <Button type="submit" disabled={!canSubmit}>
              {submitting ? 'Публикуем…' : 'Опубликовать'}
            </Button>
          </CardAction>
        </CardHeader>

        <CardContent className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="ann-title">Заголовок</Label>
            <Input
              id="ann-title"
              value={title}
              maxLength={300}
              placeholder="Например: Вывоз крупногабаритного мусора 25 мая"
              onChange={(e) => setTitle(e.target.value)}
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="ann-body">Текст объявления</Label>
            <textarea
              id="ann-body"
              value={body}
              maxLength={5000}
              rows={4}
              placeholder="Подробное описание, инструкции для горожан…"
              className="w-full rounded-lg border border-input bg-transparent px-2.5 py-1.5 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
              onChange={(e) => setBody(e.target.value)}
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label>Тип</Label>
            <div className="flex gap-2" role="group" aria-label="Тип">
              {ICON_STYLE_ORDER.map((style) => (
                <Button
                  key={style}
                  type="button"
                  size="sm"
                  variant={iconStyle === style ? 'default' : 'outline'}
                  aria-pressed={iconStyle === style}
                  onClick={() => setIconStyle(style)}
                >
                  {ICON_STYLE_META[style].label}
                </Button>
              ))}
            </div>
          </div>

          <div className="flex flex-col gap-1.5">
            <Label>Районы</Label>
            <label className="flex w-fit items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={allDistricts}
                onChange={(e) => setAllDistricts(e.target.checked)}
              />
              Все районы
            </label>
            {!allDistricts && (
              <div className="flex flex-wrap gap-3 pl-1">
                {DISTRICTS.map((d) => (
                  <label key={d} className="flex items-center gap-2 text-sm">
                    <input
                      type="checkbox"
                      checked={districts.includes(d)}
                      onChange={() => toggleDistrict(d)}
                    />
                    {d}
                  </label>
                ))}
              </div>
            )}
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="ann-expiry">Срок действия</Label>
            <div className="flex items-center gap-2">
              <Input
                id="ann-expiry"
                type="date"
                value={expiry}
                className="w-44"
                onChange={(e) => setExpiry(e.target.value)}
              />
              {expiry ? (
                <Button type="button" size="sm" variant="ghost" onClick={() => setExpiry('')}>
                  Без срока
                </Button>
              ) : (
                <span className="text-xs text-muted-foreground">Пусто — бессрочно</span>
              )}
            </div>
          </div>

          <p className="text-xs text-muted-foreground">
            После публикации жителям выбранных районов придёт push-уведомление.
          </p>
        </CardContent>
      </Card>
    </form>
  )
}
