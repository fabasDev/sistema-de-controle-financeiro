import { useState } from 'react'
import { ArrowLeft, ArrowRight, Check, Sparkles, Wallet } from 'lucide-react'
import { CategoryIcon, CurrencyInput, slotColor } from '@/components/common'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import { formatBRL } from '@/lib/format'
import { COMMON_BILLS, createInitialData, PRESETS, type RulePreset } from '@/lib/presets'
import { cn } from '@/lib/utils'
import { createSampleData } from '@/store/sample'
import { useStore } from '@/store/store'

const STEPS = ['Boas-vindas', 'Renda', 'Regra', 'Contas fixas', 'Reserva'] as const
const categoryIcon = Object.fromEntries(createInitialData().categories.map((c) => [c.id, c.icon]))

export function Onboarding() {
  const completeOnboarding = useStore((s) => s.completeOnboarding)
  const replaceData = useStore((s) => s.replaceData)
  const [step, setStep] = useState(0)
  const [userName, setUserName] = useState('')
  const [income, setIncome] = useState<number | undefined>()
  const [incomeDay, setIncomeDay] = useState('5')
  const [presetId, setPresetId] = useState<RulePreset['id']>('50-30-20')
  const [bills, setBills] = useState(() => COMMON_BILLS.map((b) => ({ ...b, amount: undefined as number | undefined })))
  const [reserveBalance, setReserveBalance] = useState<number | undefined>()
  const [reserveMonths, setReserveMonths] = useState('6')
  const preset = PRESETS.find((p) => p.id === presetId)!
  const billsTotal = bills.reduce((a, b) => a + (b.amount ?? 0), 0)

  function finish() {
    completeOnboarding({
      userName: userName.trim(),
      presetId,
      income: { description: 'Salário', amount: income ?? 0, day: Math.min(31, Math.max(1, Number(incomeDay) || 5)) },
      bills: bills.filter((b) => b.amount).map((b) => ({ description: b.description, amount: b.amount!, categoryId: b.categoryId, day: b.day })),
      reserveBalance: reserveBalance ?? 0,
      reserveMonths: Number(reserveMonths),
    })
  }

  function useSample() {
    const sample = createSampleData()
    replaceData({ ...sample, settings: { ...sample.settings, userName: userName.trim() || sample.settings.userName } })
  }

  return (
    <main className="flex min-h-dvh items-center justify-center px-4 py-10">
      <Card className="w-full max-w-xl">
        <CardHeader>
          <ol className="mb-4 flex gap-1.5" aria-label="Etapas">
            {STEPS.map((s, i) => (
              <li key={s} className="flex-1">
                <span className={cn('block h-1.5 rounded-full bg-muted', i <= step && 'bg-primary')} />
                <span className="sr-only">
                  {s}
                  {i === step ? ' (etapa atual)' : i < step ? ' (concluída)' : ''}
                </span>
              </li>
            ))}
          </ol>
          <p className="text-xs font-medium text-muted-foreground">
            Etapa {step + 1} de {STEPS.length}
          </p>
          <CardTitle className="text-xl">
            {['Vamos organizar suas finanças', 'Quanto você recebe por mês?', 'Como quer dividir sua renda?', 'Quais são suas contas fixas?', 'Reserva de emergência'][step]}
          </CardTitle>
          <CardDescription>
            {
              [
                'Leva menos de 2 minutos. Tudo pode ser alterado depois.',
                'Informe sua renda líquida (o que cai na conta). Ela vira uma receita fixa mensal.',
                'Escolha uma regra de distribuição. Você pode personalizar os percentuais depois.',
                'Preencha só as que você tem. Elas aparecerão automaticamente todo mês como “a pagar”.',
                'O valor guardado para imprevistos. O app calcula a meta com base nos seus gastos.',
              ][step]
            }
          </CardDescription>
        </CardHeader>

        <CardContent className="grid gap-4">
          {step === 0 && (
            <>
              <div className="grid gap-1.5">
                <Label htmlFor="ob-name">Como podemos te chamar? (opcional)</Label>
                <Input id="ob-name" autoFocus autoComplete="given-name" maxLength={40} value={userName} onChange={(e) => setUserName(e.target.value)} className="h-11" />
              </div>
              <div className="grid gap-2 sm:grid-cols-2">
                <button
                  type="button"
                  onClick={() => setStep(1)}
                  className="flex flex-col items-start gap-2 rounded-xl border p-4 text-left transition-colors hover:border-primary hover:bg-accent/50"
                >
                  <Wallet className="size-5 text-primary" aria-hidden />
                  <span className="font-medium">Configurar com meus dados</span>
                  <span className="text-xs text-muted-foreground">Renda, regra, contas fixas e reserva.</span>
                </button>
                <button
                  type="button"
                  onClick={useSample}
                  className="flex flex-col items-start gap-2 rounded-xl border p-4 text-left transition-colors hover:border-primary hover:bg-accent/50"
                >
                  <Sparkles className="size-5 text-primary" aria-hidden />
                  <span className="font-medium">Explorar com dados de exemplo</span>
                  <span className="text-xs text-muted-foreground">6 meses fictícios. Dá para apagar depois em Ajustes.</span>
                </button>
              </div>
            </>
          )}

          {step === 1 && (
            <div className="grid grid-cols-[1fr_7rem] gap-3">
              <div className="grid gap-1.5">
                <Label htmlFor="ob-income">Renda mensal líquida</Label>
                <CurrencyInput id="ob-income" autoFocus value={income} onValueChange={setIncome} className="h-11 text-lg" />
              </div>
              <div className="grid gap-1.5">
                <Label htmlFor="ob-day">Dia que cai</Label>
                <Input id="ob-day" type="number" inputMode="numeric" min={1} max={31} value={incomeDay} onChange={(e) => setIncomeDay(e.target.value)} className="h-11" />
              </div>
              <p className="col-span-2 text-xs text-muted-foreground">Tem outras rendas? Adicione depois em Contas fixas ou Lançamentos.</p>
            </div>
          )}

          {step === 2 && (
            <RadioGroup value={presetId} onValueChange={(v) => setPresetId(v as RulePreset['id'])} className="grid gap-2" aria-label="Regra de distribuição">
              {PRESETS.map((p) => (
                <Label
                  key={p.id}
                  htmlFor={`ob-${p.id}`}
                  className={cn('flex cursor-pointer items-start gap-3 rounded-xl border p-3 font-normal', presetId === p.id && 'border-primary bg-accent/50')}
                >
                  <RadioGroupItem id={`ob-${p.id}`} value={p.id} className="mt-1" />
                  <span className="grid flex-1 gap-1.5">
                    <span className="font-medium">{p.name}</span>
                    <span className="text-xs text-muted-foreground">{p.description}</span>
                    <span className="flex h-2 overflow-hidden rounded-full" aria-hidden>
                      {p.buckets.map((b, i) => (
                        <span key={b.name} style={{ width: `${b.percent}%`, background: slotColor(i) }} className="border-r-2 border-card last:border-r-0" />
                      ))}
                    </span>
                    {income ? (
                      <span className="text-xs text-muted-foreground">
                        {p.buckets.map((b) => `${b.name}: ${formatBRL((income * b.percent) / 100)}`).join(' · ')}
                      </span>
                    ) : null}
                  </span>
                </Label>
              ))}
            </RadioGroup>
          )}

          {step === 3 && (
            <div className="grid gap-2">
              {bills.map((b, i) => (
                <div key={b.description} className="grid grid-cols-[auto_1fr_8.5rem] items-center gap-3">
                  <CategoryIcon icon={categoryIcon[b.categoryId]} size="sm" />
                  <Label htmlFor={`ob-bill-${i}`} className="font-normal">
                    {b.description}
                  </Label>
                  <CurrencyInput
                    id={`ob-bill-${i}`}
                    value={b.amount}
                    onValueChange={(v) => setBills((all) => all.map((x, j) => (j === i ? { ...x, amount: v } : x)))}
                    className="h-9"
                  />
                </div>
              ))}
              <p className="mt-1 text-right text-sm">
                Total: <span className="font-semibold">{formatBRL(billsTotal)}</span>
                {income ? <span className="text-muted-foreground"> · {Math.round((billsTotal / income) * 100)}% da renda</span> : null}
              </p>
            </div>
          )}

          {step === 4 && (
            <div className="grid gap-4">
              <div className="grid gap-1.5">
                <Label htmlFor="ob-reserve">Quanto você já tem guardado para emergências?</Label>
                <CurrencyInput id="ob-reserve" autoFocus value={reserveBalance} onValueChange={setReserveBalance} className="h-11" />
              </div>
              <div className="grid gap-1.5">
                <Label id="ob-months">Quantos meses de custo você quer ter guardado?</Label>
                <ToggleGroup type="single" variant="outline" value={reserveMonths} onValueChange={(v) => v && setReserveMonths(v)} aria-labelledby="ob-months" className="w-full">
                  {['3', '6', '9', '12'].map((m) => (
                    <ToggleGroupItem key={m} value={m} className="h-10 flex-1">
                      {m} meses
                    </ToggleGroupItem>
                  ))}
                </ToggleGroup>
                <p className="text-xs text-muted-foreground">Recomendação comum: 6 meses para CLT, 12 para autônomos ou renda variável.</p>
              </div>
              <div className="rounded-xl bg-muted/60 p-3 text-sm">
                Regra escolhida: <span className="font-medium">{preset.name}</span>
                {income ? (
                  <>
                    {' '}— separe{' '}
                    <span className="font-semibold">
                      {formatBRL((income * preset.buckets.filter((b) => b.kind === 'futuro').reduce((a, b) => a + b.percent, 0)) / 100)}
                    </span>{' '}
                    por mês para reserva e investimentos.
                  </>
                ) : null}
              </div>
            </div>
          )}
        </CardContent>

        {step > 0 && (
          <CardFooter className="justify-between gap-2">
            <Button variant="ghost" size="lg" onClick={() => setStep((s) => s - 1)}>
              <ArrowLeft aria-hidden /> Voltar
            </Button>
            {step < STEPS.length - 1 ? (
              <Button size="lg" onClick={() => setStep((s) => s + 1)}>
                {step === 1 && !income ? 'Pular' : 'Continuar'} <ArrowRight aria-hidden />
              </Button>
            ) : (
              <Button size="lg" onClick={finish}>
                <Check aria-hidden /> Concluir
              </Button>
            )}
          </CardFooter>
        )}
      </Card>
    </main>
  )
}
