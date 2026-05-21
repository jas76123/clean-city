import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import * as authApi from '@/api/auth'
import { extractApiError } from '@/api/errors'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card } from '@/components/ui/card'

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [sent, setSent] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await authApi.forgotPassword(email)
      setSent(true)
    } catch (err) {
      setError(extractApiError(err).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="flex h-screen items-center justify-center bg-slate-50">
      <Card className="w-full max-w-sm p-6">
        <h1 className="mb-1 text-lg font-semibold text-slate-800">Восстановление пароля</h1>
        {sent ? (
          <>
            <p className="mt-2 text-sm text-slate-500">
              Если такой email зарегистрирован, на него отправлено письмо со ссылкой
              для сброса пароля.
            </p>
            <Link to="/login" className="mt-4 block text-center text-sm text-slate-500 hover:underline">
              Вернуться ко входу
            </Link>
          </>
        ) : (
          <form onSubmit={handleSubmit} className="mt-2 flex flex-col gap-3">
            <div className="flex flex-col gap-1">
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                type="email"
                autoComplete="username"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </div>
            {error && <p className="text-sm text-red-600">{error}</p>}
            <Button type="submit" disabled={busy}>
              {busy ? 'Отправка…' : 'Отправить ссылку'}
            </Button>
            <Link to="/login" className="text-center text-sm text-slate-500 hover:underline">
              Вернуться ко входу
            </Link>
          </form>
        )}
      </Card>
    </div>
  )
}
