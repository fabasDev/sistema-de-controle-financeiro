import { useMemo, useState } from 'react'
import { CircleCheck, EllipsisVertical, Pencil, PiggyBank, Plus, Scale, ShieldCheck, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { CategoryIcon, CurrencyInput, Meter, PageHeader, useData } from '@/components/common'
import { IconPicker } from '@/components/IconPicker'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Slider } from '@/components/ui/slider'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import { goalBalance, goalPlan, recommendation } from '@/lib/finance'
import { formatBRL, formatDuration, formatNumber, formatPercent } from '@/lib/format'
import { addMonths, monthLabel } from '@/lib/month'
import { RESERVE_GOAL_ID } from '@/lib/presets'
import { cn } from '@/lib/utils'
import { useStore, useUI } from '@/store/store'
import type { Goal, ReserveBase } from '@/store/types'

export default function Goals() {
  const data = useData()
  const month = useUI((s) => s.month)
  const openTx = useUI((s) => s.openTx)
  const updateSettings = useStore((s) => s.updateSettings)
  const removeGoal = useStore((s) => s.removeGoal)
  const [editing, setEditing] = useState<Goal | 'new' | null>(null)
  const [adjusting, setAdjusting] = useState<Goal | null>(null)
  const rec = useMemo(() => recommendation(data, month), [data, month])
  const reserve = rec.reserve
  const reserveGoal = data.goals.find((g) => g.id === RESERVE_GOAL_ID)
  const others = data.goals.filter((g) => g.id !== RESERVE_GOAL_ID)
  const { settings } = data

  function contribute(goal: Goal, amount?: number) {
    const category = data.categories.find((c) => c.goalId === goal.id && !c.archived) ?? data.categories.find((c) => c.id === 'cat-metas')
    openTx({
      defaults: { type: 'despesa', categoryId: category?.id, goalId: goal.id, description: `Aporte: ${goal.name}`, amount },
    })
  }

  return (
    <>
      <PageHeader
        title="Metas"
        description="Reserva de emergência, investimentos e seus objetivos"
        actions={
          <Button size="lg" onClick={() => setEditing('new')}>
            <Plus aria-hidden /> Nova meta
          </Button>
        }
      />

      {reserveGoal && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <ShieldCheck className="size-5 text-primary" aria-hidden /> Reserva de emergência
            </CardTitle>
            <CardDescription>Dinheiro para imprevistos — perda de renda, saúde, conserto. Deixe em aplicação segura e de resgate rápido.</CardDescription>
            <CardAction className="flex gap-2">
              <Button variant="outline" size="sm" onClick={() => setAdjusting(reserveGoal)}>
                <Scale aria-hidden /> Ajustar saldo
              </Button>
            </CardAction>
          </CardHeader>
          <CardContent className="grid gap-6 lg:grid-cols-2">
            <div className="grid content-start gap-4">
              <div>
                <p className="text-sm text-muted-foreground">Saldo atual</p>
                <p className="text-4xl font-semibold tracking-tight">{formatBRL(reserve.balance)}</p>
                <p className="mt-1 text-sm text-muted-foreground">
                  de {formatBRL(reserve.target)} · cobre {formatNumber(reserve.monthsCovered)} de {settings.reserveMonths} meses
                </p>
              </div>
              <Meter value={reserve.balance} max={reserve.target} color="var(--chart-1)" label="Progresso da reserva de emergência" className="h-3" />
              <div className="grid gap-1 text-sm">
                {reserve.gap === 0 && reserve.target > 0 ? (
                  <p className="flex items-center gap-2 font-medium text-success">
                    <CircleCheck className="size-4" aria-hidden /> Reserva completa! Agora o foco pode ser investir.
                  </p>
                ) : (
                  <>
                    <p>
                      Faltam <span className="font-semibold">{formatBRL(reserve.gap)}</span> ({formatPercent(1 - reserve.progress)})
                    </p>
                    <p className="text-muted-foreground">
                      Sugestão para este mês: <span className="font-medium text-foreground">{formatBRL(rec.toReserve)}</span>
                      {rec.monthsToReserve ? ` — nesse ritmo, completa em ${formatDuration(rec.monthsToReserve)} (${monthLabel(addMonths(month, rec.monthsToReserve - 1)).toLowerCase()})` : ''}
                    </p>
                  </>
                )}
                <p className="text-xs text-muted-foreground">
                  Custo mensal considerado: {formatBRL(reserve.monthlyCost)} ({reserve.basis})
                </p>
              </div>
              <Button size="lg" className="w-full sm:w-fit" onClick={() => contribute(reserveGoal, rec.toReserve || undefined)}>
                <PiggyBank aria-hidden /> Registrar aporte na reserva
              </Button>
            </div>

            <div className="grid content-start gap-5 rounded-xl bg-muted/50 p-4">
              <div className="grid gap-2">
                <Label id="reserve-months">Quantos meses de custo guardar?</Label>
                <ToggleGroup
                  type="single"
                  variant="outline"
                  value={String(settings.reserveMonths)}
                  onValueChange={(v) => v && updateSettings({ reserveMonths: Number(v) })}
                  aria-labelledby="reserve-months"
                  className="w-full bg-card"
                >
                  {[3, 6, 9, 12].map((m) => (
                    <ToggleGroupItem key={m} value={String(m)} className="h-9 flex-1">
                      {m} meses
                    </ToggleGroupItem>
                  ))}
                </ToggleGroup>
                <p className="text-xs text-muted-foreground">CLT costuma usar 6 meses; autônomos e renda variável, 12.</p>
              </div>
              <div className="grid gap-2">
                <Label htmlFor="reserve-base">Custo mensal baseado em</Label>
                <Select value={settings.reserveBase} onValueChange={(v) => updateSettings({ reserveBase: v as ReserveBase })}>
                  <SelectTrigger id="reserve-base" className="h-10 w-full bg-card">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="necessidades">Gastos essenciais (média dos últimos 3 meses)</SelectItem>
                    <SelectItem value="gastos">Todos os gastos (média dos últimos 3 meses)</SelectItem>
                    <SelectItem value="manual">Um valor que eu definir</SelectItem>
                  </SelectContent>
                </Select>
                {settings.reserveBase === 'manual' && (
                  <CurrencyInput
                    aria-label="Custo mensal para a reserva"
                    value={settings.reserveManualCost}
                    onValueChange={(v) => updateSettings({ reserveManualCost: v ?? 0 })}
                    className="h-10 bg-card"
                  />
                )}
              </div>
              <div className="grid gap-2">
                <div className="flex items-baseline justify-between">
                  <Label id="reserve-priority">Prioridade da reserva</Label>
                  <span className="text-sm font-medium tabular">{settings.reservePriority}%</span>
                </div>
                <Slider
                  value={[settings.reservePriority]}
                  min={0}
                  max={100}
                  step={5}
                  onValueChange={([v]) => updateSettings({ reservePriority: v })}
                  aria-labelledby="reserve-priority"
                />
                <p className="text-xs text-muted-foreground">
                  Parte do valor “Futuro” que vai para a reserva até ela ficar completa. O restante ({100 - settings.reservePriority}%) vai para investimentos.
                </p>
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      <div className="mt-4 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {others.map((g) => (
          <GoalCard
            key={g.id}
            goal={g}
            onContribute={contribute}
            onEdit={() => setEditing(g)}
            onAdjust={() => setAdjusting(g)}
            onRemove={() => {
              removeGoal(g.id)
              toast('Meta excluída', { description: 'Os lançamentos ligados a ela foram mantidos.' })
            }}
          />
        ))}
        <button
          type="button"
          onClick={() => setEditing('new')}
          className="flex min-h-48 flex-col items-center justify-center gap-2 rounded-xl border border-dashed text-sm text-muted-foreground transition-colors hover:bg-muted/50 hover:text-foreground"
        >
          <Plus className="size-5" aria-hidden /> Criar meta (viagem, carro, casa…)
        </button>
      </div>

      <Dialog open={!!editing} onOpenChange={(o) => !o && setEditing(null)}>
        <DialogContent className="max-h-[92dvh] overflow-y-auto sm:max-w-lg">
          {editing && <GoalForm key={editing === 'new' ? 'new' : editing.id} editing={editing === 'new' ? undefined : editing} onDone={() => setEditing(null)} />}
        </DialogContent>
      </Dialog>
      <Dialog open={!!adjusting} onOpenChange={(o) => !o && setAdjusting(null)}>
        <DialogContent className="sm:max-w-sm">{adjusting && <AdjustForm goal={adjusting} onDone={() => setAdjusting(null)} />}</DialogContent>
      </Dialog>
    </>
  )
}

function GoalCard({
  goal,
  onContribute,
  onEdit,
  onAdjust,
  onRemove,
}: {
  goal: Goal
  onContribute: (g: Goal, amount?: number) => void
  onEdit: () => void
  onAdjust: () => void
  onRemove: () => void
}) {
  const { transactions } = useData()
  const month = useUI((s) => s.month)
  const plan = goalPlan(goal, transactions, month)
  const hasTarget = goal.target > 0
  return (
    <Card>
      <CardHeader>
        <div className="flex items-center gap-3">
          <CategoryIcon icon={goal.icon} color="var(--chart-1)" />
          <div className="min-w-0">
            <CardTitle className="truncate">{goal.name}</CardTitle>
            <CardDescription>
              {goal.kind === 'investimento' ? 'Investimentos' : goal.deadline ? `Até ${monthLabel(goal.deadline).toLowerCase()}` : 'Sem prazo'}
            </CardDescription>
          </div>
        </div>
        <CardAction>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="icon" className="size-9" aria-label={`Ações para ${goal.name}`}>
                <EllipsisVertical aria-hidden />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem onSelect={onEdit}>
                <Pencil /> Editar
              </DropdownMenuItem>
              <DropdownMenuItem onSelect={onAdjust}>
                <Scale /> Ajustar saldo
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem variant="destructive" onSelect={onRemove}>
                <Trash2 /> Excluir
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </CardAction>
      </CardHeader>
      <CardContent className="grid gap-3">
        <div>
          <p className="text-2xl font-semibold tracking-tight">{formatBRL(plan.balance)}</p>
          {hasTarget && <p className="text-sm text-muted-foreground">de {formatBRL(goal.target)}</p>}
        </div>
        {hasTarget && <Meter value={plan.balance} max={goal.target} label={`Progresso de ${goal.name}`} />}
        <div className="min-h-10 text-sm">
          {plan.done ? (
            <p className="flex items-center gap-2 font-medium text-success">
              <CircleCheck className="size-4" aria-hidden /> Meta alcançada!
            </p>
          ) : hasTarget ? (
            <>
              <p>
                {formatPercent(plan.progress)} concluído · faltam <span className="font-medium">{formatBRL(plan.remaining)}</span>
              </p>
              {plan.monthlyNeeded !== null && (
                <p className="text-muted-foreground">
                  Guarde <span className="font-medium text-foreground">{formatBRL(plan.monthlyNeeded)}</span>/mês por {plan.monthsLeft}{' '}
                  {plan.monthsLeft === 1 ? 'mês' : 'meses'}
                </p>
              )}
            </>
          ) : (
            <p className="text-muted-foreground">Acompanhe quanto já acumulou. Defina um alvo para ver o progresso.</p>
          )}
        </div>
        <Button variant="outline" onClick={() => onContribute(goal, plan.monthlyNeeded ?? undefined)}>
          <PiggyBank aria-hidden /> Aportar
        </Button>
      </CardContent>
    </Card>
  )
}

function GoalForm({ editing, onDone }: { editing?: Goal; onDone: () => void }) {
  const addGoal = useStore((s) => s.addGoal)
  const updateGoal = useStore((s) => s.updateGoal)
  const month = useUI((s) => s.month)
  const [name, setName] = useState(editing?.name ?? '')
  const [icon, setIcon] = useState(editing?.icon ?? 'Target')
  const [target, setTarget] = useState<number | undefined>(editing?.target || undefined)
  const [deadline, setDeadline] = useState(editing?.deadline ?? '')
  const [initial, setInitial] = useState<number | undefined>(editing?.initial || undefined)
  const [error, setError] = useState('')
  const isReserve = editing?.kind === 'reserva'

  function submit(e: React.FormEvent) {
    e.preventDefault()
    if (!name.trim()) return setError('Dê um nome para a meta.')
    const payload = {
      name: name.trim(),
      icon,
      target: target ?? 0,
      deadline: deadline || undefined,
      initial: initial ?? 0,
    }
    if (editing) updateGoal(editing.id, payload)
    else addGoal({ ...payload, kind: 'meta' })
    toast.success(editing ? 'Meta atualizada' : 'Meta criada')
    onDone()
  }

  return (
    <form onSubmit={submit} noValidate className="grid gap-4">
      <DialogHeader>
        <DialogTitle>{editing ? 'Editar meta' : 'Nova meta'}</DialogTitle>
        <DialogDescription>Defina o valor e o prazo — o app calcula quanto guardar por mês.</DialogDescription>
      </DialogHeader>
      <div className="grid gap-1.5">
        <Label htmlFor="goal-name">Nome *</Label>
        <Input
          id="goal-name"
          autoFocus
          maxLength={60}
          value={name}
          placeholder="Ex.: Viagem para o Nordeste"
          onChange={(e) => {
            setName(e.target.value)
            setError('')
          }}
          aria-invalid={!!error}
          aria-describedby={error ? 'goal-name-error' : undefined}
        />
        {error && (
          <p id="goal-name-error" role="alert" className="text-xs text-danger">
            {error}
          </p>
        )}
      </div>
      <div className="grid gap-1.5">
        <Label id="goal-icon-label">Ícone</Label>
        <IconPicker value={icon} onChange={setIcon} labelledBy="goal-icon-label" />
      </div>
      {!isReserve && (
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="grid gap-1.5">
            <Label htmlFor="goal-target">Valor da meta</Label>
            <CurrencyInput id="goal-target" value={target} onValueChange={setTarget} className="h-10" />
          </div>
          <div className="grid gap-1.5">
            <Label htmlFor="goal-deadline">Prazo (opcional)</Label>
            <Input id="goal-deadline" type="month" min={month} value={deadline} onChange={(e) => setDeadline(e.target.value)} className="h-10" />
          </div>
        </div>
      )}
      <div className="grid gap-1.5">
        <Label htmlFor="goal-initial">Quanto você já tem guardado para ela</Label>
        <CurrencyInput id="goal-initial" value={initial} onValueChange={setInitial} className="h-10" />
      </div>
      <DialogFooter>
        <Button type="button" variant="outline" size="lg" onClick={onDone}>
          Cancelar
        </Button>
        <Button type="submit" size="lg">
          Salvar
        </Button>
      </DialogFooter>
    </form>
  )
}

function AdjustForm({ goal, onDone }: { goal: Goal; onDone: () => void }) {
  const { transactions } = useData()
  const updateGoal = useStore((s) => s.updateGoal)
  const current = goalBalance(goal, transactions)
  const [value, setValue] = useState<number | undefined>(current)

  function submit(e: React.FormEvent) {
    e.preventDefault()
    const next = value ?? 0
    updateGoal(goal.id, { initial: goal.initial + (next - current) })
    toast.success('Saldo ajustado', { description: `${goal.name}: ${formatBRL(next)}` })
    onDone()
  }

  return (
    <form onSubmit={submit} className="grid gap-4">
      <DialogHeader>
        <DialogTitle>Ajustar saldo</DialogTitle>
        <DialogDescription>
          Informe quanto existe hoje em “{goal.name}” (por exemplo, depois de um resgate ou rendimento).
        </DialogDescription>
      </DialogHeader>
      <div className="grid gap-1.5">
        <Label htmlFor="adjust-value">Saldo atual</Label>
        <CurrencyInput id="adjust-value" autoFocus value={value} onValueChange={setValue} className={cn('h-11 text-lg')} />
        <p className="text-xs text-muted-foreground">Antes: {formatBRL(current)}</p>
      </div>
      <DialogFooter>
        <Button type="button" variant="outline" size="lg" onClick={onDone}>
          Cancelar
        </Button>
        <Button type="submit" size="lg">
          Salvar saldo
        </Button>
      </DialogFooter>
    </form>
  )
}

