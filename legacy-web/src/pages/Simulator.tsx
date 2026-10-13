import { useMemo, useState } from 'react'
import { Area, AreaChart, CartesianGrid, Line, ReferenceLine, XAxis, YAxis } from 'recharts'
import { CircleCheck, Info, Target, TriangleAlert } from 'lucide-react'
import { ChartTip, CurrencyInput, Legend, NumberInput, PageHeader, useData } from '@/components/common'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { ChartContainer, ChartTooltip, type ChartConfig } from '@/components/ui/chart'
import { Label } from '@/components/ui/label'
import { Slider } from '@/components/ui/slider'
import { Switch } from '@/components/ui/switch'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { monthlyRate, monthsToTarget, recommendation, requiredMonthly, simulate } from '@/lib/finance'
import { formatBRL, formatBRLCompact, formatDuration, formatNumber } from '@/lib/format'
import { useStore, useUI } from '@/store/store'
import type { SimSettings } from '@/store/types'

const config = {
  total: { label: 'Total acumulado', color: 'var(--chart-1)' },
  invested: { label: 'Total investido', color: 'var(--chart-2)' },
  real: { label: 'Total em valores de hoje', color: 'var(--chart-3)' },
} satisfies ChartConfig

export default function Simulator() {
  const data = useData()
  const month = useUI((s) => s.month)
  const updateSettings = useStore((s) => s.updateSettings)
  const sim = data.settings.sim
  const set = (patch: Partial<SimSettings>) => updateSettings({ sim: { ...sim, ...patch } })
  const [showReal, setShowReal] = useState(true)
  const suggestion = useMemo(() => recommendation(data, month).futurePlanned, [data, month])

  const points = useMemo(() => simulate(sim), [sim])
  const last = points.at(-1)!
  const interest = last.total - last.invested
  const months = points.length - 1
  const reachIn = sim.target > 0 ? monthsToTarget(sim, sim.target) : null
  const needed = sim.target > 0 ? requiredMonthly(sim.initial, sim.annualRate, months, sim.target) : null
  const yearly = points.filter((p) => p.month % 12 === 0 && p.month > 0)
  const tickStep = sim.years > 30 ? 60 : sim.years > 12 ? 24 : 12
  const ticks = Array.from({ length: Math.floor(months / tickStep) + 1 }, (_, i) => i * tickStep)

  return (
    <>
      <PageHeader title="Simulador de investimentos" description="Veja quanto seu dinheiro pode render com juros compostos." />

      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-1">
          <CardHeader>
            <CardTitle>Parâmetros</CardTitle>
            <CardDescription>Os valores ficam salvos para a próxima visita</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-4">
            <Field id="sim-initial" label="Valor inicial">
              <CurrencyInput id="sim-initial" value={sim.initial} onValueChange={(v) => set({ initial: v ?? 0 })} className="h-10" />
            </Field>
            <Field id="sim-monthly" label="Aporte mensal">
              <CurrencyInput id="sim-monthly" value={sim.monthly} onValueChange={(v) => set({ monthly: v ?? 0 })} className="h-10" />
              {suggestion > 0 && Math.abs(suggestion - sim.monthly) > 0.01 && (
                <Button type="button" variant="link" size="sm" className="h-auto justify-start px-0" onClick={() => set({ monthly: suggestion })}>
                  Usar o valor sugerido para guardar: {formatBRL(suggestion)}
                </Button>
              )}
            </Field>
            <Field id="sim-rate" label="Rentabilidade esperada (ao ano)" hint="Use a taxa do seu investimento ou escolha um exemplo">
              <NumberInput id="sim-rate" value={sim.annualRate} onValueChange={(v) => set({ annualRate: Math.min(100, v) })} className="h-10" />
              <div className="flex flex-wrap gap-1.5">
                {[6, 8, 10, 12, 14].map((r) => (
                  <Button key={r} type="button" size="xs" variant={sim.annualRate === r ? 'default' : 'outline'} onClick={() => set({ annualRate: r })} aria-pressed={sim.annualRate === r}>
                    {r}%
                  </Button>
                ))}
              </div>
            </Field>
            <div className="grid gap-2">
              <div className="flex items-baseline justify-between">
                <Label id="sim-years">Período</Label>
                <span className="text-sm font-medium tabular">
                  {sim.years} {sim.years === 1 ? 'ano' : 'anos'}
                </span>
              </div>
              <Slider value={[sim.years]} min={1} max={50} step={1} onValueChange={([v]) => set({ years: v })} aria-labelledby="sim-years" />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <Field id="sim-inflation" label="Inflação (ao ano)">
                <NumberInput id="sim-inflation" value={sim.inflation} onValueChange={(v) => set({ inflation: Math.min(100, v) })} className="h-10" />
              </Field>
              <Field id="sim-increase" label="Aumento anual do aporte">
                <NumberInput id="sim-increase" value={sim.yearlyIncrease} onValueChange={(v) => set({ yearlyIncrease: Math.min(100, v) })} className="h-10" />
              </Field>
            </div>
            <Field id="sim-target" label="Meta de patrimônio (opcional)">
              <CurrencyInput id="sim-target" value={sim.target} onValueChange={(v) => set({ target: v ?? 0 })} className="h-10" placeholder="Ex.: 1.000.000,00" />
            </Field>
          </CardContent>
        </Card>

        <div className="grid content-start gap-4 lg:col-span-2">
          <div className="grid grid-cols-2 gap-3 xl:grid-cols-4">
            <Result label="Total acumulado" value={formatBRL(last.total)} strong />
            <Result label="Total investido" value={formatBRL(last.invested)} />
            <Result label="Juros ganhos" value={formatBRL(interest)} />
            <Result label="Em valores de hoje" value={formatBRL(last.real)} hint={`descontando ${formatNumber(sim.inflation)}% de inflação a.a.`} />
          </div>

          <Card className="gap-2 py-4">
            <CardContent className="grid gap-2 text-sm">
              <p>
                Rendimento mensal estimado ao final: <span className="font-semibold">{formatBRL(last.total * monthlyRate(sim.annualRate))}</span>{' '}
                <span className="text-muted-foreground">(sem mexer no valor acumulado)</span>
              </p>
              {sim.target > 0 && (
                <p className={reachIn !== null && reachIn <= months ? 'flex items-start gap-2 text-success' : 'flex items-start gap-2'}>
                  {reachIn !== null && reachIn <= months ? (
                    <CircleCheck className="mt-0.5 size-4 shrink-0" aria-hidden />
                  ) : (
                    <Target className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-hidden />
                  )}
                  <span className="text-foreground">
                    {reachIn === null
                      ? `Com esses valores, a meta de ${formatBRL(sim.target)} não é alcançada em 100 anos.`
                      : reachIn === 0
                        ? 'Você já tem o valor da meta.'
                        : `Você chega a ${formatBRL(sim.target)} em ${formatDuration(reachIn)}.`}{' '}
                    {needed !== null && needed > 0 && (
                      <>
                        Para chegar em {sim.years} {sim.years === 1 ? 'ano' : 'anos'}, invista{' '}
                        <span className="font-semibold">{formatBRL(needed)}</span>/mês.
                      </>
                    )}
                  </span>
                </p>
              )}
              {sim.annualRate <= sim.inflation && (
                <p className="flex items-start gap-2 text-warning">
                  <TriangleAlert className="mt-0.5 size-4 shrink-0" aria-hidden /> A rentabilidade está abaixo da inflação: o dinheiro perde poder de compra.
                </p>
              )}
            </CardContent>
          </Card>

          <Card>
            <Tabs defaultValue="grafico" className="gap-0">
              <CardHeader className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <CardTitle>Evolução do patrimônio</CardTitle>
                  <CardDescription>Aportes no fim de cada mês, taxa constante</CardDescription>
                </div>
                <TabsList>
                  <TabsTrigger value="grafico">Gráfico</TabsTrigger>
                  <TabsTrigger value="tabela">Tabela</TabsTrigger>
                </TabsList>
              </CardHeader>
              <TabsContent value="grafico">
                <CardContent className="grid gap-3 pt-4">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <Legend
                      items={[
                        { label: 'Total acumulado', color: 'var(--chart-1)' },
                        { label: 'Total investido', color: 'var(--chart-2)' },
                        ...(showReal ? [{ label: 'Em valores de hoje', color: 'var(--chart-3)' }] : []),
                        ...(sim.target > 0 ? [{ label: 'Meta', color: 'var(--muted-foreground)', dashed: true }] : []),
                      ]}
                    />
                    <label className="flex cursor-pointer items-center gap-2 text-xs text-muted-foreground">
                      <Switch checked={showReal} onCheckedChange={setShowReal} /> Valores de hoje
                    </label>
                  </div>
                  <ChartContainer
                    config={config}
                    className="aspect-auto h-72 w-full"
                    role="img"
                    aria-label={`Gráfico de área: em ${sim.years} anos o total acumulado chega a ${formatBRL(last.total)}, dos quais ${formatBRL(last.invested)} foram investidos.`}
                  >
                    <AreaChart data={points} margin={{ left: 4, right: 12, top: 8 }}>
                      <CartesianGrid vertical={false} stroke="var(--chart-grid)" />
                      <XAxis
                        dataKey="month"
                        type="number"
                        domain={[0, months]}
                        ticks={ticks}
                        tickLine={false}
                        axisLine={false}
                        tickMargin={8}
                        tickFormatter={(m: number) => (m === 0 ? 'Hoje' : `${m / 12}a`)}
                      />
                      <YAxis tickLine={false} axisLine={false} width={72} tickFormatter={formatBRLCompact} />
                      <ChartTooltip
                        content={(p) => (
                          <ChartTip
                            {...p}
                            labelFormatter={(l) => (Number(l) === 0 ? 'Hoje' : `Após ${formatDuration(Number(l))}`)}
                          />
                        )}
                      />
                      <Area dataKey="total" name="Total acumulado" type="monotone" stroke="var(--color-total)" strokeWidth={2} fill="var(--color-total)" fillOpacity={0.1} isAnimationActive={false} />
                      <Line dataKey="invested" name="Total investido" type="monotone" stroke="var(--color-invested)" strokeWidth={2} dot={false} isAnimationActive={false} />
                      {showReal && (
                        <Line dataKey="real" name="Em valores de hoje" type="monotone" stroke="var(--color-real)" strokeWidth={2} dot={false} isAnimationActive={false} />
                      )}
                      {sim.target > 0 && <ReferenceLine y={sim.target} stroke="var(--muted-foreground)" strokeDasharray="4 4" />}
                    </AreaChart>
                  </ChartContainer>
                </CardContent>
              </TabsContent>
              <TabsContent value="tabela">
                <CardContent className="pt-4">
                  <div className="max-h-96 overflow-auto rounded-lg border">
                    <Table>
                      <TableHeader className="sticky top-0 bg-card">
                        <TableRow>
                          <TableHead>Ano</TableHead>
                          <TableHead className="text-right">Investido</TableHead>
                          <TableHead className="text-right">Juros</TableHead>
                          <TableHead className="text-right">Total</TableHead>
                          <TableHead className="text-right">Valores de hoje</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody className="tabular">
                        {yearly.map((p) => (
                          <TableRow key={p.month}>
                            <TableCell>{p.month / 12}</TableCell>
                            <TableCell className="text-right">{formatBRL(p.invested)}</TableCell>
                            <TableCell className="text-right">{formatBRL(p.total - p.invested)}</TableCell>
                            <TableCell className="text-right font-medium">{formatBRL(p.total)}</TableCell>
                            <TableCell className="text-right">{formatBRL(p.real)}</TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </div>
                </CardContent>
              </TabsContent>
            </Tabs>
          </Card>

          <p className="flex items-start gap-2 text-xs text-muted-foreground">
            <Info className="mt-0.5 size-3.5 shrink-0" aria-hidden />
            Simulação educativa com taxa constante. Não considera impostos (IR), taxas nem variações de mercado e não é recomendação de investimento.
          </p>
        </div>
      </div>
    </>
  )
}

function Field({ id, label, hint, children }: { id: string; label: string; hint?: string; children: React.ReactNode }) {
  return (
    <div className="grid gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      {hint && <p className="-mt-1 text-xs text-muted-foreground">{hint}</p>}
      {children}
    </div>
  )
}

function Result({ label, value, hint, strong }: { label: string; value: string; hint?: string; strong?: boolean }) {
  return (
    <Card className="gap-1 px-4 py-4">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className={strong ? 'text-xl font-semibold tracking-tight text-primary' : 'text-xl font-semibold tracking-tight'}>{value}</p>
      {hint && <p className="text-[11px] text-muted-foreground">{hint}</p>}
    </Card>
  )
}
