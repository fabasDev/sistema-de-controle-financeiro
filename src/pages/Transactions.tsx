import { useMemo, useState } from 'react'
import { ArrowUpRight, Check, EllipsisVertical, Pencil, Plus, Repeat, Search, Trash2, Undo2 } from 'lucide-react'
import { toast } from 'sonner'
import { CategoryIcon, EmptyState, PageHeader, useData } from '@/components/common'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import { formatBRL } from '@/lib/format'
import { diffMonths, longDayLabel, monthLabel } from '@/lib/month'
import { cn } from '@/lib/utils'
import { useStore, useUI } from '@/store/store'
import type { Transaction } from '@/store/types'

type Filter = 'todos' | 'despesa' | 'receita' | 'pendentes'

export default function Transactions() {
  const data = useData()
  const month = useUI((s) => s.month)
  const openTx = useUI((s) => s.openTx)
  const removeTransaction = useStore((s) => s.removeTransaction)
  const restoreTransaction = useStore((s) => s.restoreTransaction)
  const togglePaid = useStore((s) => s.togglePaid)
  const [filter, setFilter] = useState<Filter>('todos')
  const [categoryId, setCategoryId] = useState('all')
  const [query, setQuery] = useState('')

  const cats = useMemo(() => new Map(data.categories.map((c) => [c.id, c])), [data.categories])
  const recurring = useMemo(() => new Map(data.recurring.map((r) => [r.id, r])), [data.recurring])
  const monthTxs = data.transactions.filter((t) => t.month === month)

  const filtered = monthTxs
    .filter((t) => {
      if (filter === 'despesa' || filter === 'receita') return t.type === filter
      if (filter === 'pendentes') return !t.paid
      return true
    })
    .filter((t) => categoryId === 'all' || t.categoryId === categoryId)
    .filter((t) => {
      const q = query.trim().toLowerCase()
      if (!q) return true
      return t.description.toLowerCase().includes(q) || (cats.get(t.categoryId ?? '')?.name.toLowerCase().includes(q) ?? false)
    })
    .sort((a, b) => b.date.localeCompare(a.date) || a.type.localeCompare(b.type))

  const groups = filtered.reduce<{ date: string; items: Transaction[] }[]>((acc, t) => {
    const last = acc.at(-1)
    if (last?.date === t.date) last.items.push(t)
    else acc.push({ date: t.date, items: [t] })
    return acc
  }, [])

  const total = (type: 'receita' | 'despesa') => monthTxs.filter((t) => t.type === type).reduce((a, t) => a + t.amount, 0)
  const usedCategories = data.categories.filter((c) => monthTxs.some((t) => t.categoryId === c.id))

  function remove(t: Transaction) {
    const removed = removeTransaction(t.id)
    if (!removed) return
    toast('Lançamento excluído', {
      description: `${t.description} · ${formatBRL(t.amount)}`,
      action: { label: 'Desfazer', onClick: () => restoreTransaction(removed) },
    })
  }

  function installmentLabel(t: Transaction) {
    const r = t.recurringId ? recurring.get(t.recurringId) : undefined
    if (!r?.endMonth) return null
    return `${diffMonths(r.startMonth, t.month) + 1}/${diffMonths(r.startMonth, r.endMonth) + 1}`
  }

  return (
    <>
      <PageHeader
        title="Lançamentos"
        description={monthLabel(month)}
        actions={
          <Button size="lg" onClick={() => openTx()} className="hidden lg:inline-flex">
            <Plus aria-hidden /> Novo lançamento
          </Button>
        }
      />

      <div className="mb-4 grid grid-cols-3 gap-2 sm:gap-4">
        <Total label="Receitas" value={total('receita')} tone="success" />
        <Total label="Despesas" value={total('despesa')} />
        <Total label="Pendente" value={monthTxs.filter((t) => !t.paid && t.type === 'despesa').reduce((a, t) => a + t.amount, 0)} />
      </div>

      <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-center">
        <ToggleGroup
          type="single"
          variant="outline"
          value={filter}
          onValueChange={(v) => v && setFilter(v as Filter)}
          aria-label="Filtrar por tipo"
          className="w-full sm:w-auto"
        >
          <ToggleGroupItem value="todos" className="h-9 flex-1 px-3">Todos</ToggleGroupItem>
          <ToggleGroupItem value="despesa" className="h-9 flex-1 px-3">Despesas</ToggleGroupItem>
          <ToggleGroupItem value="receita" className="h-9 flex-1 px-3">Receitas</ToggleGroupItem>
          <ToggleGroupItem value="pendentes" className="h-9 flex-1 px-3">Pendentes</ToggleGroupItem>
        </ToggleGroup>
        <div className="flex gap-2 sm:ml-auto">
          <Select value={categoryId} onValueChange={setCategoryId}>
            <SelectTrigger className="h-9 w-full sm:w-48" aria-label="Filtrar por categoria">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">Todas as categorias</SelectItem>
              {usedCategories.map((c) => (
                <SelectItem key={c.id} value={c.id}>
                  {c.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <div className="relative w-full sm:w-56">
            <Search className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
            <Input
              type="search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Buscar"
              aria-label="Buscar lançamentos"
              className="h-9 pl-8"
            />
          </div>
        </div>
      </div>

      <Card className="py-2">
        <CardContent className="px-2 sm:px-4">
          {groups.length === 0 ? (
            <EmptyState
              icon="Receipt"
              title={monthTxs.length ? 'Nada encontrado com esses filtros' : 'Nenhum lançamento neste mês'}
              description={monthTxs.length ? 'Tente limpar a busca ou trocar o filtro.' : 'Adicione receitas e despesas para acompanhar seu mês.'}
              action={
                !monthTxs.length && (
                  <Button onClick={() => openTx()}>
                    <Plus aria-hidden /> Adicionar lançamento
                  </Button>
                )
              }
            />
          ) : (
            groups.map((g) => (
              <section key={g.date} aria-label={longDayLabel(g.date)}>
                <h2 className="z-10 bg-card/95 px-2 pt-3 pb-1 text-xs font-medium text-muted-foreground backdrop-blur sm:sticky sm:top-16">
                  {longDayLabel(g.date)}
                </h2>
                <ul>
                  {g.items.map((t) => {
                    const c = cats.get(t.categoryId ?? '')
                    const parcel = installmentLabel(t)
                    return (
                      <li key={t.id} className="flex items-center gap-3 rounded-lg px-2 py-2.5 hover:bg-muted/50">
                        {t.type === 'receita' ? (
                          <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-success/12 text-success">
                            <ArrowUpRight className="size-4" aria-hidden />
                          </span>
                        ) : (
                          <CategoryIcon icon={c?.icon} />
                        )}
                        <button
                          type="button"
                          className="min-w-0 flex-1 text-left"
                          onClick={() => openTx({ editing: t })}
                          aria-label={`Editar ${t.description}`}
                        >
                          <p className="truncate text-sm font-medium">{t.description}</p>
                          <p className="mt-0.5 flex flex-wrap items-center gap-1.5 text-xs text-muted-foreground">
                            <span>{t.type === 'receita' ? 'Receita' : (c?.name ?? 'Sem categoria')}</span>
                            {t.recurringId && (
                              <Badge variant="outline" className="h-5 gap-1 px-1.5 text-[11px]">
                                <Repeat className="size-3" aria-hidden /> {parcel ? `Parcela ${parcel}` : 'Fixa'}
                              </Badge>
                            )}
                            {!t.paid && (
                              <Badge variant="secondary" className="h-5 px-1.5 text-[11px]">
                                {t.type === 'despesa' ? 'A pagar' : 'A receber'}
                              </Badge>
                            )}
                          </p>
                        </button>
                        <span className={cn('shrink-0 text-sm font-semibold tabular', t.type === 'receita' && 'text-success', !t.paid && 'opacity-70')}>
                          {t.type === 'receita' ? '+ ' : '− '}
                          {formatBRL(t.amount)}
                        </span>
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <Button variant="ghost" size="icon" className="size-9 shrink-0" aria-label={`Ações para ${t.description}`}>
                              <EllipsisVertical aria-hidden />
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end">
                            <DropdownMenuItem onSelect={() => openTx({ editing: t })}>
                              <Pencil /> Editar
                            </DropdownMenuItem>
                            <DropdownMenuItem onSelect={() => togglePaid(t.id)}>
                              {t.paid ? <Undo2 /> : <Check />}
                              {t.paid
                                ? `Marcar como ${t.type === 'despesa' ? 'não pago' : 'não recebido'}`
                                : `Marcar como ${t.type === 'despesa' ? 'pago' : 'recebido'}`}
                            </DropdownMenuItem>
                            <DropdownMenuSeparator />
                            <DropdownMenuItem variant="destructive" onSelect={() => remove(t)}>
                              <Trash2 /> Excluir
                            </DropdownMenuItem>
                          </DropdownMenuContent>
                        </DropdownMenu>
                      </li>
                    )
                  })}
                </ul>
              </section>
            ))
          )}
        </CardContent>
      </Card>
    </>
  )
}

function Total({ label, value, tone }: { label: string; value: number; tone?: 'success' }) {
  return (
    <Card className="gap-1 px-3 py-3 sm:px-5 sm:py-4">
      <p className="text-xs text-muted-foreground sm:text-sm">{label}</p>
      <p className={cn('truncate text-base font-semibold tracking-tight sm:text-xl', tone === 'success' && 'text-success')}>{formatBRL(value)}</p>
    </Card>
  )
}
