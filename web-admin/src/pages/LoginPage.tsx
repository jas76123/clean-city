import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '@/auth/AuthContext'
import { extractApiError } from '@/api/errors'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card } from '@/components/ui/card'

export function LoginPage() {
  const { status, login, submit2fa } = useAuth()
  const navigate = useNavigate()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [challengeToken, setChallengeToken] = useState<string | null>(null)
  const [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (status === 'authenticated') navigate('/overview', { replace: true })
  }, [status, navigate])

  async function handleCredentials(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      const res = await login(email, password)
      if (res.requires2fa && res.challengeToken) {
        setChallengeToken(res.challengeToken)
      }
    } catch (err) {
      setError(extractApiError(err).message)
    } finally {
      setBusy(false)
    }
  }

  async function handle2fa(e: FormEvent) {
    e.preventDefault()
    if (!challengeToken) return
    setError(null)
    setBusy(true)
    try {
      await submit2fa(challengeToken, code)
    } catch (err) {
      setError(extractApiError(err).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="flex h-screen items-center justify-center bg-slate-50">
      <Card className="w-full max-w-sm p-6">
        <h1 className="mb-1 text-lg font-semibold text-slate-800">CleanCity — админ-панель</h1>
        <p className="mb-4 text-sm text-slate-400">
          {challengeToken ? 'Введите код из приложения-аутентификатора' : 'Вход для сотрудников'}
        </p>

        {!challengeToken ? (
          <form onSubmit={handleCredentials} className="flex flex-col gap-3">
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
            <div className="flex flex-col gap-1">
              <Label htmlFor="password">Пароль</Label>
              <Input
                id="password"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </div>
            {error && <p className="text-sm text-red-600">{error}</p>}
            <Button type="submit" disabled={busy}>
              {busy ? 'Вход…' : 'Войти'}
            </Button>
            <Link to="/forgot-password" className="text-center text-sm text-slate-500 hover:underline">
              Забыли пароль?
            </Link>
          </form>
        ) : (
          <form onSubmit={handle2fa} className="flex flex-col gap-3">
            <div className="flex flex-col gap-1">
              <Label htmlFor="code">Код подтверждения</Label>
              <Input
                id="code"
                inputMode="numeric"
                autoComplete="one-time-code"
                value={code}
                onChange={(e) => setCode(e.target.value)}
                required
              />
            </div>
            {error && <p className="text-sm text-red-600">{error}</p>}
            <Button type="submit" disabled={busy}>
              {busy ? 'Проверка…' : 'Подтвердить'}
            </Button>
          </form>
        )}
      </Card>
    </div>
  )
}
