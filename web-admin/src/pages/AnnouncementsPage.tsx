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
