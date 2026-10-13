import { useMemo, useState } from 'react'
import { ArrowDownRight, ArrowUpRight, Repeat } from 'lucide-react'
import { toast } from 'sonner'
import { CategoryIcon, CurrencyInput, useData } from '@/components/common'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectGroup, SelectItem, SelectLabel, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import { formatBRL } from '@/lib/format'
import { addMonths, dateInMonth, todayISO } from '@/lib/month'
import { useStore, useUI } from '@/store/store'
import type { Transaction, TxType } from '@/store/types'

export function TransactionDialog() {
  const { open, editing, defaults } = useUI((s) => s.txDialog)
  const closeTx = useUI((s) => s.closeTx)
  return (
    <Dialog open={open} onOpenChange={(o) => !o && closeTx()}>
      <DialogContent className="max-h-[92dvh] overflow-y-auto sm:max-w-md">
        {open && <TxForm key={editing?.id ?? 'new'} editing={editing} defaults={defaults} onDone={closeTx} />}
      </DialogContent>
    </Dialog>
  )
}

const NONE = '__none__'

function TxForm({ editing, defaults, onDone }: { editing?: Transaction; defaults?: Partial<Transaction>; onDone: () => void }) {
  const { buckets, categories, goals } = useData()
  const viewMonth = useUI((s) => s.month)
  const addTransaction = useStore((s) => s.addTransaction)
  const updateTransaction = useStore((s) => s.updateTransaction)
  const addRecurring = useStore((s) => s.addRecurring)

  const initial = { ...defaults, ...editing }
  const today = todayISO()
  const [type, setType] = useState<TxType>(initial.type ?? 'despesa')
  const [amount, setAmount] = useState<number | undefined>(initial.amount)
  const [description, setDescription] = useState(initial.description ?? '')
  const [categoryId, setCategoryId] = useState<string | undefined>(initial.categoryId)
  const [goalId, setGoalId] = useState<string | undefined>(initial.goalId)
  const [date, setDate] = useState(
    initial.date ?? (viewMonth === today.slice(0, 7) ? today : dateInMonth(viewMonth, Number(today.slice(8)))),
  )
  const [paid, setPaid] = useState<boolean | undefined>(initial.paid)
  const [repeat, setRepeat] = useState(false)
  const [installments, setInstallments] = useState('')
  const [submitted, setSubmitted] = useState(false)

  const category = categories.find((c) => c.id === categoryId)
  const isPaid = paid ?? date <= today
  const showGoal = type === 'despesa' && (category?.kind === 'futuro' || !!goalId)
  const amountError = submitted && !(amount && amount > 0) ? 'Informe um valor maior que zero.' : undefined
  const categoryError = submitted && type === 'despesa' && !category ? 'Escolha uma categoria.' : undefined

  const grouped = useMemo(
    () =>
      buckets
        .map((b) => ({
          bucket: b,
          items: categories.filter((c) => c.bucketId === b.id && (!c.archived || c.id === initial.categoryId)),
        }))
        .filter((g) => g.items.length),
    [buckets, categories, initial.categoryId],
  )

  function pickCategory(id: string) {
    setCategoryId(id)
    const c = categories.find((x) => x.id === id)
    if (c?.goalId) setGoalId(c.goalId)
    else if (c?.kind !== 'futuro') setGoalId(undefined)
  }

  function submit(e: React.FormEvent) {
    e.preventDefault()
    setSubmitted(true)
    if (!(amount && amount > 0) || (type === 'despesa' && !category)) return
    const fallback = type === 'receita' ? 'Receita' : (category?.name ?? 'Despesa')
    const base = {
      type,
      amount,
      description: description.trim() || fallback,
      categoryId: type === 'despesa' ? categoryId : undefined,
      goalId: showGoal ? goalId : undefined,
      date,
      month: date.slice(0, 7),
      paid: isPaid,
    }
    if (editing) {
      updateTransaction(editing.id, base)
      toast.success('Lançamento atualizado')
    } else {
      let recurringId: string | undefined
      if (repeat) {
        const n = Number(installments)
        recurringId = addRecurring({
          type: base.type,
          description: base.description,
          amount: base.amount,
          categoryId: base.categoryId,
          goalId: base.goalId,
          day: Number(date.slice(8)),
          startMonth: base.month,
          endMonth: n > 1 ? addMonths(base.month, n - 1) : undefined,
        })
      }
      addTransaction({ ...base, recurringId })
      toast.success(repeat ? 'Lançamento salvo e repetido nos próximos meses' : 'Lançamento salvo', {
        description: `${base.description} · ${formatBRL(base.amount)}`,
      })
    }
    onDone()
  }

  return (
    <form onSubmit={submit} noValidate className="grid gap-4">
      <DialogHeader>
        <DialogTitle>{editing ? 'Editar lançamento' : 'Novo lançamento'}</DialogTitle>
        <DialogDescription>
          {editing?.recurringId
            ? 'Este lançamento vem de uma conta fixa. A alteração vale só para este mês.'
            : 'Registre uma entrada ou saída de dinheiro.'}
        </DialogDescription>
      </DialogHeader>

      <ToggleGroup
        type="single"
        variant="outline"
        value={type}
        onValueChange={(v) => v && setType(v as TxType)}
        className="w-full"
        aria-label="Tipo de lançamento"
      >
        <ToggleGroupItem value="despesa" className="h-10 flex-1 gap-2">
          <ArrowDownRight className="size-4" aria-hidden /> Despesa
        </ToggleGroupItem>
        <ToggleGroupItem value="receita" className="h-10 flex-1 gap-2">
          <ArrowUpRight className="size-4" aria-hidden /> Receita
        </ToggleGroupItem>
      </ToggleGroup>

      <div className="grid gap-1.5">
        <Label htmlFor="tx-amount">Valor *</Label>
        <CurrencyInput
          id="tx-amount"
          autoFocus
          className="h-11 text-lg font-medium"
          value={amount}
          onValueChange={setAmount}
          aria-invalid={!!amountError}
          aria-describedby={amountError ? 'tx-amount-error' : undefined}
          enterKeyHint="next"
        />
        {amountError && (
          <p id="tx-amount-error" role="alert" className="text-xs text-danger">
            {amountError}
          </p>
        )}
      </div>

      <div className="grid gap-1.5">
        <Label htmlFor="tx-desc">Descrição</Label>
        <Input
          id="tx-desc"
          value={description}
          maxLength={120}
          onChange={(e) => setDescription(e.target.value)}
          placeholder={type === 'receita' ? 'Ex.: Salário, freelance' : 'Ex.: Supermercado, aluguel'}
          enterKeyHint="next"
        />
      </div>

      {type === 'despesa' && (
        <div className="grid gap-1.5">
          <Label htmlFor="tx-category">Categoria *</Label>
          <Select value={categoryId} onValueChange={pickCategory}>
            <SelectTrigger
              id="tx-category"
              className="h-10 w-full"
              aria-invalid={!!categoryError}
              aria-describedby={categoryError ? 'tx-category-error' : undefined}
            >
              <SelectValue placeholder="Escolha uma categoria" />
            </SelectTrigger>
            <SelectContent className="max-h-80">
              {grouped.map(({ bucket, items }) => (
                <SelectGroup key={bucket.id}>
                  <SelectLabel>{bucket.name}</SelectLabel>
                  {items.map((c) => (
                    <SelectItem key={c.id} value={c.id}>
                      <CategoryIcon icon={c.icon} size="sm" className="size-6" />
                      {c.name}
                    </SelectItem>
                  ))}
                </SelectGroup>
              ))}
            </SelectContent>
          </Select>
          {categoryError && (
            <p id="tx-category-error" role="alert" className="text-xs text-danger">
              {categoryError}
            </p>
          )}
        </div>
      )}

      {showGoal && (
        <div className="grid gap-1.5">
          <Label htmlFor="tx-goal">Guardar em</Label>
          <Select value={goalId ?? NONE} onValueChange={(v) => setGoalId(v === NONE ? undefined : v)}>
            <SelectTrigger id="tx-goal" className="h-10 w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={NONE}>Nenhuma meta</SelectItem>
              {goals.map((g) => (
                <SelectItem key={g.id} value={g.id}>
                  <CategoryIcon icon={g.icon} size="sm" className="size-6" />
                  {g.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <p className="text-xs text-muted-foreground">O valor soma no saldo da meta quando estiver marcado como pago.</p>
        </div>
      )}

      <div className="grid grid-cols-2 gap-3">
        <div className="grid gap-1.5">
          <Label htmlFor="tx-date">Data</Label>
          <Input id="tx-date" type="date" required value={date} onChange={(e) => e.target.value && setDate(e.target.value)} className="h-10" />
        </div>
        <div className="flex items-end">
          <label className="flex h-10 w-full cursor-pointer items-center justify-between gap-2 rounded-lg border px-3 text-sm">
            {type === 'despesa' ? 'Já paguei' : 'Já recebi'}
            <Switch checked={isPaid} onCheckedChange={setPaid} />
          </label>
        </div>
      </div>

      {!editing && (
        <div className="grid gap-3 rounded-lg border p-3">
          <label className="flex cursor-pointer items-center justify-between gap-3 text-sm">
            <span className="flex items-center gap-2">
              <Repeat className="size-4 text-muted-foreground" aria-hidden />
              Repetir todo mês
            </span>
            <Switch checked={repeat} onCheckedChange={setRepeat} />
          </label>
          {repeat && (
            <div className="grid gap-1.5">
              <Label htmlFor="tx-installments">Número de parcelas (opcional)</Label>
              <Input
                id="tx-installments"
                type="number"
                inputMode="numeric"
                min={2}
                max={360}
                placeholder="Deixe vazio para repetir sem fim"
                value={installments}
                onChange={(e) => setInstallments(e.target.value)}
                className="h-10"
              />
              <p className="text-xs text-muted-foreground">
                Vira uma conta fixa: aparece automaticamente nos próximos meses, no dia {Number(date.slice(8))}.
              </p>
            </div>
          )}
        </div>
      )}

      <DialogFooter>
        <Button type="button" variant="outline" size="lg" onClick={onDone}>
          Cancelar
        </Button>
        <Button type="submit" size="lg">
          {editing ? 'Salvar alterações' : 'Adicionar'}
        </Button>
      </DialogFooter>
    </form>
  )
}
