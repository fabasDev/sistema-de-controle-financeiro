import { useState } from 'react'
import { ArrowUpRight, EllipsisVertical, Pencil, Plus, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { CategoryIcon, CurrencyInput, EmptyState, PageHeader, useData } from '@/components/common'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
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
import { Select, SelectContent, SelectGroup, SelectItem, SelectLabel, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import { formatBRL, formatPercent } from '@/lib/format'
import { addMonths, currentMonth, diffMonths, monthShort } from '@/lib/month'
import { cn } from '@/lib/utils'
import { useStore } from '@/store/store'
import type { Recurring, TxType } from '@/store/types'

export default function RecurringPage() {
  const { recurring, categories } = useData()
  const updateRecurring = useStore((s) => s.updateRecurring)
  const removeRecurring = useStore((s) => s.removeRecurring)
  const [editing, setEditing] = useState<Recurring | 'new' | null>(null)
  const now = currentMonth()
  const live = (r: Recurring) => r.active && r.startMonth <= now && (!r.endMonth || now <= r.endMonth)
  const isSaving = (r: Recurring) => categories.find((c) => c.id === r.categoryId)?.kind === 'futuro'
  const total = (list: Recurring[]) => list.filter(live).reduce((a, r) => a + r.amount, 0)
  const incomes = total(recurring.filter((r) => r.type === 'receita'))
  const bills = total(recurring.filter((r) => r.type === 'despesa' && !isSaving(r)))
  const autoSavings = total(recurring.filter((r) => r.type === 'despesa' && isSaving(r)))

  function remove(r: Recurring) {
    removeRecurring(r.id)
    toast('Conta fixa excluída', { description: 'Os lançamentos já pagos continuam no histórico.' })
  }

  const section = (type: TxType, title: string, description: string) => {
    const items = recurring.filter((r) => r.type === type).sort((a, b) => a.day - b.day)
    return (
      <Card>
        <CardHeader>
          <CardTitle>{title}</CardTitle>
          <CardDescription>{description}</CardDescription>
        </CardHeader>
        <CardContent>
          {items.length === 0 ? (
            <EmptyState
              icon={type === 'receita' ? 'Briefcase' : 'Receipt'}
              title={type === 'receita' ? 'Nenhuma receita fixa' : 'Nenhuma conta fixa'}
              description={type === 'receita' ? 'Ex.: salário, aluguel recebido, pensão.' : 'Ex.: aluguel, internet, plano de saúde, parcelas.'}
              className="py-6"
            />
          ) : (
            <ul className="grid gap-1">
              {items.map((r) => {
                const c = categories.find((x) => x.id === r.categoryId)
                const totalParcels = r.endMonth ? diffMonths(r.startMonth, r.endMonth) + 1 : 0
                const ended = r.endMonth ? now > r.endMonth : false
                return (
                  <li key={r.id} className={cn('flex items-center gap-3 rounded-lg px-1 py-2', (!r.active || ended) && 'opacity-60')}>
                    {type === 'receita' ? (
                      <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-success/12 text-success">
                        <ArrowUpRight className="size-4" aria-hidden />
                      </span>
                    ) : (
                      <CategoryIcon icon={c?.icon} />
                    )}
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium">{r.description}</p>
                      <p className="mt-0.5 flex flex-wrap items-center gap-1.5 text-xs text-muted-foreground">
                        Todo dia {r.day}
                        {c && ` · ${c.name}`}
                        {totalParcels > 0 && (
                          <Badge variant="outline" className="h-5 px-1.5 text-[11px]">
                            {ended
                              ? `Encerrada em ${monthShort(r.endMonth!)}`
                              : `${Math.min(totalParcels, Math.max(1, diffMonths(r.startMonth, now) + 1))}/${totalParcels} · até ${monthShort(r.endMonth!)}`}
                          </Badge>
                        )}
                        {r.startMonth > now && (
                          <Badge variant="outline" className="h-5 px-1.5 text-[11px]">
                            Começa em {monthShort(r.startMonth)}
                          </Badge>
                        )}
                      </p>
                    </div>
                    <span className={cn('text-sm font-semibold tabular', type === 'receita' && 'text-success')}>{formatBRL(r.amount)}</span>
                    <Switch
                      checked={r.active}
                      onCheckedChange={(active) => updateRecurring(r.id, { active })}
                      aria-label={`${r.active ? 'Pausar' : 'Ativar'} ${r.description}`}
                    />
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button variant="ghost" size="icon" className="size-9" aria-label={`Ações para ${r.description}`}>
                          <EllipsisVertical aria-hidden />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem onSelect={() => setEditing(r)}>
                          <Pencil /> Editar
                        </DropdownMenuItem>
                        <DropdownMenuSeparator />
                        <DropdownMenuItem variant="destructive" onSelect={() => remove(r)}>
                          <Trash2 /> Excluir
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </li>
                )
              })}
            </ul>
          )}
        </CardContent>
      </Card>
    )
  }

  return (
    <>
      <PageHeader
        title="Contas fixas"
        description="Receitas e despesas que se repetem todo mês entram sozinhas nos lançamentos."
        actions={
          <Button size="lg" onClick={() => setEditing('new')}>
            <Plus aria-hidden /> Nova conta fixa
          </Button>
        }
      />

      <div className="mb-4 grid gap-4 sm:grid-cols-3">
        <Card className="gap-1 px-5 py-4">
          <p className="text-sm text-muted-foreground">Receitas fixas / mês</p>
          <p className="text-xl font-semibold text-success">{formatBRL(incomes)}</p>
        </Card>
        <Card className="gap-1 px-5 py-4">
          <p className="text-sm text-muted-foreground">Despesas fixas / mês</p>
          <p className="text-xl font-semibold">{formatBRL(bills)}</p>
          {autoSavings > 0 && <p className="text-xs text-muted-foreground">+ {formatBRL(autoSavings)} em aportes automáticos</p>}
        </Card>
        <Card className="gap-1 px-5 py-4">
          <p className="text-sm text-muted-foreground">Renda comprometida</p>
          <p className="text-xl font-semibold">{incomes > 0 ? formatPercent(bills / incomes) : '—'}</p>
          <p className="text-xs text-muted-foreground">Ideal: até 50% em gastos fixos</p>
        </Card>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        {section('despesa', 'Despesas fixas e parcelas', 'Aparecem como “a pagar” em cada mês até você marcar como pagas.')}
        {section('receita', 'Receitas fixas', 'Usadas como base do planejamento quando o mês ainda não tem renda lançada.')}
      </div>

      <Dialog open={!!editing} onOpenChange={(o) => !o && setEditing(null)}>
        <DialogContent className="max-h-[92dvh] overflow-y-auto sm:max-w-md">
          {editing && (
            <RecurringForm key={editing === 'new' ? 'new' : editing.id} editing={editing === 'new' ? undefined : editing} onDone={() => setEditing(null)} />
          )}
        </DialogContent>
      </Dialog>
    </>
  )
}

const NONE = '__none__'

function RecurringForm({ editing, onDone }: { editing?: Recurring; onDone: () => void }) {
  const { buckets, categories, goals } = useData()
  const addRecurring = useStore((s) => s.addRecurring)
  const updateRecurring = useStore((s) => s.updateRecurring)
  const now = currentMonth()
  const [type, setType] = useState<TxType>(editing?.type ?? 'despesa')
  const [description, setDescription] = useState(editing?.description ?? '')
  const [amount, setAmount] = useState<number | undefined>(editing?.amount)
  const [categoryId, setCategoryId] = useState(editing?.categoryId)
  const [goalId, setGoalId] = useState(editing?.goalId)
  const [day, setDay] = useState(String(editing?.day ?? 10))
  const [startMonth, setStartMonth] = useState(editing?.startMonth ?? now)
  const [parcels, setParcels] = useState(editing?.endMonth ? String(diffMonths(editing.startMonth, editing.endMonth) + 1) : '')
  const [submitted, setSubmitted] = useState(false)
  const category = categories.find((c) => c.id === categoryId)
  const dayNum = Math.round(Number(day))

  const errors = {
    description: !description.trim() ? 'Informe uma descrição.' : '',
    amount: !(amount && amount > 0) ? 'Informe um valor maior que zero.' : '',
    category: type === 'despesa' && !category ? 'Escolha uma categoria.' : '',
    day: !(dayNum >= 1 && dayNum <= 31) ? 'Use um dia entre 1 e 31.' : '',
  }

  function submit(e: React.FormEvent) {
    e.preventDefault()
    setSubmitted(true)
    if (Object.values(errors).some(Boolean)) return
    const n = Number(parcels)
    const payload = {
      type,
      description: description.trim(),
      amount: amount!,
      categoryId: type === 'despesa' ? categoryId : undefined,
      goalId: type === 'despesa' && category?.kind === 'futuro' ? goalId : undefined,
      day: dayNum,
      startMonth,
      endMonth: n > 1 ? addMonths(startMonth, n - 1) : undefined,
    }
    if (editing) {
      updateRecurring(editing.id, payload)
      toast.success('Conta fixa atualizada', { description: 'Lançamentos não pagos deste mês em diante foram ajustados.' })
    } else {
      addRecurring(payload)
      toast.success('Conta fixa criada')
    }
    onDone()
  }

  const err = (k: keyof typeof errors) =>
    submitted && errors[k] ? (
      <p id={`rec-${k}-error`} role="alert" className="text-xs text-danger">
        {errors[k]}
      </p>
    ) : null
  const invalid = (k: keyof typeof errors) => ({
    'aria-invalid': submitted && !!errors[k],
    'aria-describedby': submitted && errors[k] ? `rec-${k}-error` : undefined,
  })

  return (
    <form onSubmit={submit} noValidate className="grid gap-4">
      <DialogHeader>
        <DialogTitle>{editing ? 'Editar conta fixa' : 'Nova conta fixa'}</DialogTitle>
        <DialogDescription>Ela será lançada automaticamente todo mês.</DialogDescription>
      </DialogHeader>
      <ToggleGroup type="single" variant="outline" value={type} onValueChange={(v) => v && setType(v as TxType)} className="w-full" aria-label="Tipo">
        <ToggleGroupItem value="despesa" className="h-10 flex-1">
          Despesa
        </ToggleGroupItem>
        <ToggleGroupItem value="receita" className="h-10 flex-1">
          Receita
        </ToggleGroupItem>
      </ToggleGroup>
      <div className="grid gap-1.5">
        <Label htmlFor="rec-desc">Descrição *</Label>
        <Input id="rec-desc" autoFocus maxLength={120} value={description} onChange={(e) => setDescription(e.target.value)} {...invalid('description')} />
        {err('description')}
      </div>
      <div className="grid grid-cols-[1fr_6rem] gap-3">
        <div className="grid gap-1.5">
          <Label htmlFor="rec-amount">Valor *</Label>
          <CurrencyInput id="rec-amount" value={amount} onValueChange={setAmount} className="h-10" {...invalid('amount')} />
          {err('amount')}
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="rec-day">Dia</Label>
          <Input id="rec-day" type="number" inputMode="numeric" min={1} max={31} value={day} onChange={(e) => setDay(e.target.value)} className="h-10" {...invalid('day')} />
          {err('day')}
        </div>
      </div>
      {type === 'despesa' && (
        <div className="grid gap-1.5">
          <Label htmlFor="rec-category">Categoria *</Label>
          <Select
            value={categoryId}
            onValueChange={(v) => {
              setCategoryId(v)
              setGoalId(categories.find((c) => c.id === v)?.goalId)
            }}
          >
            <SelectTrigger id="rec-category" className="h-10 w-full" {...invalid('category')}>
              <SelectValue placeholder="Escolha uma categoria" />
            </SelectTrigger>
            <SelectContent className="max-h-80">
              {buckets.map((b) => (
                <SelectGroup key={b.id}>
                  <SelectLabel>{b.name}</SelectLabel>
                  {categories
                    .filter((c) => c.bucketId === b.id && (!c.archived || c.id === categoryId))
                    .map((c) => (
                      <SelectItem key={c.id} value={c.id}>
                        <CategoryIcon icon={c.icon} size="sm" className="size-6" />
                        {c.name}
                      </SelectItem>
                    ))}
                </SelectGroup>
              ))}
            </SelectContent>
          </Select>
          {err('category')}
        </div>
      )}
      {type === 'despesa' && category?.kind === 'futuro' && (
        <div className="grid gap-1.5">
          <Label htmlFor="rec-goal">Guardar em</Label>
          <Select value={goalId ?? NONE} onValueChange={(v) => setGoalId(v === NONE ? undefined : v)}>
            <SelectTrigger id="rec-goal" className="h-10 w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={NONE}>Nenhuma meta</SelectItem>
              {goals.map((g) => (
                <SelectItem key={g.id} value={g.id}>
                  {g.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      )}
      <div className="grid grid-cols-2 gap-3">
        <div className="grid gap-1.5">
          <Label htmlFor="rec-start">Começa em</Label>
          <Input id="rec-start" type="month" value={startMonth} onChange={(e) => e.target.value && setStartMonth(e.target.value)} className="h-10" />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="rec-parcels">Parcelas</Label>
          <Input
            id="rec-parcels"
            type="number"
            inputMode="numeric"
            min={2}
            max={360}
            placeholder="Sem fim"
            value={parcels}
            onChange={(e) => setParcels(e.target.value)}
            className="h-10"
          />
        </div>
      </div>
      {Number(parcels) > 1 && (
        <p className="-mt-2 text-xs text-muted-foreground">
          Última parcela em {monthShort(addMonths(startMonth, Number(parcels) - 1))} · total de {formatBRL((amount ?? 0) * Number(parcels))}
        </p>
      )}
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
