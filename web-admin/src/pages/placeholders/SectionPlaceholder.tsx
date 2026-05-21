export function SectionPlaceholder({ title }: { title: string }) {
  return (
    <div className="flex h-full flex-col items-center justify-center text-center">
      <div className="text-lg font-semibold text-slate-700">{title}</div>
      <div className="mt-1 text-sm text-slate-400">Раздел в разработке</div>
    </div>
  )
}
