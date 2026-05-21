import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '@/auth/AuthContext'
import { extractApiError } from '@/api/errors'
import { validateAdminPassword } from './passwordRules'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card } from '@/components/ui/card'

export function AcceptInvitePage() {
  const [params] = useSearchParams()
  const token = params.get('token')
  const { status, acceptInvite } = useAuth()
  const navigate = useNavigate()

  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (status === 'authenticated') navigate('/overview', { replace: true })
  }, [status, navigate])

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
      setError('Ссылка-приглашение недействительна')
      return
    }
    setBusy(true)
    try {
      await acceptInvite(token, password)
    } catch (err) {
      setError(extractApiError(err).message)
    } finally {
      setBusy(false)
    }
  }

  if (!token) {
    return (
      <CenteredCard title="Приглашение">
        <p className="text-sm text-red-600">Ссылка-приглашение недействительна.</p>
        <Link to="/login" className="mt-3 block text-center text-sm text-slate-500 hover:underline">
          На страницу входа
        </Link>
      </CenteredCard>
    )
  }

  return (
    <CenteredCard title="Создание пароля">
      <p className="mb-4 text-sm text-slate-400">
        Задайте пароль для входа в админ-панель CleanCity.
      </p>
      <form onSubmit={handleSubmit} className="flex flex-col gap-3">
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
          {busy ? 'Сохранение…' : 'Создать пароль и войти'}
        </Button>
      </form>
    </CenteredCard>
  )
}

function CenteredCard({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="flex h-screen items-center justify-center bg-slate-50">
      <Card className="w-full max-w-sm p-6">
        <h1 className="mb-1 text-lg font-semibold text-slate-800">{title}</h1>
        {children}
      </Card>
    </div>
  )
}
