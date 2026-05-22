interface Props {
  page: number
  size: number
  total: number
  onPage: (page: number) => void
}

export function ComplaintsPagination({ page, size, total, onPage }: Props) {
  const lastPage = Math.max(0, Math.ceil(total / size) - 1)
  if (total === 0) return null
  return (
    <div className="flex items-center justify-end gap-3 py-3 text-sm text-slate-600">
      <span>
        Стр. {page + 1} из {lastPage + 1} · всего {total}
      </span>
      <button
        type="button"
        disabled={page <= 0}
        onClick={() => onPage(page - 1)}
        className="rounded border border-slate-300 px-2 py-1 disabled:opacity-40"
      >
        Назад
      </button>
      <button
        type="button"
        disabled={page >= lastPage}
        onClick={() => onPage(page + 1)}
        className="rounded border border-slate-300 px-2 py-1 disabled:opacity-40"
      >
        Вперёд
      </button>
    </div>
  )
}
