export function parseContentDisposition(header: string | undefined | null): string | null {
  if (!header) return null
  const match = header.match(/filename="?([^";]+)"?/i)
  return match ? match[1] : null
}
