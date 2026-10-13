import { useMemo } from 'react'
import { Link } from 'react-router'
import { Bar, BarChart, CartesianGrid, XAxis, YAxis } from 'recharts'
import {
  ArrowDownRight,
  ArrowRight,
  ArrowUpRight,
  CircleCheck,
  PiggyBank,
  Plus,
  Repeat,
  ShieldCheck,
  Sparkles,
  TrendingUp,
  TriangleAlert,
} from 'lucide-react'
import { CategoryIcon, ChartTip, EmptyState, Legend, Meter, PageHeader, slotColor, useData } from '@/components/common'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { ChartContainer, ChartTooltip, type ChartConfig } from '@/components/ui/chart'
import { Checkbox } from '@/components/ui/checkbox'
import { history, recommendation, summarizeMonth } from '@/lib/finance'
import { formatBRL, formatBRLCompact, formatDuration, formatPercent } from '@/lib/format'
import { dayLabel, monthLabel, monthShort } from '@/lib/month'
import { INVEST_GOAL_ID, PRESETS, RESERVE_GOAL_ID } from '@/lib/presets'
import { cn } from '@/lib/utils'
import { useStore, useUI } from '@/store/store'

const historyConfig = {
  income: { label: 'Receitas', color: 'var(--chart-1)' },
  spent: { label: 'Gastos', color: 'var(--chart-2)' },
  saved: { label: 'Guardado', color: 'var(--chart-3)' },
} satisfies ChartConfig

export default function Dashboard() {
  const data = useData()
  const month = useUI((s) => s.month)
  const openTx = useUI((s) => s.openTx)
  const togglePaid = useStore((s) => s.togglePaid)

  const summary = useMemo(() => summarizeMonth(data, month), [data, month])
  const rec = useMemo(() => recommendation(data, month), [data, month])
  const hist = useMemo(() => history(data, month, 6), [data, month])
  const preset = PRESETS.find((p) => p.id === data.settings.presetId)
  const ruleName = preset ? `regra ${preset.short}` : 'sua regra personalizada'
  const futurePct = data.buckets.filter((b) => b.kind === 'futuro').reduce((a, b) => a + b.percent, 0)

  const pending = data.transactions
    .filter((t) => t.month === month && !t.paid)
    .sort((a, b) => a.date.localeCompare(b.date))

  const topCats = summary.categories.slice(0, 7)
  const rest = summary.categories.slice(7)
  const maxCat = Math.max(1, ...summary.categories.map((c) => c.actual))
  const name = data.settings.userName

  if (!summary.hasData && summary.planIncome === 0) {
    return (
      <>
        <PageHeader title={name ? `Olá, ${name}` : 'Visão geral'} description={monthLabel(month)} />
        <Card>
          <EmptyState
            icon="Wallet"
            title="Seu mês ainda está vazio"
            description="Cadastre sua renda e suas contas fixas para ver quanto pode gastar, guardar e investir."
            action={
              <div className="flex flex-wrap justify-center gap-2">
                <Button size="lg" onClick={() => openTx({ defaults: { type: 'receita', description: 'Salário' } })}>
                  <Plus aria-hidden /> Adicionar renda
                </Button>
                <Button size="lg" variant="outline" asChild>
                  <Link to="/contas-fixas">
                    <Repeat aria-hidden /> Configurar contas fixas
                  </Link>
                </Button>
              </div>
            }
          />
        </Card>
      </>
    )
  }

  const reserveDone = rec.reserve.target > 0 && rec.reserve.gap === 0
  const savedRatio = rec.futurePlanned > 0 ? rec.savedThisMonth / rec.futurePlanned : 0

  return (
    <>
      <PageHeader title={name ? `Olá, ${name}` : 'Visão geral'} description={`Resumo de ${monthLabel(month).toLowerCase()}`} />

      {/* Números do mês */}
      <div className="grid gap-4 lg:grid-cols-5">
        <Card className="justify-between lg:col-span-2">
          <CardHeader>
            <CardDescription>Saldo livre do mês</CardDescription>
            <p
              className={cn(
                'mt-1 text-4xl leading-tight font-semibold tracking-tight break-words xl:text-5xl',
                summary.balance < 0 && 'text-danger',
              )}
            >
              {formatBRL(summary.balance)}
            </p>
            <p className="mt-2 text-sm text-muted-foreground">Receitas − gastos − valores guardados</p>
          </CardHeader>
          <CardContent className="grid gap-1.5 text-sm">
            {summary.balance < 0 && (
              <p className="flex items-center gap-2 text-danger">
                <TriangleAlert className="size-4 shrink-0" aria-hidden /> Você saiu mais do que entrou neste mês.
              </p>
            )}
            {summary.unpaid > 0 && (
              <p className="text-muted-foreground">
                <span className="font-medium text-foreground">{formatBRL(summary.unpaid)}</span> em contas ainda a pagar
              </p>
            )}
            {summary.pendingIncome > 0 && (
              <p className="text-muted-foreground">
                <span className="font-medium text-foreground">{formatBRL(summary.pendingIncome)}</span> ainda a receber
              </p>
            )}
          </CardContent>
        </Card>

        <Card className="lg:col-span-3">
          <CardContent className="grid h-full gap-5 sm:grid-cols-3 sm:gap-0 sm:divide-x">
            <Stat
              icon={<ArrowUpRight className="size-4" aria-hidden />}
              label="Receitas"
              value={formatBRL(summary.income)}
              hint={summary.income === 0 && summary.planIncome > 0 ? `Previsto: ${formatBRL(summary.planIncome)}` : 'Tudo o que entrou'}
            />
            <Stat
              icon={<ArrowDownRight className="size-4" aria-hidden />}
              label="Gastos"
              value={formatBRL(summary.spent)}
              hint={summary.income > 0 ? `${formatPercent(summary.spent / summary.income)} da renda` : 'Consumo do mês'}
            />
            <Stat
              icon={<PiggyBank className="size-4" aria-hidden />}
              label="Guardado"
              value={formatBRL(summary.saved)}
              hint={summary.income > 0 ? `Taxa de poupança: ${formatPercent(summary.savingsRate)}` : 'Reserva, investimentos e metas'}
            />
          </CardContent>
        </Card>
      </div>

      {/* Recomendação + distribuição */}
      <div className="mt-4 grid gap-4 lg:grid-cols-5">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Quanto guardar este mês</CardTitle>
            <CardDescription>
              {futurePct}% da renda de {formatBRL(rec.income)}, pela {ruleName}
            </CardDescription>
          </CardHeader>
          <CardContent className="grid gap-5">
            <p className="text-3xl font-semibold tracking-tight">{formatBRL(rec.futurePlanned)}</p>

            <div className="grid gap-3">
              <SplitRow
                icon={<ShieldCheck className="size-4" aria-hidden />}
                label="Reserva de emergência"
                value={formatBRL(rec.toReserve)}
                note={
                  reserveDone ? (
                    <span className="inline-flex items-center gap-1 text-success">
                      <CircleCheck className="size-3.5" aria-hidden /> Reserva completa
                    </span>
                  ) : rec.reserve.target > 0 ? (
                    `Faltam ${formatBRL(rec.reserve.gap)}${rec.monthsToReserve ? ` · cerca de ${formatDuration(rec.monthsToReserve)}` : ''}`
                  ) : (
                    'Registre seus gastos para calcular a meta'
                  )
                }
              />
              <SplitRow
                icon={<TrendingUp className="size-4" aria-hidden />}
                label="Investimentos"
                value={formatBRL(rec.toInvest)}
                note={
                  reserveDone
                    ? 'Com a reserva pronta, todo o valor pode ir para investimentos'
                    : `${100 - data.settings.reservePriority}% enquanto a reserva não fica pronta`
                }
              />
            </div>

            <div className="grid gap-2 rounded-lg bg-muted/60 p-3">
              <div className="flex items-baseline justify-between gap-2 text-sm">
                <span className="text-muted-foreground">Já guardado no mês</span>
                <span className="font-medium tabular">
                  {formatBRL(rec.savedThisMonth)} <span className="text-muted-foreground">/ {formatBRL(rec.futurePlanned)}</span>
                </span>
              </div>
              <Meter value={rec.savedThisMonth} max={rec.futurePlanned} color="var(--chart-3)" label="Progresso do valor a guardar no mês" />
              <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
                {savedRatio >= 1 ? (
                  <>
                    <CircleCheck className="size-3.5 text-success" aria-hidden /> Meta de poupança do mês atingida!
                  </>
                ) : (
                  `Faltam ${formatBRL(rec.remainingToSave)} para cumprir o plano`
                )}
              </p>
            </div>

            <Button
              variant="outline"
              size="lg"
              onClick={() =>
                openTx({
                  defaults: reserveDone
                    ? { type: 'despesa', categoryId: 'cat-investimentos', goalId: INVEST_GOAL_ID, description: 'Aporte em investimentos', amount: rec.toInvest || undefined }
                    : { type: 'despesa', categoryId: 'cat-reserva', goalId: RESERVE_GOAL_ID, description: 'Aporte na reserva', amount: rec.toReserve || undefined },
                })
              }
            >
              <PiggyBank aria-hidden /> Registrar aporte
            </Button>
            <p className="text-xs text-muted-foreground">
              Sugestão educativa com base na regra escolhida — não é recomendação de investimento.
            </p>
          </CardContent>
        </Card>

        <Card className="lg:col-span-3">
          <CardHeader>
            <CardTitle>Planejado × realizado</CardTitle>
            <CardDescription>Como sua renda está sendo distribuída ({ruleName})</CardDescription>
            <CardAction>
              <Button variant="ghost" size="sm" asChild>
                <Link to="/planejamento">
                  Ajustar <ArrowRight aria-hidden />
                </Link>
              </Button>
            </CardAction>
          </CardHeader>
          <CardContent className="grid gap-5">
            {summary.buckets.map(({ bucket, index, planned, actual }) => {
              const saving = bucket.kind === 'futuro'
              const over = !saving && actual > planned
              const done = saving && actual >= planned && planned > 0
              return (
                <div key={bucket.id} className="grid gap-2">
                  <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
                    <span className="flex items-center gap-2 font-medium">
                      <span aria-hidden className="size-2.5 rounded-[3px]" style={{ background: slotColor(index) }} />
                      {bucket.name}
                      <Badge variant="secondary" className="tabular">
                        {bucket.percent}%
                      </Badge>
                    </span>
                    <span className="text-sm tabular">
                      <span className="font-medium">{formatBRL(actual)}</span>
                      <span className="text-muted-foreground"> de {formatBRL(planned)}</span>
                    </span>
                  </div>
                  <Meter value={actual} max={planned} color={slotColor(index)} label={`${bucket.name}: ${formatBRL(actual)} de ${formatBRL(planned)}`} />
                  <p className={cn('flex items-center gap-1.5 text-xs text-muted-foreground', over && 'text-danger', done && 'text-success')}>
                    {over ? (
                      <>
                        <TriangleAlert className="size-3.5" aria-hidden /> {formatBRL(actual - planned)} acima do planejado
                      </>
                    ) : done ? (
                      <>
                        <CircleCheck className="size-3.5" aria-hidden /> Meta do mês atingida
                      </>
                    ) : saving ? (
                      `Faltam ${formatBRL(planned - actual)} para guardar`
                    ) : (
                      `Ainda pode usar ${formatBRL(planned - actual)}`
                    )}
                  </p>
                </div>
              )
            })}
          </CardContent>
        </Card>
      </div>

      {/* Categorias + contas a pagar */}
      <div className="mt-4 grid gap-4 lg:grid-cols-5">
        <Card className="lg:col-span-3">
          <CardHeader>
            <CardTitle>Gastos por categoria</CardTitle>
            <CardDescription>Inclui valores guardados · total de {formatBRL(summary.outflow)}</CardDescription>
            <CardAction>
              <Button variant="ghost" size="sm" asChild>
                <Link to="/lancamentos">
                  Ver todos <ArrowRight aria-hidden />
                </Link>
              </Button>
            </CardAction>
          </CardHeader>
          <CardContent>
            {summary.categories.length === 0 ? (
              <EmptyState icon="Receipt" title="Nenhum gasto registrado" description="Os gastos do mês aparecem aqui, do maior para o menor." />
            ) : (
              <ul className="grid gap-3.5">
                {topCats.map(({ category, actual, count }) => {
                  const overBudget = category.budget ? actual > category.budget : false
                  return (
                    <li key={category.id} className="grid grid-cols-[auto_1fr] items-center gap-3">
                      <CategoryIcon icon={category.icon} />
                      <div className="grid min-w-0 gap-1.5">
                        <div className="flex items-baseline justify-between gap-2 text-sm">
                          <span className="truncate font-medium">{category.name}</span>
                          <span className="shrink-0 font-medium tabular">{formatBRL(actual)}</span>
                        </div>
                        <div className="h-1.5 overflow-hidden rounded-full bg-muted">
                          <div className="h-full rounded-full bg-chart-1" style={{ width: `${(actual / maxCat) * 100}%` }} />
                        </div>
                        <div className="flex justify-between gap-2 text-xs text-muted-foreground">
                          <span>
                            {count} {count === 1 ? 'lançamento' : 'lançamentos'} · {formatPercent(actual / Math.max(1, summary.outflow))}
                          </span>
                          {category.budget ? (
                            <span className={cn('flex items-center gap-1', overBudget && 'text-danger')}>
                              {overBudget && <TriangleAlert className="size-3" aria-hidden />}
                              limite {formatBRL(category.budget)}
                            </span>
                          ) : null}
                        </div>
                      </div>
                    </li>
                  )
                })}
                {rest.length > 0 && (
                  <li className="flex items-center justify-between rounded-lg bg-muted/60 px-3 py-2 text-sm">
                    <span className="text-muted-foreground">Outras {rest.length} categorias</span>
                    <span className="font-medium tabular">{formatBRL(rest.reduce((a, c) => a + c.actual, 0))}</span>
                  </li>
                )}
              </ul>
            )}
          </CardContent>
        </Card>

        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Pendências do mês</CardTitle>
            <CardDescription>Marque quando pagar ou receber</CardDescription>
          </CardHeader>
          <CardContent>
            {pending.length === 0 ? (
              <EmptyState icon="Sparkles" title="Tudo em dia" description="Nenhuma conta pendente neste mês." className="py-6" />
            ) : (
              <ul className="grid gap-1">
                {pending.slice(0, 8).map((t) => {
                  const category = data.categories.find((c) => c.id === t.categoryId)
                  return (
                    <li key={t.id} className="flex items-center gap-3 rounded-lg px-1 py-1.5">
                      <Checkbox
                        checked={t.paid}
                        onCheckedChange={() => togglePaid(t.id)}
                        aria-label={`Marcar "${t.description}" como ${t.type === 'despesa' ? 'pago' : 'recebido'}`}
                      />
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium">{t.description}</p>
                        <p className="text-xs text-muted-foreground">
                          {t.type === 'receita' ? 'A receber' : 'Vence'} {dayLabel(t.date)}
                          {category ? ` · ${category.name}` : ''}
                        </p>
                      </div>
                      <span className={cn('shrink-0 text-sm font-medium whitespace-nowrap tabular', t.type === 'receita' && 'text-success')}>
                        {t.type === 'receita' ? '+' : ''}
                        {formatBRL(t.amount)}
                      </span>
                    </li>
                  )
                })}
                {pending.length > 8 && (
                  <li className="pt-1 text-center text-xs text-muted-foreground">
                    e mais {pending.length - 8} em <Link className="underline" to="/lancamentos">Lançamentos</Link>
                  </li>
                )}
              </ul>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Últimos meses */}
      <Card className="mt-4">
        <CardHeader>
          <CardTitle>Últimos 6 meses</CardTitle>
          <CardDescription>Receitas, gastos e valores guardados por mês</CardDescription>
          <CardAction>
            <Button variant="ghost" size="sm" asChild>
              <Link to="/historico">
                Histórico <ArrowRight aria-hidden />
              </Link>
            </Button>
          </CardAction>
        </CardHeader>
        <CardContent className="grid gap-3">
          <Legend
            items={[
              { label: 'Receitas', color: 'var(--chart-1)' },
              { label: 'Gastos', color: 'var(--chart-2)' },
              { label: 'Guardado', color: 'var(--chart-3)' },
            ]}
          />
          <ChartContainer
            config={historyConfig}
            className="aspect-auto h-64 w-full"
            role="img"
            aria-label={`Gráfico de colunas dos últimos 6 meses. ${hist
              .map((h) => `${monthLabel(h.month)}: receitas ${formatBRL(h.income)}, gastos ${formatBRL(h.spent)}, guardado ${formatBRL(h.saved)}`)
              .join('; ')}`}
          >
            <BarChart data={hist} barGap={2} margin={{ left: 4, right: 4, top: 8 }}>
              <CartesianGrid vertical={false} stroke="var(--chart-grid)" />
              <XAxis dataKey="month" tickLine={false} axisLine={false} tickMargin={8} tickFormatter={monthShort} />
              <YAxis tickLine={false} axisLine={false} width={64} tickFormatter={formatBRLCompact} />
              <ChartTooltip cursor={{ fill: 'var(--muted)', opacity: 0.6 }} content={(p) => <ChartTip {...p} labelFormatter={(l) => monthLabel(String(l))} />} />
              <Bar dataKey="income" name="Receitas" fill="var(--color-income)" radius={[4, 4, 0, 0]} maxBarSize={24} />
              <Bar dataKey="spent" name="Gastos" fill="var(--color-spent)" radius={[4, 4, 0, 0]} maxBarSize={24} />
              <Bar dataKey="saved" name="Guardado" fill="var(--color-saved)" radius={[4, 4, 0, 0]} maxBarSize={24} />
            </BarChart>
          </ChartContainer>
        </CardContent>
      </Card>

      {data.goals.length > 0 && (
        <p className="mt-6 flex items-center justify-center gap-2 text-center text-sm text-muted-foreground">
          <Sparkles className="size-4" aria-hidden /> Acompanhe suas metas e a reserva em{' '}
          <Link to="/metas" className="font-medium text-foreground underline underline-offset-4">
            Metas
          </Link>
        </p>
      )}
    </>
  )
}

function Stat({ icon, label, value, hint }: { icon: React.ReactNode; label: string; value: string; hint: string }) {
  return (
    <div className="flex flex-col justify-center gap-1 sm:px-6 sm:first:pl-0 sm:last:pr-0">
      <span className="flex items-center gap-2 text-sm text-muted-foreground">
        <span className="grid size-7 place-items-center rounded-md bg-muted text-foreground">{icon}</span>
        {label}
      </span>
      <span className="mt-1 text-xl font-semibold tracking-tight xl:text-2xl">{value}</span>
      <span className="text-xs text-muted-foreground">{hint}</span>
    </div>
  )
}

function SplitRow({ icon, label, value, note }: { icon: React.ReactNode; label: string; value: string; note: React.ReactNode }) {
  return (
    <div className="flex items-start gap-3">
      <span className="mt-0.5 grid size-8 shrink-0 place-items-center rounded-lg bg-accent text-accent-foreground">{icon}</span>
      <div className="min-w-0 flex-1">
        <div className="flex items-baseline justify-between gap-2">
          <span className="text-sm font-medium">{label}</span>
          <span className="font-semibold tabular">{value}</span>
        </div>
        <p className="mt-0.5 text-xs text-muted-foreground">{note}</p>
      </div>
    </div>
  )
}
