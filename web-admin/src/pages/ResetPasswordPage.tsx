import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import * as authApi from '@/api/auth'
import { extractApiError } from '@/api/errors'
import { validateAdminPassword } from './passwordRules'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card } from '@/components/ui/card'

export function ResetPasswordPage() {
  const [params] = useSearchParams()
  const token = params.get('token')
  const navigate = useNavigate()

  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (password !== confirm) {
      setError('Пароли не совпадают')
      return
    }
    const ruleError = validateAdminPassword(password)
    if (ruleError) {
      setError(ruleError)
      return
    }
    if (!token) {
      setError('Ссылка для сброса недействительна')
      return
    }
    setBusy(true)
    try {
      await authApi.resetPassword(token, password)
      toast.success('Пароль обновлён. Войдите с новым паролем.')
      navigate('/login', { replace: true })
    } catch (err) {
      setError(extractApiError(err).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="flex h-screen items-center justify-center bg-slate-50">
      <Card className="w-full max-w-sm p-6">
        <h1 className="mb-1 text-lg font-semibold text-slate-800">Новый пароль</h1>
        {!token ? (
          <>
            <p className="mt-2 text-sm text-red-600">Ссылка для сброса недействительна.</p>
            <Link to="/login" className="mt-3 block text-center text-sm text-slate-500 hover:underline">
              На страницу входа
            </Link>
          </>
        ) : (
          <form onSubmit={handleSubmit} className="mt-2 flex flex-col gap-3">
            <div className="flex flex-col gap-1">
              <Label htmlFor="password">Новый пароль</Label>
              <Input
                id="password"
                type="password"
                autoComplete="new-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </div>
            <div className="flex flex-col gap-1">
              <Label htmlFor="confirm">Повторите пароль</Label>
              <Input
                id="confirm"
                type="password"
                autoComplete="new-password"
                value={confirm}
                onChange={(e) => setConfirm(e.target.value)}
                required
              />
            </div>
            <p className="text-xs text-slate-400">
              Минимум 12 символов, заглавная буква, цифра и спецсимвол.
            </p>
            {error && <p className="text-sm text-red-600">{error}</p>}
            <Button type="submit" disabled={busy}>
              {busy ? 'Сохранение…' : 'Сохранить пароль'}
            </Button>
          </form>
        )}
      </Card>
    </div>
  )
}
