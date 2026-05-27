// Сочи — UTC+3 без перехода на летнее время.
const SOCHI_OFFSET = '+03:00'

/** date-инпут (YYYY-MM-DD) → ISO-8601 на конец дня с offset Сочи. */
export function toEndOfDayIso(date: string): string {
  return `${date}T23:59:59${SOCHI_OFFSET}`
}

/** Сегодняшняя дата в Сочи в формате YYYY-MM-DD — для атрибута min у <input type="date">. */
export function todayInSochi(now: Date = new Date()): string {
  const sochi = new Date(now.getTime() + 3 * 60 * 60 * 1000)
  return sochi.toISOString().slice(0, 10)
}
