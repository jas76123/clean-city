# Day 17B — AnnouncementsPage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Заменить заглушку маршрута `/announcements` в веб-админке CleanCity рабочей страницей: создание объявления, список опубликованных, снятие с публикации.

**Architecture:** Страница следует паттерну `ComplaintsPage` — page-компонент компонует презентационные подкомпоненты, данные идут через TanStack Query, тосты через `sonner`, ошибки нормализуются `extractApiError`. Презентационные компоненты (`AnnouncementForm`, `AnnouncementList`, `AnnouncementItem`) не знают про сеть — получают данные и колбэки пропсами, что делает их тестируемыми в изоляции. Бэкенд CRUD `/announcements` уже готов и не меняется.

**Tech Stack:** React 19, TypeScript, Vite, TanStack Query v5, axios, Tailwind, base-ui, Vitest + Testing Library + MSW, sonner.

**Спек:** `docs/superpowers/specs/2026-05-24-day17b-announcements-design.md`

**Рабочая директория:** все пути ниже — относительно `web-admin/` (если не указано иное). Команды запускать из `web-admin/`. Ветка: `day17b-announcements` (уже создана).

---

## Справка по бэкенду (готов, НЕ меняем)

| Метод | Эндпоинт | Тело / параметры | Ответ |
|-------|----------|------------------|-------|
| `GET` | `/announcements?limit=100` | — | `{ items: Announcement[], total: number }` — только активные |
| `POST` | `/announcements` | `CreateAnnouncementRequest` | `201` + `Announcement` |
| `DELETE` | `/announcements/{id}` | — | `204 No Content` |

Поле `districts`: пустой список бэкенд сохраняет как `["ALL"]` (= все районы). `expiresAt` — ISO-8601 **с offset**, пусто = бессрочно. Создание (`POST`) триггерит push жителям; роли не из {ADMIN, OPERATOR, INSPECTOR} получают `403`.

---

## Структура файлов

| Файл | Ответственность |
|------|-----------------|
| `src/api/types.ts` (modify) | + типы `IconStyle`, `Announcement`, `AnnouncementsListResponse`, `CreateAnnouncementRequest` |
| `src/api/announcements.ts` (create) | 3 функции-обёртки над axios: list / create / unpublish |
| `src/lib/announcementMeta.ts` (create) | мета `IconStyle` (лейбл, глиф, цвет) + `formatDistricts` |
| `src/hooks/announcementQueries.ts` (create) | хуки TanStack Query: query списка + 2 мутации |
| `src/components/announcements/AnnouncementForm.tsx` (create) | форма создания (презентационная) |
| `src/components/announcements/AnnouncementItem.tsx` (create) | карточка одного объявления + инлайн-снятие |
| `src/components/announcements/AnnouncementList.tsx` (create) | список + empty state |
| `src/pages/AnnouncementsPage.tsx` (create) | компоновка: хуки → форма + список |
| `src/App.tsx` (modify) | маршрут `/announcements` → `<AnnouncementsPage />` |
| `docs/PLAN.md` (modify, корень репо) | отметить пункт 17B |

---

## Task 1: Типы и API-слой

**Files:**
- Modify: `src/api/types.ts` (добавить в конец файла)
- Create: `src/api/announcements.ts`
- Test: `src/api/announcements.test.ts`

- [ ] **Step 1: Добавить типы в `src/api/types.ts`**

Добавить в **конец** файла `src/api/types.ts` (тип `ProblemCategory` уже объявлен в этом файле выше — переиспользуем его):

```ts
export type IconStyle = 'INFO' | 'SUCCESS' | 'WARNING'

export interface Announcement {
  id: number
  title: string
  body: string
  iconStyle: IconStyle
  category: ProblemCategory | null
  districts: string[]
  authorId: number
  publishedAt: string
  expiresAt: string | null
}

export interface AnnouncementsListResponse {
  items: Announcement[]
  total: number
}

export interface CreateAnnouncementRequest {
  title: string
  body: string
  iconStyle: IconStyle
  districts: string[]
  expiresAt?: string
}
```

- [ ] **Step 2: Написать падающий тест `src/api/announcements.test.ts`**

```ts
import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { listAnnouncements, createAnnouncement, unpublishAnnouncement } from './announcements'

const BASE = 'http://localhost:8081'
const server = setupServer()

beforeAll(() => server.listen())
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('api/announcements', () => {
  it('listAnnouncements запрашивает limit=100', async () => {
    let seenUrl = ''
    server.use(
      http.get(`${BASE}/announcements`, ({ request }) => {
        seenUrl = request.url
        return HttpResponse.json({ items: [], total: 0 })
      }),
    )
    const res = await listAnnouncements()
    expect(res).toEqual({ items: [], total: 0 })
    expect(seenUrl).toContain('limit=100')
  })

  it('createAnnouncement шлёт POST с телом запроса', async () => {
    let body: unknown
    server.use(
      http.post(`${BASE}/announcements`, async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({ id: 1 }, { status: 201 })
      }),
    )
    await createAnnouncement({ title: 'T', body: 'B', iconStyle: 'INFO', districts: [] })
    expect(body).toMatchObject({ title: 'T', body: 'B', iconStyle: 'INFO', districts: [] })
  })

  it('unpublishAnnouncement шлёт DELETE по id', async () => {
    let method = ''
    server.use(
      http.delete(`${BASE}/announcements/:id`, ({ request, params }) => {
        method = request.method
        expect(params.id).toBe('7')
        return new HttpResponse(null, { status: 204 })
      }),
    )
    await unpublishAnnouncement(7)
    expect(method).toBe('DELETE')
  })
})
```

- [ ] **Step 3: Запустить тест — убедиться, что падает**

Run: `npx vitest run src/api/announcements.test.ts`
Expected: FAIL — `Failed to resolve import "./announcements"` (файл ещё не создан).

- [ ] **Step 4: Создать `src/api/announcements.ts`**

```ts
import { api } from './client'
import type { Announcement, AnnouncementsListResponse, CreateAnnouncementRequest } from './types'

export async function listAnnouncements(): Promise<AnnouncementsListResponse> {
  const res = await api.get<AnnouncementsListResponse>('/announcements', {
    params: { limit: 100 },
  })
  return res.data
}

export async function createAnnouncement(
  req: CreateAnnouncementRequest,
): Promise<Announcement> {
  const res = await api.post<Announcement>('/announcements', req)
  return res.data
}

export async function unpublishAnnouncement(id: number): Promise<void> {
  await api.delete(`/announcements/${id}`)
}
```

- [ ] **Step 5: Запустить тест — убедиться, что проходит**

Run: `npx vitest run src/api/announcements.test.ts`
Expected: PASS — 3 теста зелёные.

- [ ] **Step 6: Коммит**

```bash
git add src/api/types.ts src/api/announcements.ts src/api/announcements.test.ts
git commit -m "feat(web): API-слой объявлений + типы"
```

---

## Task 2: Мета IconStyle и форматирование районов

**Files:**
- Create: `src/lib/announcementMeta.ts`
- Test: `src/lib/announcementMeta.test.ts`

- [ ] **Step 1: Написать падающий тест `src/lib/announcementMeta.test.ts`**

```ts
import { describe, it, expect } from 'vitest'
import { ICON_STYLE_META, ICON_STYLE_ORDER, formatDistricts } from './announcementMeta'

describe('formatDistricts', () => {
  it('пустой список → «Все районы»', () => {
    expect(formatDistricts([])).toBe('Все районы')
  })

  it('список с ALL → «Все районы»', () => {
    expect(formatDistricts(['ALL'])).toBe('Все районы')
  })

  it('конкретные районы → перечисление через запятую', () => {
    expect(formatDistricts(['Центральный', 'Адлерский'])).toBe('Центральный, Адлерский')
  })
})

describe('ICON_STYLE_META', () => {
  it('покрывает три стиля и совпадает с порядком', () => {
    expect(Object.keys(ICON_STYLE_META).sort()).toEqual(['INFO', 'SUCCESS', 'WARNING'])
    expect([...ICON_STYLE_ORDER].sort()).toEqual(['INFO', 'SUCCESS', 'WARNING'])
  })
})
```

- [ ] **Step 2: Запустить тест — убедиться, что падает**

Run: `npx vitest run src/lib/announcementMeta.test.ts`
Expected: FAIL — `Failed to resolve import "./announcementMeta"`.

- [ ] **Step 3: Создать `src/lib/announcementMeta.ts`**

```ts
import type { IconStyle } from '@/api/types'

export interface IconStyleMeta {
  /** Подпись кнопки-переключателя типа в форме. */
  label: string
  /** Символ в цветном кружке у элемента списка. */
  glyph: string
  /** Tailwind-классы кружка (фон + текст). */
  className: string
}

export const ICON_STYLE_META: Record<IconStyle, IconStyleMeta> = {
  INFO: { label: 'Инфо', glyph: 'i', className: 'bg-blue-100 text-blue-700' },
  SUCCESS: { label: 'Успех', glyph: '✓', className: 'bg-emerald-100 text-emerald-700' },
  WARNING: { label: 'Предупреждение', glyph: '!', className: 'bg-amber-100 text-amber-800' },
}

export const ICON_STYLE_ORDER: IconStyle[] = ['INFO', 'SUCCESS', 'WARNING']

/** Человекочитаемая строка районов: пустой список или ALL → «Все районы». */
export function formatDistricts(districts: string[]): string {
  if (districts.length === 0 || districts.includes('ALL')) return 'Все районы'
  return districts.join(', ')
}
```

- [ ] **Step 4: Запустить тест — убедиться, что проходит**

Run: `npx vitest run src/lib/announcementMeta.test.ts`
Expected: PASS — 4 теста зелёные.

- [ ] **Step 5: Коммит**

```bash
git add src/lib/announcementMeta.ts src/lib/announcementMeta.test.ts
git commit -m "feat(web): мета IconStyle и formatDistricts для объявлений"
```

---

## Task 3: Хуки TanStack Query

**Files:**
- Create: `src/hooks/announcementQueries.ts`

> Хуки тонкие и зеркалят существующий `src/hooks/complaintQueries.ts` (у того нет юнит-тестов — хуки покрываются тестом страницы в Task 7). Поэтому здесь TDD не применяется; задача проверяется компиляцией TypeScript.

- [ ] **Step 1: Создать `src/hooks/announcementQueries.ts`**

```ts
import { useMutation, useQuery, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import {
  listAnnouncements,
  createAnnouncement,
  unpublishAnnouncement,
} from '@/api/announcements'
import type { CreateAnnouncementRequest } from '@/api/types'

// Список объявлений автообновляется раз в минуту — как таблица жалоб,
// чтобы админ видел чужие правки без ручного F5.
const ANNOUNCEMENTS_REFETCH_MS = 60_000

export function useAnnouncementsQuery() {
  return useQuery({
    queryKey: ['announcements'],
    queryFn: listAnnouncements,
    placeholderData: keepPreviousData,
    refetchInterval: ANNOUNCEMENTS_REFETCH_MS,
  })
}

export function useCreateAnnouncementMutation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (req: CreateAnnouncementRequest) => createAnnouncement(req),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['announcements'] }),
  })
}

export function useUnpublishAnnouncementMutation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => unpublishAnnouncement(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['announcements'] }),
  })
}
```

- [ ] **Step 2: Проверить компиляцию TypeScript**

Run: `npx tsc -b`
Expected: компиляция без ошибок (нет вывода, exit code 0).

- [ ] **Step 3: Коммит**

```bash
git add src/hooks/announcementQueries.ts
git commit -m "feat(web): хуки запросов объявлений"
```

---

## Task 4: AnnouncementForm — форма создания

**Files:**
- Create: `src/components/announcements/AnnouncementForm.tsx`
- Test: `src/components/announcements/AnnouncementForm.test.tsx`

Презентационный компонент. Пропсы: `submitting: boolean`, `onSubmit: (req: CreateAnnouncementRequest) => void`. Сброс формы после успешной публикации делается на уровне страницы перемонтированием через `key` (Task 7) — сам компонент про успех не знает.

- [ ] **Step 1: Написать падающий тест `src/components/announcements/AnnouncementForm.test.tsx`**

```ts
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AnnouncementForm } from './AnnouncementForm'

describe('AnnouncementForm', () => {
  it('кнопка «Опубликовать» заблокирована при пустых полях', () => {
    render(<AnnouncementForm submitting={false} onSubmit={vi.fn()} />)
    expect(screen.getByRole('button', { name: /опубликовать/i })).toBeDisabled()
  })

  it('кнопка активируется, когда заполнены заголовок и текст', async () => {
    render(<AnnouncementForm submitting={false} onSubmit={vi.fn()} />)
    await userEvent.type(screen.getByLabelText(/заголовок/i), 'Вывоз мусора')
    await userEvent.type(screen.getByLabelText(/текст объявления/i), 'Подробности')
    expect(screen.getByRole('button', { name: /опубликовать/i })).toBeEnabled()
  })

  it('при выключенном «Все районы» нужен хотя бы один отмеченный район', async () => {
    render(<AnnouncementForm submitting={false} onSubmit={vi.fn()} />)
    await userEvent.type(screen.getByLabelText(/заголовок/i), 'Заголовок')
    await userEvent.type(screen.getByLabelText(/текст объявления/i), 'Текст')
    await userEvent.click(screen.getByLabelText(/все районы/i)) // снять галочку «Все»
    expect(screen.getByRole('button', { name: /опубликовать/i })).toBeDisabled()
    await userEvent.click(screen.getByLabelText('Адлерский'))
    expect(screen.getByRole('button', { name: /опубликовать/i })).toBeEnabled()
  })

  it('submit передаёт собранный запрос (по умолчанию все районы)', async () => {
    const onSubmit = vi.fn()
    render(<AnnouncementForm submitting={false} onSubmit={onSubmit} />)
    await userEvent.type(screen.getByLabelText(/заголовок/i), 'Заголовок')
    await userEvent.type(screen.getByLabelText(/текст объявления/i), 'Текст')
    await userEvent.click(screen.getByRole('button', { name: /опубликовать/i }))
    expect(onSubmit).toHaveBeenCalledWith({
      title: 'Заголовок',
      body: 'Текст',
      iconStyle: 'INFO',
      districts: [],
    })
  })
})
```

- [ ] **Step 2: Запустить тест — убедиться, что падает**

Run: `npx vitest run src/components/announcements/AnnouncementForm.test.tsx`
Expected: FAIL — `Failed to resolve import "./AnnouncementForm"`.

- [ ] **Step 3: Создать `src/components/announcements/AnnouncementForm.tsx`**

```tsx
import { useState } from 'react'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { DISTRICTS } from '@/lib/complaintMeta'
import { ICON_STYLE_META, ICON_STYLE_ORDER } from '@/lib/announcementMeta'
import type { CreateAnnouncementRequest, IconStyle } from '@/api/types'

// Сочи — UTC+3 без перехода на летнее время.
const SOCHI_OFFSET = '+03:00'

/** date-инпут (YYYY-MM-DD) → ISO-8601 на конец дня с offset Сочи. */
function toEndOfDayIso(date: string): string {
  return `${date}T23:59:59${SOCHI_OFFSET}`
}

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
            <div className="flex gap-2">
              {ICON_STYLE_ORDER.map((style) => (
                <Button
                  key={style}
                  type="button"
                  size="sm"
                  variant={iconStyle === style ? 'default' : 'outline'}
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
        </CardContent>
      </Card>
    </form>
  )
}
```

- [ ] **Step 4: Запустить тест — убедиться, что проходит**

Run: `npx vitest run src/components/announcements/AnnouncementForm.test.tsx`
Expected: PASS — 4 теста зелёные.

- [ ] **Step 5: Коммит**

```bash
git add src/components/announcements/AnnouncementForm.tsx src/components/announcements/AnnouncementForm.test.tsx
git commit -m "feat(web): AnnouncementForm — форма создания объявления"
```

---

## Task 5: AnnouncementItem — карточка объявления с инлайн-снятием

**Files:**
- Create: `src/components/announcements/AnnouncementItem.tsx`
- Test: `src/components/announcements/AnnouncementItem.test.tsx`

Пропсы: `announcement: Announcement`, `unpublishing: boolean`, `onUnpublish: (id: number) => void`.

- [ ] **Step 1: Написать падающий тест `src/components/announcements/AnnouncementItem.test.tsx`**

```ts
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AnnouncementItem } from './AnnouncementItem'
import type { Announcement } from '@/api/types'

function ann(over: Partial<Announcement> = {}): Announcement {
  return {
    id: 1,
    title: 'Заголовок',
    body: 'Текст объявления',
    iconStyle: 'INFO',
    category: null,
    districts: [],
    authorId: 1,
    publishedAt: '2026-05-20T09:00:00Z',
    expiresAt: null,
    ...over,
  }
}

describe('AnnouncementItem', () => {
  it('рендерит заголовок, текст и районы', () => {
    render(<AnnouncementItem announcement={ann()} unpublishing={false} onUnpublish={vi.fn()} />)
    expect(screen.getByText('Заголовок')).toBeInTheDocument()
    expect(screen.getByText('Текст объявления')).toBeInTheDocument()
    expect(screen.getByText(/Все районы/)).toBeInTheDocument()
  })

  it('снятие требует инлайн-подтверждения', async () => {
    const onUnpublish = vi.fn()
    render(
      <AnnouncementItem announcement={ann({ id: 5 })} unpublishing={false} onUnpublish={onUnpublish} />,
    )
    await userEvent.click(screen.getByRole('button', { name: /снять с публикации/i }))
    expect(onUnpublish).not.toHaveBeenCalled()
    expect(screen.getByText(/точно снять/i)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /^да$/i }))
    expect(onUnpublish).toHaveBeenCalledWith(5)
  })

  it('«Отмена» возвращает исходную кнопку', async () => {
    render(<AnnouncementItem announcement={ann()} unpublishing={false} onUnpublish={vi.fn()} />)
    await userEvent.click(screen.getByRole('button', { name: /снять с публикации/i }))
    await userEvent.click(screen.getByRole('button', { name: /отмена/i }))
    expect(screen.getByRole('button', { name: /снять с публикации/i })).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Запустить тест — убедиться, что падает**

Run: `npx vitest run src/components/announcements/AnnouncementItem.test.tsx`
Expected: FAIL — `Failed to resolve import "./AnnouncementItem"`.

- [ ] **Step 3: Создать `src/components/announcements/AnnouncementItem.tsx`**

```tsx
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
```

- [ ] **Step 4: Запустить тест — убедиться, что проходит**

Run: `npx vitest run src/components/announcements/AnnouncementItem.test.tsx`
Expected: PASS — 3 теста зелёные.

- [ ] **Step 5: Коммит**

```bash
git add src/components/announcements/AnnouncementItem.tsx src/components/announcements/AnnouncementItem.test.tsx
git commit -m "feat(web): AnnouncementItem — карточка объявления с инлайн-снятием"
```

---

## Task 6: AnnouncementList — список с empty state

**Files:**
- Create: `src/components/announcements/AnnouncementList.tsx`
- Test: `src/components/announcements/AnnouncementList.test.tsx`

Пропсы: `items: Announcement[]`, `unpublishingId: number | null`, `onUnpublish: (id: number) => void`.

- [ ] **Step 1: Написать падающий тест `src/components/announcements/AnnouncementList.test.tsx`**

```ts
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { AnnouncementList } from './AnnouncementList'
import type { Announcement } from '@/api/types'

function ann(over: Partial<Announcement> = {}): Announcement {
  return {
    id: 1,
    title: 'Заголовок',
    body: 'Текст',
    iconStyle: 'INFO',
    category: null,
    districts: [],
    authorId: 1,
    publishedAt: '2026-05-20T09:00:00Z',
    expiresAt: null,
    ...over,
  }
}

describe('AnnouncementList', () => {
  it('показывает empty state на пустом списке', () => {
    render(<AnnouncementList items={[]} unpublishingId={null} onUnpublish={vi.fn()} />)
    expect(screen.getByText(/объявлений пока нет/i)).toBeInTheDocument()
  })

  it('рендерит элементы списка', () => {
    render(
      <AnnouncementList
        items={[ann({ id: 1, title: 'Первое' }), ann({ id: 2, title: 'Второе' })]}
        unpublishingId={null}
        onUnpublish={vi.fn()}
      />,
    )
    expect(screen.getByText('Первое')).toBeInTheDocument()
    expect(screen.getByText('Второе')).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Запустить тест — убедиться, что падает**

Run: `npx vitest run src/components/announcements/AnnouncementList.test.tsx`
Expected: FAIL — `Failed to resolve import "./AnnouncementList"`.

- [ ] **Step 3: Создать `src/components/announcements/AnnouncementList.tsx`**

```tsx
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
```

- [ ] **Step 4: Запустить тест — убедиться, что проходит**

Run: `npx vitest run src/components/announcements/AnnouncementList.test.tsx`
Expected: PASS — 2 теста зелёные.

- [ ] **Step 5: Коммит**

```bash
git add src/components/announcements/AnnouncementList.tsx src/components/announcements/AnnouncementList.test.tsx
git commit -m "feat(web): AnnouncementList — список объявлений"
```

---

## Task 7: AnnouncementsPage + подключение маршрута

**Files:**
- Create: `src/pages/AnnouncementsPage.tsx`
- Modify: `src/App.tsx`
- Test: `src/pages/AnnouncementsPage.test.tsx`

- [ ] **Step 1: Написать падающий тест `src/pages/AnnouncementsPage.test.tsx`**

```ts
import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { Toaster } from 'sonner'
import { AnnouncementsPage } from './AnnouncementsPage'
import type { Announcement } from '@/api/types'

const BASE = 'http://localhost:8081'

function ann(id: number, over: Partial<Announcement> = {}): Announcement {
  return {
    id,
    title: `Объявление ${id}`,
    body: 'текст',
    iconStyle: 'INFO',
    category: null,
    districts: [],
    authorId: 1,
    publishedAt: '2026-05-20T09:00:00Z',
    expiresAt: null,
    ...over,
  }
}

const server = setupServer()
beforeAll(() => server.listen())
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <>
        <AnnouncementsPage />
        <Toaster />
      </>
    </QueryClientProvider>,
  )
}

describe('AnnouncementsPage', () => {
  it('загружает и показывает список объявлений', async () => {
    server.use(
      http.get(`${BASE}/announcements`, () =>
        HttpResponse.json({ items: [ann(1), ann(2)], total: 2 }),
      ),
    )
    renderPage()
    expect(await screen.findByText('Объявление 1')).toBeInTheDocument()
    expect(screen.getByText('Объявление 2')).toBeInTheDocument()
  })

  it('показывает empty state, когда объявлений нет', async () => {
    server.use(http.get(`${BASE}/announcements`, () => HttpResponse.json({ items: [], total: 0 })))
    renderPage()
    expect(await screen.findByText(/объявлений пока нет/i)).toBeInTheDocument()
  })

  it('показывает ошибку загрузки с кнопкой «Повторить»', async () => {
    server.use(http.get(`${BASE}/announcements`, () => new HttpResponse(null, { status: 500 })))
    renderPage()
    expect(await screen.findByText(/не удалось загрузить/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /повторить/i })).toBeInTheDocument()
  })

  it('успешная публикация показывает toast', async () => {
    server.use(
      http.get(`${BASE}/announcements`, () => HttpResponse.json({ items: [], total: 0 })),
      http.post(`${BASE}/announcements`, () => HttpResponse.json(ann(99), { status: 201 })),
    )
    renderPage()
    await userEvent.type(await screen.findByLabelText(/заголовок/i), 'Новое')
    await userEvent.type(screen.getByLabelText(/текст объявления/i), 'Текст объявления')
    await userEvent.click(screen.getByRole('button', { name: /опубликовать/i }))
    await waitFor(() =>
      expect(screen.getByText('Объявление опубликовано')).toBeInTheDocument(),
    )
  })
})
```

- [ ] **Step 2: Запустить тест — убедиться, что падает**

Run: `npx vitest run src/pages/AnnouncementsPage.test.tsx`
Expected: FAIL — `Failed to resolve import "./AnnouncementsPage"`.

- [ ] **Step 3: Создать `src/pages/AnnouncementsPage.tsx`**

```tsx
import { useState } from 'react'
import { AxiosError } from 'axios'
import { toast } from 'sonner'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { AnnouncementForm } from '@/components/announcements/AnnouncementForm'
import { AnnouncementList } from '@/components/announcements/AnnouncementList'
import {
  useAnnouncementsQuery,
  useCreateAnnouncementMutation,
  useUnpublishAnnouncementMutation,
} from '@/hooks/announcementQueries'
import { extractApiError } from '@/api/errors'
import type { CreateAnnouncementRequest } from '@/api/types'

function statusOf(err: unknown): number | undefined {
  return err instanceof AxiosError ? err.response?.status : undefined
}

export function AnnouncementsPage() {
  // formKey растёт после успешной публикации — перемонтирует форму, сбрасывая её поля.
  const [formKey, setFormKey] = useState(0)
  const list = useAnnouncementsQuery()
  const createMutation = useCreateAnnouncementMutation()
  const unpublishMutation = useUnpublishAnnouncementMutation()

  function handleCreate(req: CreateAnnouncementRequest) {
    createMutation.mutate(req, {
      onSuccess: () => {
        toast.success('Объявление опубликовано')
        setFormKey((k) => k + 1)
      },
      onError: (err) => {
        if (statusOf(err) === 403) {
          toast.error('Недостаточно прав для публикации')
        } else {
          toast.error(extractApiError(err).message)
        }
      },
    })
  }

  function handleUnpublish(id: number) {
    unpublishMutation.mutate(id, {
      onSuccess: () => toast.success('Снято с публикации'),
      onError: (err) => {
        const status = statusOf(err)
        if (status === 403) {
          toast.error('Недостаточно прав')
        } else if (status === 404) {
          toast.error('Объявление уже снято')
          list.refetch()
        } else {
          toast.error(extractApiError(err).message)
        }
      },
    })
  }

  const unpublishingId =
    unpublishMutation.isPending && typeof unpublishMutation.variables === 'number'
      ? unpublishMutation.variables
      : null

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-4 p-4">
      <AnnouncementForm
        key={formKey}
        submitting={createMutation.isPending}
        onSubmit={handleCreate}
      />
      <p className="-mt-2 px-1 text-xs text-slate-400">
        После публикации жителям выбранных районов придёт push-уведомление.
      </p>

      <Card>
        <CardHeader className="border-b">
          <CardTitle>Опубликованные объявления</CardTitle>
          <CardDescription>Видны всем пользователям приложения</CardDescription>
        </CardHeader>
        <CardContent>
          {list.isError ? (
            <div className="p-6 text-center text-sm text-red-600">
              Не удалось загрузить объявления.{' '}
              <button onClick={() => list.refetch()} className="underline">
                Повторить
              </button>
            </div>
          ) : list.isLoading ? (
            <div className="p-6 text-center text-sm text-slate-400">Загрузка…</div>
          ) : (
            <AnnouncementList
              items={list.data?.items ?? []}
              unpublishingId={unpublishingId}
              onUnpublish={handleUnpublish}
            />
          )}
        </CardContent>
      </Card>
    </div>
  )
}
```

- [ ] **Step 4: Запустить тест — убедиться, что проходит**

Run: `npx vitest run src/pages/AnnouncementsPage.test.tsx`
Expected: PASS — 4 теста зелёные.

- [ ] **Step 5: Подключить маршрут в `src/App.tsx`**

Добавить импорт рядом с другими импортами страниц (после строки `import { AnalyticsPage } from '@/pages/AnalyticsPage'`):

```tsx
import { AnnouncementsPage } from '@/pages/AnnouncementsPage'
```

Заменить строку маршрута:

```tsx
            <Route path="/announcements" element={<SectionPlaceholder title="Объявления" />} />
```

на:

```tsx
            <Route path="/announcements" element={<AnnouncementsPage />} />
```

Импорт `SectionPlaceholder` оставить — он ещё используется маршрутом `/settings`.

- [ ] **Step 6: Проверить компиляцию и сборку**

Run: `npx tsc -b`
Expected: без ошибок (exit code 0).

- [ ] **Step 7: Коммит**

```bash
git add src/pages/AnnouncementsPage.tsx src/pages/AnnouncementsPage.test.tsx src/App.tsx
git commit -m "feat(web): AnnouncementsPage + маршрут /announcements"
```

---

## Task 8: Финальная проверка и отметка в плане

**Files:**
- Modify: `docs/PLAN.md` (корень репозитория, не `web-admin/`)

- [ ] **Step 1: Прогнать весь тест-сьют веб-админки**

Run (из `web-admin/`): `npm test`
Expected: PASS — все тесты зелёные, включая новые файлы Task 1–7.

- [ ] **Step 2: Прогнать линтер**

Run (из `web-admin/`): `npm run lint`
Expected: без ошибок.

- [ ] **Step 3: Прогнать production-сборку**

Run (из `web-admin/`): `npm run build`
Expected: сборка успешна (`tsc -b && vite build` без ошибок).

- [ ] **Step 4: Отметить пункт 17B в `docs/PLAN.md`**

В файле `docs/PLAN.md` (корень репозитория) найти строку:

```
- [ ] `AnnouncementsPage` — список + форма создания (title, body, icon, category, districts, expires_at)
```

Заменить на (отметка выполнения + уточнение, что реализовано без category и без edit):

```
- [x] `AnnouncementsPage` — форма создания (title, body, icon, districts, expires_at) + список + снятие с публикации. Без редактирования и без поля category — см. docs/superpowers/specs/2026-05-24-day17b-announcements-design.md.
```

- [ ] **Step 5: Коммит**

```bash
git add docs/PLAN.md
git commit -m "docs: отметить выполнение Day 17B (AnnouncementsPage)"
```

---

## Ручная проверка (после всех задач, опционально)

Запустить бэкенд + `cd web-admin && npm run dev` (:5173), залогиниться dev-админом
(`admin@cleancity.dev` / `Admin12345!`), открыть раздел «Объявления»:

1. Опубликовать объявление с типом «Успех» на «Все районы» → toast, форма очистилась, объявление появилось в списке.
2. Опубликовать с конкретными районами и сроком действия → проверить мета-строку в карточке.
3. «Снять с публикации» → инлайн «Точно снять?» → «Да» → объявление исчезло из списка.
4. (Опционально) проверить, что на mobile пришёл push при публикации.

---

## Самопроверка плана (выполнена автором)

- **Покрытие спека:** создание (Task 4, 7), список (Task 6, 7), снятие с инлайн-подтверждением (Task 5), API-слой (Task 1), хуки (Task 3), мета/`formatDistricts` (Task 2), маршрут (Task 7), обработка ошибок 400/403/404 (Task 7), тесты всех уровней (Task 1–7) — все разделы спека покрыты.
- **Плейсхолдеров нет:** все шаги содержат полный код и точные команды.
- **Согласованность типов:** `CreateAnnouncementRequest`, `Announcement`, `IconStyle` объявлены в Task 1 и используются в Task 3–7 с теми же именами полей; `ICON_STYLE_META`/`ICON_STYLE_ORDER`/`formatDistricts` из Task 2 используются в Task 4–5; пропсы компонентов (`submitting`/`onSubmit`, `unpublishing`/`onUnpublish`, `items`/`unpublishingId`) согласованы между Task 4–7.
- **Вне объёма:** редактирование (`PATCH`), поле category, архив снятых — намеренно не реализуются.
