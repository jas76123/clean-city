interface SlaAlertBannerProps {
  count: number
}

/** Красный баннер вверху Overview. Рендерится только при count > 0 (проверка у вызывающего). */
export function SlaAlertBanner({ count }: SlaAlertBannerProps) {
  return (
    <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
      <strong>{count}</strong>{' '}
      {count === 1 ? 'жалоба превысила' : 'жалоб превысили'} норматив SLA — требуется внимание.
    </div>
  )
}
