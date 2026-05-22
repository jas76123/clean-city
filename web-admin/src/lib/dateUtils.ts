// Сочи — UTC+3 без перехода на летнее время.
const SOCHI_OFFSET = '+03:00'

/** date-инпут (YYYY-MM-DD) → ISO-8601 на конец дня с offset Сочи. */
export function toEndOfDayIso(date: string): string {
  return `${date}T23:59:59${SOCHI_OFFSET}`
}
