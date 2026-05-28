import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  freezeUser,
  inviteTeamMember,
  listTeamMembers,
  revokeInvitation,
  unfreezeUser,
} from '@/api/admin'
import { extractApiError } from '@/api/errors'
import type { TeamMemberDto, UserRole } from '@/api/types'
import { ConfirmActionDialog } from './ConfirmActionDialog'
import { InviteMemberDialog } from './InviteMemberDialog'
import { TeamTable } from './TeamTable'

type TabKey = 'active' | 'frozen' | 'pending'
type ActionKind = 'freeze' | 'unfreeze' | 'revoke'

type Props = { currentRole: UserRole }

const CONFIRM: Record<
  ActionKind,
  { title: string; message: string; confirmLabel: string; variant: 'default' | 'destructive' }
> = {
  freeze: {
    title: 'Заморозить сотрудника?',
    message: 'Все активные сессии будут отозваны. Сотрудник не сможет войти.',
    confirmLabel: 'Заморозить',
    variant: 'destructive',
  },
  unfreeze: {
    title: 'Разморозить сотрудника?',
    message: 'Сотрудник снова сможет войти в систему.',
    confirmLabel: 'Разморозить',
    variant: 'default',
  },
  revoke: {
    title: 'Отозвать приглашение?',
    message: 'Ссылка из письма перестанет работать.',
    confirmLabel: 'Отозвать',
    variant: 'destructive',
  },
}

export function TeamSection({ currentRole }: Props) {
  const qc = useQueryClient()
  const [tab, setTab] = useState<TabKey>('active')
  const [inviteOpen, setInviteOpen] = useState(false)
  const [pending, setPending] = useState<{ kind: ActionKind; member: TeamMemberDto } | null>(null)

  const activeQ = useQuery({
    queryKey: ['team', 'active'],
    queryFn: () => listTeamMembers('active'),
  })
  const frozenQ = useQuery({
    queryKey: ['team', 'frozen'],
    queryFn: () => listTeamMembers('frozen'),
  })
  const pendingQ = useQuery({
    queryKey: ['team', 'pending'],
    queryFn: () => listTeamMembers('pending'),
  })

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['team'] })
    qc.invalidateQueries({ queryKey: ['audit-log'] })
  }

  const inviteMut = useMutation({
    mutationFn: (vars: { email: string; role: 'ADMIN' | 'OPERATOR' }) =>
      inviteTeamMember(vars.email, vars.role),
    onSuccess: () => {
      toast.success('Приглашение отправлено')
      setInviteOpen(false)
      invalidate()
    },
    onError: (err) => toast.error(extractApiError(err).message),
  })

  const actionMut = useMutation({
    mutationFn: async (vars: { kind: ActionKind; userId: number }) => {
      if (vars.kind === 'freeze') return freezeUser(vars.userId)
      if (vars.kind === 'unfreeze') return unfreezeUser(vars.userId)
      return revokeInvitation(vars.userId)
    },
    onSuccess: () => {
      toast.success('Готово')
      setPending(null)
      invalidate()
    },
    onError: (err) => {
      toast.error(extractApiError(err).message)
      setPending(null)
    },
  })

  const counts = {
    active: activeQ.data?.length ?? 0,
    frozen: frozenQ.data?.length ?? 0,
    pending: pendingQ.data?.length ?? 0,
  }

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle>Команда</CardTitle>
        {currentRole === 'ADMIN' && (
          <Button onClick={() => setInviteOpen(true)}>Пригласить сотрудника</Button>
        )}
      </CardHeader>
      <CardContent>
        <Tabs value={tab} onValueChange={(v) => setTab(v as TabKey)}>
          <TabsList>
            <TabsTrigger value="active">Активные ({counts.active})</TabsTrigger>
            <TabsTrigger value="frozen">Замороженные ({counts.frozen})</TabsTrigger>
            <TabsTrigger value="pending">Ожидают ({counts.pending})</TabsTrigger>
          </TabsList>
          <TabsContent value="active">
            <TeamTable
              status="active"
              members={activeQ.data ?? []}
              currentRole={currentRole}
              onAction={(kind, member) => setPending({ kind, member })}
            />
          </TabsContent>
          <TabsContent value="frozen">
            <TeamTable
              status="frozen"
              members={frozenQ.data ?? []}
              currentRole={currentRole}
              onAction={(kind, member) => setPending({ kind, member })}
            />
          </TabsContent>
          <TabsContent value="pending">
            <TeamTable
              status="pending"
              members={pendingQ.data ?? []}
              currentRole={currentRole}
              onAction={(kind, member) => setPending({ kind, member })}
            />
          </TabsContent>
        </Tabs>
      </CardContent>

      <InviteMemberDialog
        open={inviteOpen}
        loading={inviteMut.isPending}
        onSubmit={(email, role) => inviteMut.mutate({ email, role })}
        onCancel={() => setInviteOpen(false)}
      />

      {pending && (
        <ConfirmActionDialog
          open
          title={CONFIRM[pending.kind].title}
          message={CONFIRM[pending.kind].message}
          confirmLabel={CONFIRM[pending.kind].confirmLabel}
          variant={CONFIRM[pending.kind].variant}
          loading={actionMut.isPending}
          onConfirm={() => actionMut.mutate({ kind: pending.kind, userId: pending.member.id })}
          onCancel={() => setPending(null)}
        />
      )}
    </Card>
  )
}
