import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import type { TeamMemberDto, UserRole } from '@/api/types'

type ActionKind = 'freeze' | 'unfreeze' | 'revoke'

type Props = {
  status: 'active' | 'frozen' | 'pending'
  members: TeamMemberDto[]
  currentRole: UserRole
  onAction: (kind: ActionKind, member: TeamMemberDto) => void
}

const EMPTY_LABEL: Record<Props['status'], string> = {
  active: 'Нет активных сотрудников',
  frozen: 'Нет замороженных сотрудников',
  pending: 'Нет ожидающих приглашений',
}

function formatDate(iso: string | null): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function actionFor(status: Props['status']): ActionKind | null {
  if (status === 'active') return 'freeze'
  if (status === 'frozen') return 'unfreeze'
  if (status === 'pending') return 'revoke'
  return null
}

function actionLabel(kind: ActionKind): string {
  return kind === 'freeze' ? 'Заморозить' : kind === 'unfreeze' ? 'Разморозить' : 'Отозвать'
}

export function TeamTable({ status, members, currentRole, onAction }: Props) {
  if (members.length === 0) {
    return (
      <div className="py-8 text-center text-sm text-muted-foreground">
        {EMPTY_LABEL[status]}
      </div>
    )
  }

  const action = actionFor(status)
  const showActions = currentRole === 'ADMIN' && action !== null

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Email</TableHead>
          <TableHead>ФИО</TableHead>
          <TableHead>Роль</TableHead>
          <TableHead>Район</TableHead>
          <TableHead>{status === 'pending' ? 'Приглашён' : 'Последний вход'}</TableHead>
          {showActions && <TableHead className="text-right">Действия</TableHead>}
        </TableRow>
      </TableHeader>
      <TableBody>
        {members.map((m) => (
          <TableRow key={m.id}>
            <TableCell>{m.email}</TableCell>
            <TableCell>{m.fullName ?? '—'}</TableCell>
            <TableCell>
              <Badge variant={m.role === 'ADMIN' ? 'default' : 'secondary'}>
                {m.role === 'ADMIN' ? 'Админ' : 'Оператор'}
              </Badge>
            </TableCell>
            <TableCell>{m.district ?? 'Все районы'}</TableCell>
            <TableCell>{formatDate(status === 'pending' ? m.invitedAt : m.lastLoginAt)}</TableCell>
            {showActions && action && (
              <TableCell className="text-right">
                <Button
                  variant={action === 'unfreeze' ? 'default' : 'destructive'}
                  size="sm"
                  onClick={() => onAction(action, m)}
                >
                  {actionLabel(action)}
                </Button>
              </TableCell>
            )}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}
