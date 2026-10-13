import { useMemo, useState } from 'react'
import { Bar, BarChart, CartesianGrid, Line, LineChart, XAxis, YAxis } from 'recharts'
import { CategoryIcon, ChartTip, Legend, PageHeader, useData } from '@/components/common'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { ChartContainer, ChartTooltip, type ChartConfig } from '@/components/ui/chart'
import { Table, TableBody, TableCell, TableFooter, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import { categoryHistory, history } from '@/lib/finance'
import { formatBRL, formatBRLCompact, formatPercent } from '@/lib/format'
import { monthLabel, monthShort } from '@/lib/month'
import { cn } from '@/lib/utils'
import { useUI } from '@/store/store'

const flowConfig = {
  income: { label: 'Receitas', color: 'var(--chart-1)' },
  spent: { label: 'Gastos', color: 'var(--chart-2)' },
  saved: { label: 'Guardado', color: 'var(--chart-3)' },
} satisfies ChartConfig

const rateConfig = { rate: { label: 'Taxa de poupança', color: 'var(--chart-1)' } } satisfies ChartConfig

export default function History() {
  const data = useData()
  const month = useUI((s) => s.month)
  const [range, setRange] = useState('6')
  const count = Number(range)
  const rows = useMemo(() => history(data, month, count), [data, month, count])
  const cats = useMemo(() => categoryHistory(data, month, count), [data, month, count])
  const withData = rows.filter((r) => r.income > 0 || r.spent > 0)
  const avg = (k: 'income' | 'spent' | 'saved') => (withData.length ? withData.reduce((a, r) => a + r[k], 0) / withData.length : 0)
  const rateData = rows.map((r) => ({ month: r.month, rate: r.income > 0 ? Math.round(r.savingsRate * 1000) / 10 : null }))
  const sum = (k: 'income' | 'spent' | 'saved' | 'balance') => rows.reduce((a, r) => a + r[k], 0)

  return (
    <>
      <PageHeader
        title="Histórico"
        description={`Até ${monthLabel(month).toLowerCase()}`}
        actions={
          <ToggleGroup type="single" variant="outline" value={range} onValueChange={(v) => v && setRange(v)} aria-label="Período">
            <ToggleGroupItem value="6" className="h-9 px-3">6 meses</ToggleGroupItem>
            <ToggleGroupItem value="12" className="h-9 px-3">12 meses</ToggleGroupItem>
            <ToggleGroupItem value="24" className="h-9 px-3">24 meses</ToggleGroupItem>
          </ToggleGroup>
        }
      />

      <div className="mb-4 grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Avg label="Receita média" value={avg('income')} />
        <Avg label="Gasto médio" value={avg('spent')} />
        <Avg label="Guardado em média" value={avg('saved')} />
        <Avg label="Taxa de poupança média" text={formatPercent(avg('income') > 0 ? avg('saved') / avg('income') : 0)} />
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Entradas e saídas</CardTitle>
            <CardDescription>Receitas, gastos (consumo) e valores guardados por mês</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-3">
            <Legend
              items={[
                { label: 'Receitas', color: 'var(--chart-1)' },
                { label: 'Gastos', color: 'var(--chart-2)' },
                { label: 'Guardado', color: 'var(--chart-3)' },
              ]}
            />
            <ChartContainer config={flowConfig} className="aspect-auto h-72 w-full" role="img" aria-label="Gráfico de colunas por mês. Os valores estão na tabela abaixo.">
              <BarChart data={rows} barGap={2} margin={{ left: 4, right: 4, top: 8 }}>
                <CartesianGrid vertical={false} stroke="var(--chart-grid)" />
                <XAxis dataKey="month" tickLine={false} axisLine={false} tickMargin={8} tickFormatter={monthShort} minTickGap={8} />
                <YAxis tickLine={false} axisLine={false} width={64} tickFormatter={formatBRLCompact} />
                <ChartTooltip cursor={{ fill: 'var(--muted)', opacity: 0.6 }} content={(p) => <ChartTip {...p} labelFormatter={(l) => monthLabel(String(l))} />} />
                <Bar dataKey="income" name="Receitas" fill="var(--color-income)" radius={[4, 4, 0, 0]} maxBarSize={20} />
                <Bar dataKey="spent" name="Gastos" fill="var(--color-spent)" radius={[4, 4, 0, 0]} maxBarSize={20} />
                <Bar dataKey="saved" name="Guardado" fill="var(--color-saved)" radius={[4, 4, 0, 0]} maxBarSize={20} />
              </BarChart>
            </ChartContainer>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Taxa de poupança</CardTitle>
            <CardDescription>% da renda guardada a cada mês</CardDescription>
          </CardHeader>
          <CardContent>
            <ChartContainer config={rateConfig} className="aspect-auto h-72 w-full" role="img" aria-label="Gráfico de linha da taxa de poupança mensal.">
              <LineChart data={rateData} margin={{ left: 4, right: 12, top: 8 }}>
                <CartesianGrid vertical={false} stroke="var(--chart-grid)" />
                <XAxis dataKey="month" tickLine={false} axisLine={false} tickMargin={8} tickFormatter={monthShort} minTickGap={12} />
                <YAxis tickLine={false} axisLine={false} width={40} tickFormatter={(v: number) => `${v}%`} domain={[0, 'auto']} />
                <ChartTooltip
                  content={(p) => <ChartTip {...p} labelFormatter={(l) => monthLabel(String(l))} valueFormatter={(v) => `${v.toLocaleString('pt-BR')}%`} />}
                />
                <Line
                  dataKey="rate"
                  name="Taxa de poupança"
                  type="monotone"
                  stroke="var(--color-rate)"
                  strokeWidth={2}
                  dot={{ r: 4, fill: 'var(--color-rate)', stroke: 'var(--card)', strokeWidth: 2 }}
                  activeDot={{ r: 6, stroke: 'var(--card)', strokeWidth: 2 }}
                  connectNulls
                  isAnimationActive={false}
                />
              </LineChart>
            </ChartContainer>
          </CardContent>
        </Card>
      </div>

      <Card className="mt-4">
        <CardHeader>
          <CardTitle>Resumo mensal</CardTitle>
          <CardDescription>Os mesmos números dos gráficos, em tabela</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="overflow-x-auto rounded-lg border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Mês</TableHead>
                  <TableHead className="text-right">Receitas</TableHead>
                  <TableHead className="text-right">Gastos</TableHead>
                  <TableHead className="text-right">Guardado</TableHead>
                  <TableHead className="text-right">Saldo livre</TableHead>
                  <TableHead className="text-right">Poupança</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody className="tabular">
                {[...rows].reverse().map((r) => (
                  <TableRow key={r.month}>
                    <TableCell className="font-medium">{monthLabel(r.month)}</TableCell>
                    <TableCell className="text-right">{formatBRL(r.income)}</TableCell>
                    <TableCell className="text-right">{formatBRL(r.spent)}</TableCell>
                    <TableCell className="text-right">{formatBRL(r.saved)}</TableCell>
                    <TableCell className={cn('text-right font-medium', r.balance < 0 && 'text-danger')}>{formatBRL(r.balance)}</TableCell>
                    <TableCell className="text-right">{r.income > 0 ? formatPercent(r.savingsRate) : '—'}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
              <TableFooter className="tabular">
                <TableRow>
                  <TableCell>Total</TableCell>
                  <TableCell className="text-right">{formatBRL(sum('income'))}</TableCell>
                  <TableCell className="text-right">{formatBRL(sum('spent'))}</TableCell>
                  <TableCell className="text-right">{formatBRL(sum('saved'))}</TableCell>
                  <TableCell className="text-right">{formatBRL(sum('balance'))}</TableCell>
                  <TableCell className="text-right">{sum('income') > 0 ? formatPercent(sum('saved') / sum('income')) : '—'}</TableCell>
                </TableRow>
              </TableFooter>
            </Table>
          </div>
        </CardContent>
      </Card>

      <Card className="mt-4">
        <CardHeader>
          <CardTitle>Gastos por categoria</CardTitle>
          <CardDescription>Quanto foi para cada categoria em cada mês (tons mais fortes = valores maiores)</CardDescription>
          <CardAction className="text-xs text-muted-foreground">{cats.rows.length} categorias</CardAction>
        </CardHeader>
        <CardContent>
          {cats.rows.length === 0 ? (
            <p className="py-6 text-center text-sm text-muted-foreground">Sem gastos no período.</p>
          ) : (
            <div className="overflow-x-auto rounded-lg border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="sticky left-0 z-10 bg-card">Categoria</TableHead>
                    {cats.months.map((m) => (
                      <TableHead key={m} className="text-right">
                        {monthShort(m)}
                      </TableHead>
                    ))}
                    <TableHead className="text-right">Média</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody className="tabular">
                  {cats.rows.map((row) => {
                    const max = Math.max(...row.values)
                    return (
                      <TableRow key={row.category.id}>
                        <TableCell className="sticky left-0 z-10 bg-card">
                          <span className="flex items-center gap-2">
                            <CategoryIcon icon={row.category.icon} size="sm" />
                            <span className="max-w-40 truncate">{row.category.name}</span>
                          </span>
                        </TableCell>
                        {row.values.map((v, i) => (
                          <TableCell
                            key={cats.months[i]}
                            className="text-right"
                            style={v > 0 ? { background: `color-mix(in oklab, var(--chart-1) ${Math.round((v / max) * 22)}%, transparent)` } : undefined}
                          >
                            {v > 0 ? formatBRL(v) : <span className="text-muted-foreground">—</span>}
                          </TableCell>
                        ))}
                        <TableCell className="text-right font-medium">{formatBRL(row.total / cats.months.length)}</TableCell>
                      </TableRow>
                    )
                  })}
                </TableBody>
              </Table>
            </div>
          )}
        </CardContent>
      </Card>
    </>
  )
}

function Avg({ label, value, text }: { label: string; value?: number; text?: string }) {
  return (
    <Card className="gap-1 px-4 py-4">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="text-xl font-semibold tracking-tight">{text ?? formatBRL(value ?? 0)}</p>
    </Card>
  )
}
