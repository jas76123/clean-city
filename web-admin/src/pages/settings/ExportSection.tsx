import { useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { downloadMonthlyReport } from '@/api/analytics'

const MONTHS_NOMINATIVE = [
  'январь', 'февраль', 'март', 'апрель', 'май', 'июнь',
  'июль', 'август', 'сентябрь', 'октябрь', 'ноябрь', 'декабрь',
]

function previousMonthLabel(now: Date = new Date()): string {
  const prev = new Date(now.getFullYear(), now.getMonth() - 1, 1)
  return `${MONTHS_NOMINATIVE[prev.getMonth()]} ${prev.getFullYear()} г.`
}

export function ExportSection() {
  const [loading, setLoading] = useState(false)

  const onDownload = async () => {
    setLoading(true)
    try {
      const { blob, filename } = await downloadMonthlyReport()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = filename
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)
      toast.success('Отчёт скачан')
    } catch {
      toast.error('Не удалось сформировать отчёт. Попробуйте ещё раз.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <section className="flex flex-col gap-3">
      <div>
        <h2 className="text-lg font-semibold">Экспорт отчётов в PDF</h2>
        <p className="text-sm text-muted-foreground">Готовые шаблоны для отчётности</p>
      </div>
      <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
        <Card className="p-4">
          <div className="text-sm font-semibold">📄 Сводный отчёт за месяц</div>
          <div className="mt-1 text-xs text-muted-foreground">
            KPI, районы, SLA · Для отчёта мэрии
          </div>
          <div className="mt-2 text-xs text-muted-foreground">За {previousMonthLabel()}</div>
          <Button
            className="mt-3 self-start"
            size="sm"
            disabled={loading}
            onClick={onDownload}
          >
            {loading ? 'Готовим…' : 'Скачать PDF'}
          </Button>
        </Card>
      </div>
    </section>
  )
}
