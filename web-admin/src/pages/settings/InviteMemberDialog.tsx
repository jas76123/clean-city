import { useState } from 'react'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

type Props = {
  open: boolean
  loading?: boolean
  onSubmit: (email: string, role: 'ADMIN' | 'OPERATOR') => void
  onCancel: () => void
}

export function InviteMemberDialog({ open, loading = false, onSubmit, onCancel }: Props) {
  const [email, setEmail] = useState('')
  const [role, setRole] = useState<'ADMIN' | 'OPERATOR'>('OPERATOR')

  const handleSubmit = () => {
    if (!email.trim()) return
    onSubmit(email.trim(), role)
  }

  const handleOpenChange = (next: boolean) => {
    if (!next) {
      setEmail('')
      setRole('OPERATOR')
      onCancel()
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Пригласить сотрудника</DialogTitle>
          <DialogDescription>
            На указанный email придёт ссылка для активации. Действует 24 часа.
          </DialogDescription>
        </DialogHeader>
        <div className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="invite-email">Email</Label>
            <Input
              id="invite-email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="ivan@sochi.gov.ru"
              disabled={loading}
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label>Роль</Label>
            <Select
              value={role}
              onValueChange={(v) => setRole(v as 'ADMIN' | 'OPERATOR')}
              disabled={loading}
            >
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="OPERATOR">Оператор (обработка обращений)</SelectItem>
                <SelectItem value="ADMIN">Администратор (полный доступ)</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => handleOpenChange(false)} disabled={loading}>
            Отмена
          </Button>
          <Button onClick={handleSubmit} disabled={loading || !email.trim()}>
            {loading ? 'Отправляем…' : 'Пригласить'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
