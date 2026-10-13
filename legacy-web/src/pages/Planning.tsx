import { useMemo, useState } from 'react'
import { Pie, PieChart, Cell } from 'recharts'
import { Archive, ArchiveRestore, CircleCheck, Pencil, Plus, Trash2, TriangleAlert } from 'lucide-react'
import { toast } from 'sonner'
import { CategoryIcon, ChartTip, CurrencyInput, PageHeader, slotColor, useData } from '@/components/common'
import { IconPicker } from '@/components/IconPicker'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { ChartContainer, ChartTooltip } from '@/components/ui/chart'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Slider } from '@/components/ui/slider'
import { Switch } from '@/components/ui/switch'
import { summarizeMonth } from '@/lib/finance'
import { formatBRL } from '@/lib/format'
import { monthLabel } from '@/lib/month'
import { KIND_LABEL, MAX_BUCKETS, PRESETS, type RulePreset } from '@/lib/presets'
import { cn } from '@/lib/utils'
import { useStore, useUI } from '@/store/store'
import type { BucketKind, Category } from '@/store/types'

export default function Planning() {
  const data = useData()
  const month = useUI((s) => s.month)
  const summary = useMemo(() => summarizeMonth(data, month), [data, month])
  const { applyPreset, updateBucket, addBucket, removeBucket, normalizeBuckets } = useStore.getState()
  const total = data.buckets.reduce((a, b) => a + b.percent, 0)
  const income = summary.planIncome

  function choosePreset(id: RulePreset['id']) {
    const before = useStore.getState()
    const snapshot = { buckets: before.buckets, categories: before.categories, settings: before.settings }
    applyPreset(id)
    toast.success(`Regra aplicada: ${PRESETS.find((p) => p.id === id)?.name}`, {
      description: 'As categorias foram redistribuídas entre os novos potes.',
      action: { label: 'Desfazer', onClick: () => useStore.setState(snapshot) },
    })
  }

  return (
    <>
      <PageHeader
        title="Planejamento"
        description={`Defina como dividir sua renda. Base: ${formatBRL(income)} (${income === summary.income ? `renda de ${monthLabel(month).toLowerCase()}` : 'receitas fixas'})`}
      />

      <Card>
        <CardHeader>
          <CardTitle>Regra de distribuição</CardTitle>
          <CardDescription>Escolha um ponto de partida — depois ajuste os percentuais como quiser.</CardDescription>
          {data.settings.presetId === 'custom' && (
            <CardAction>
              <Badge>Personalizada</Badge>
            </CardAction>
          )}
        </CardHeader>
        <CardContent>
          <RadioGroup
            value={data.settings.presetId}
            onValueChange={(v) => choosePreset(v as RulePreset['id'])}
            className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4"
            aria-label="Regra de distribuição"
          >
            {PRESETS.map((p) => (
              <Label
                key={p.id}
                htmlFor={`preset-${p.id}`}
                className={cn(
                  'flex cursor-pointer flex-col items-start gap-2 rounded-xl border p-4 font-normal transition-colors hover:bg-muted/50',
                  data.settings.presetId === p.id && 'border-primary bg-accent/60',
                )}
              >
                <span className="flex w-full items-center justify-between gap-2">
                  <span className="font-semibold">{p.name}</span>
                  <RadioGroupItem id={`preset-${p.id}`} value={p.id} />
                </span>
                <span className="text-xs leading-relaxed text-muted-foreground">{p.description}</span>
                <span className="flex flex-wrap gap-1">
                  {p.buckets.map((b) => (
                    <Badge key={b.name} variant="secondary" className="text-[11px] font-normal">
                      {b.name} {b.percent}%
                    </Badge>
                  ))}
                </span>
              </Label>
            ))}
          </RadioGroup>
        </CardContent>
      </Card>

      <div className="mt-4 grid gap-4 lg:grid-cols-5">
        <Card className="lg:col-span-3">
          <CardHeader>
            <CardTitle>Seus potes</CardTitle>
            <CardDescription>Nome, tipo e percentual de cada parte da renda</CardDescription>
            <CardAction>
              <Button variant="outline" size="sm" onClick={addBucket} disabled={data.buckets.length >= MAX_BUCKETS}>
                <Plus aria-hidden /> Pote
              </Button>
            </CardAction>
          </CardHeader>
          <CardContent className="grid gap-4">
            {data.buckets.map((b, i) => (
              <fieldset key={b.id} className="grid gap-3 rounded-xl border p-3 sm:p-4">
                <legend className="sr-only">Pote {b.name}</legend>
                <div className="flex flex-wrap items-center gap-2">
                  <span aria-hidden className="size-3 shrink-0 rounded-[3px]" style={{ background: slotColor(i) }} />
                  <Input
                    value={b.name}
                    maxLength={40}
                    onChange={(e) => updateBucket(b.id, { name: e.target.value })}
                    aria-label="Nome do pote"
                    className="h-9 min-w-0 flex-1 font-medium"
                  />
                  <Select value={b.kind} onValueChange={(v) => updateBucket(b.id, { kind: v as BucketKind })}>
                    <SelectTrigger className="h-9 w-full sm:w-52" aria-label="Tipo do pote">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {(Object.keys(KIND_LABEL) as BucketKind[]).map((k) => (
                        <SelectItem key={k} value={k}>
                          {KIND_LABEL[k]}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="size-9"
                    onClick={() => removeBucket(b.id)}
                    disabled={data.buckets.length <= 1}
                    aria-label={`Remover pote ${b.name}`}
                  >
                    <Trash2 aria-hidden />
                  </Button>
                </div>
                <div className="flex items-center gap-3">
                  <Slider
                    value={[b.percent]}
                    min={0}
                    max={100}
                    step={1}
                    onValueChange={([v]) => updateBucket(b.id, { percent: v })}
                    aria-label={`Percentual de ${b.name}`}
                    className="flex-1"
                  />
                  <div className="relative w-20">
                    <Input
                      type="number"
                      inputMode="numeric"
                      min={0}
                      max={100}
                      value={b.percent}
                      onChange={(e) => updateBucket(b.id, { percent: Math.max(0, Math.min(100, Math.round(Number(e.target.value) || 0))) })}
                      aria-label={`Percentual de ${b.name} (número)`}
                      className="h-9 pr-6 text-right tabular"
                    />
                    <span aria-hidden className="pointer-events-none absolute top-1/2 right-2.5 -translate-y-1/2 text-sm text-muted-foreground">
                      %
                    </span>
                  </div>
                </div>
                <p className="text-xs text-muted-foreground">
                  <span className="font-medium text-foreground tabular">{formatBRL((income * b.percent) / 100)}</span> por mês
                  {b.description ? ` · ${b.description}` : ''}
                </p>
              </fieldset>
            ))}

            <div
              role="status"
              className={cn(
                'flex flex-wrap items-center justify-between gap-2 rounded-lg px-3 py-2 text-sm',
                total === 100 ? 'bg-success/10 text-success' : 'bg-warning/12 text-warning',
              )}
            >
              <span className="flex items-center gap-2 font-medium">
                {total === 100 ? <CircleCheck className="size-4" aria-hidden /> : <TriangleAlert className="size-4" aria-hidden />}
                Total: {total}%{total !== 100 && ` — ${total > 100 ? 'passou' : 'faltam'} ${Math.abs(100 - total)} pontos`}
              </span>
              {total !== 100 && (
                <Button size="sm" variant="outline" onClick={normalizeBuckets}>
                  Ajustar para 100%
                </Button>
              )}
            </div>
          </CardContent>
        </Card>

        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Distribuição planejada</CardTitle>
            <CardDescription>Renda base de {formatBRL(income)}</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-4">
            <div className="relative mx-auto aspect-square w-full max-w-64">
              <ChartContainer config={{}} className="aspect-square w-full" role="img" aria-label={`Gráfico de rosca: ${data.buckets.map((b) => `${b.name} ${b.percent}%`).join(', ')}`}>
                <PieChart>
                  <ChartTooltip content={(p) => <ChartTip {...p} valueFormatter={(v) => `${v}% · ${formatBRL((income * v) / 100)}`} labelFormatter={() => 'Pote'} />} />
                  <Pie
                    data={data.buckets.filter((b) => b.percent > 0)}
                    dataKey="percent"
                    nameKey="name"
                    innerRadius="62%"
                    outerRadius="100%"
                    paddingAngle={1.5}
                    stroke="var(--card)"
                    strokeWidth={2}
                    isAnimationActive={false}
                  >
                    {data.buckets
                      .map((b, i) => ({ b, i }))
                      .filter(({ b }) => b.percent > 0)
                      .map(({ b, i }) => (
                        <Cell key={b.id} fill={slotColor(i)} />
                      ))}
                  </Pie>
                </PieChart>
              </ChartContainer>
              <div className="pointer-events-none absolute inset-0 grid place-content-center text-center">
                <span className="text-xs text-muted-foreground">Renda</span>
                <span className="text-lg font-semibold">{formatBRL(income)}</span>
              </div>
            </div>
            <ul className="grid gap-2 text-sm">
              {data.buckets.map((b, i) => (
                <li key={b.id} className="flex items-center gap-2">
                  <span aria-hidden className="size-2.5 shrink-0 rounded-[3px]" style={{ background: slotColor(i) }} />
                  <span className="min-w-0 flex-1 truncate">{b.name}</span>
                  <span className="text-muted-foreground tabular">{b.percent}%</span>
                  <span className="w-28 text-right font-medium tabular">{formatBRL((income * b.percent) / 100)}</span>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      </div>

      <CategoriesCard />
    </>
  )
}

function CategoriesCard() {
  const { buckets, categories } = useData()
  const removeCategory = useStore((s) => s.removeCategory)
  const updateCategory = useStore((s) => s.updateCategory)
  const [editing, setEditing] = useState<Category | 'new' | null>(null)
  const [showArchived, setShowArchived] = useState(false)
  const archived = categories.filter((c) => c.archived)

  function remove(c: Category) {
    const result = removeCategory(c.id)
    toast(result === 'removed' ? 'Categoria excluída' : 'Categoria arquivada', {
      description: result === 'archived' ? 'Ela tem lançamentos, então foi arquivada para manter o histórico.' : undefined,
      action: result === 'archived' ? { label: 'Desfazer', onClick: () => updateCategory(c.id, { archived: false }) } : undefined,
    })
  }

  return (
    <Card className="mt-4">
      <CardHeader>
        <CardTitle>Categorias</CardTitle>
        <CardDescription>Cada categoria pertence a um pote. Defina limites mensais para receber alertas.</CardDescription>
        <CardAction>
          <Button size="sm" onClick={() => setEditing('new')}>
            <Plus aria-hidden /> Categoria
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent className="grid gap-6 md:grid-cols-2">
        {buckets.map((b, i) => {
          const items = categories.filter((c) => c.bucketId === b.id && !c.archived)
          return (
            <section key={b.id} aria-labelledby={`bucket-${b.id}`}>
              <h3 id={`bucket-${b.id}`} className="mb-2 flex items-center gap-2 text-sm font-semibold">
                <span aria-hidden className="size-2.5 rounded-[3px]" style={{ background: slotColor(i) }} />
                {b.name}
                <span className="font-normal text-muted-foreground">({items.length})</span>
              </h3>
              {items.length === 0 ? (
                <p className="rounded-lg border border-dashed px-3 py-4 text-center text-sm text-muted-foreground">Nenhuma categoria neste pote</p>
              ) : (
                <ul className="grid gap-1">
                  {items.map((c) => (
                    <li key={c.id} className="flex items-center gap-3 rounded-lg px-1 py-1.5 hover:bg-muted/50">
                      <CategoryIcon icon={c.icon} size="sm" />
                      <span className="min-w-0 flex-1 truncate text-sm">{c.name}</span>
                      {c.budget ? <span className="text-xs text-muted-foreground tabular">limite {formatBRL(c.budget)}</span> : null}
                      <Button variant="ghost" size="icon" className="size-8" onClick={() => setEditing(c)} aria-label={`Editar ${c.name}`}>
                        <Pencil aria-hidden />
                      </Button>
                      <Button variant="ghost" size="icon" className="size-8" onClick={() => remove(c)} aria-label={`Excluir ${c.name}`}>
                        <Trash2 aria-hidden />
                      </Button>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          )
        })}
      </CardContent>
      {archived.length > 0 && (
        <CardContent className="border-t pt-4">
          <label className="flex w-fit cursor-pointer items-center gap-2 text-sm text-muted-foreground">
            <Switch checked={showArchived} onCheckedChange={setShowArchived} /> Mostrar arquivadas ({archived.length})
          </label>
          {showArchived && (
            <ul className="mt-3 grid gap-1 md:grid-cols-2">
              {archived.map((c) => (
                <li key={c.id} className="flex items-center gap-3 rounded-lg px-1 py-1.5">
                  <Archive className="size-4 text-muted-foreground" aria-hidden />
                  <span className="flex-1 truncate text-sm text-muted-foreground">{c.name}</span>
                  <Button variant="ghost" size="sm" onClick={() => updateCategory(c.id, { archived: false })}>
                    <ArchiveRestore aria-hidden /> Restaurar
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      )}
      <CategoryDialog editing={editing} onClose={() => setEditing(null)} />
    </Card>
  )
}

function CategoryDialog({ editing, onClose }: { editing: Category | 'new' | null; onClose: () => void }) {
  return (
    <Dialog open={!!editing} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-h-[92dvh] overflow-y-auto sm:max-w-lg">
        {editing && <CategoryForm key={editing === 'new' ? 'new' : editing.id} editing={editing === 'new' ? undefined : editing} onDone={onClose} />}
      </DialogContent>
    </Dialog>
  )
}

const NO_GOAL = '__none__'

function CategoryForm({ editing, onDone }: { editing?: Category; onDone: () => void }) {
  const { buckets, goals } = useData()
  const addCategory = useStore((s) => s.addCategory)
  const updateCategory = useStore((s) => s.updateCategory)
  const [name, setName] = useState(editing?.name ?? '')
  const [icon, setIcon] = useState(editing?.icon ?? 'ShoppingBag')
  const [bucketId, setBucketId] = useState(editing?.bucketId ?? buckets[0].id)
  const [budget, setBudget] = useState<number | undefined>(editing?.budget)
  const [goalId, setGoalId] = useState(editing?.goalId)
  const [error, setError] = useState('')
  const bucket = buckets.find((b) => b.id === bucketId)

  function submit(e: React.FormEvent) {
    e.preventDefault()
    if (!name.trim()) return setError('Dê um nome para a categoria.')
    const payload = {
      name: name.trim(),
      icon,
      bucketId,
      budget: budget || undefined,
      goalId: bucket?.kind === 'futuro' ? goalId : undefined,
    }
    if (editing) updateCategory(editing.id, payload)
    else addCategory({ ...payload, kind: bucket?.kind ?? 'outros' })
    toast.success(editing ? 'Categoria atualizada' : 'Categoria criada')
    onDone()
  }

  return (
    <form onSubmit={submit} noValidate className="grid gap-4">
      <DialogHeader>
        <DialogTitle>{editing ? 'Editar categoria' : 'Nova categoria'}</DialogTitle>
        <DialogDescription>Categorias organizam seus gastos dentro dos potes.</DialogDescription>
      </DialogHeader>
      <div className="grid gap-1.5">
        <Label htmlFor="cat-name">Nome *</Label>
        <Input
          id="cat-name"
          autoFocus
          maxLength={40}
          value={name}
          onChange={(e) => {
            setName(e.target.value)
            setError('')
          }}
          aria-invalid={!!error}
          aria-describedby={error ? 'cat-name-error' : undefined}
        />
        {error && (
          <p id="cat-name-error" role="alert" className="text-xs text-danger">
            {error}
          </p>
        )}
      </div>
      <div className="grid gap-1.5">
        <Label id="cat-icon-label">Ícone</Label>
        <IconPicker value={icon} onChange={setIcon} labelledBy="cat-icon-label" />
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        <div className="grid gap-1.5">
          <Label htmlFor="cat-bucket">Pote</Label>
          <Select value={bucketId} onValueChange={setBucketId}>
            <SelectTrigger id="cat-bucket" className="h-10 w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {buckets.map((b) => (
                <SelectItem key={b.id} value={b.id}>
                  {b.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="cat-budget">Limite mensal (opcional)</Label>
          <CurrencyInput id="cat-budget" value={budget} onValueChange={setBudget} className="h-10" />
        </div>
      </div>
      {bucket?.kind === 'futuro' && (
        <div className="grid gap-1.5">
          <Label htmlFor="cat-goal">Enviar valores para a meta</Label>
          <Select value={goalId ?? NO_GOAL} onValueChange={(v) => setGoalId(v === NO_GOAL ? undefined : v)}>
            <SelectTrigger id="cat-goal" className="h-10 w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={NO_GOAL}>Escolher a cada lançamento</SelectItem>
              {goals.map((g) => (
                <SelectItem key={g.id} value={g.id}>
                  {g.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
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
