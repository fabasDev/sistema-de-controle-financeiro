import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router'
import { Calculator, ClipboardList, Pencil, RotateCcw, ShieldCheck, Sparkles, TriangleAlert } from 'lucide-react'
import { AdvisorChat } from '@/components/advisor/AdvisorChat'
import { ProfileQuiz } from '@/components/advisor/ProfileQuiz'
import { Meter, NumberInput, PageHeader, useData } from '@/components/common'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  allocationFor,
  ASSET_CLASSES,
  DEFAULT_ALLOCATION,
  MAX_SCORE,
  profileExpired,
  PROFILES,
  splitPlan,
  type AdvisorContext,
  type AssetClassId,
} from '@/lib/advisor'
import { recommendation, summarizeMonth } from '@/lib/finance'
import { formatBRL } from '@/lib/format'
import { monthLabel } from '@/lib/month'
import { cn } from '@/lib/utils'
import { useStore, useUI } from '@/store/store'

const RISK_LABEL = ['', 'Muito baixo', 'Baixo', 'Médio', 'Alto', 'Muito alto']

export default function Invest() {
  const data = useData()
  const month = useUI((s) => s.month)
  const navigate = useNavigate()
  const updateSettings = useStore((s) => s.updateSettings)
  const [quizOpen, setQuizOpen] = useState(false)
  const rec = useMemo(() => recommendation(data, month), [data, month])
  const summary = useMemo(() => summarizeMonth(data, month), [data, month])
  const ctx: AdvisorContext = useMemo(() => ({ data, month, rec, summary }), [data, month, rec, summary])
  const profile = data.profile
  const plan = profile ? splitPlan(rec.toInvest, allocationFor(profile)) : []

  function simulate() {
    updateSettings({ sim: { ...data.settings.sim, monthly: rec.toInvest || rec.futurePlanned } })
    navigate('/simulador')
  }

  return (
    <>
      <PageHeader
        title="Investimentos"
        description={
          <span className="inline-flex flex-wrap items-center gap-2">
            Descubra seu perfil, veja onde investir e tire dúvidas com o assistente.
            <Badge variant="secondary" className="gap-1">
              <Sparkles className="size-3" aria-hidden /> Assistente offline
            </Badge>
          </span>
        }
        actions={
          profile && (
            <Button variant="outline" onClick={() => setQuizOpen(true)}>
              <ClipboardList aria-hidden /> Refazer perfil
            </Button>
          )
        }
      />

      <div className="grid gap-4 lg:grid-cols-5">
        <Card className="gap-0 overflow-hidden py-0 lg:col-span-3">
          <CardHeader className="border-b py-4">
            <CardTitle>Assistente de investimentos</CardTitle>
            <CardDescription>Usa seus números de {monthLabel(month).toLowerCase()} para responder</CardDescription>
          </CardHeader>
          <AdvisorChat ctx={ctx} onQuiz={() => setQuizOpen(true)} className="h-[34rem] lg:h-[38rem]" />
        </Card>

        <div className="grid content-start gap-4 lg:col-span-2">
          <Card>
            <CardHeader>
              <CardTitle>Seu perfil de investidor</CardTitle>
              {profile && (
                <CardDescription>
                  Respondido em {new Date(profile.date).toLocaleDateString('pt-BR')} · {profile.score} de {MAX_SCORE} pontos
                </CardDescription>
              )}
            </CardHeader>
            <CardContent className="grid gap-3">
              {profile ? (
                <>
                  <div className="grid grid-cols-3 gap-1.5">
                    {(['conservador', 'moderado', 'arrojado'] as const).map((id) => (
                      <div
                        key={id}
                        aria-current={id === profile.profile ? 'true' : undefined}
                        className={cn('rounded-lg border px-2 py-2 text-center text-xs', id === profile.profile ? 'border-primary bg-accent font-semibold' : 'text-muted-foreground')}
                      >
                        {PROFILES[id].name}
                      </div>
                    ))}
                  </div>
                  <p className="text-sm">{PROFILES[profile.profile].summary}</p>
                  {profileExpired(profile, month) && (
                    <p className="flex items-center gap-2 text-sm text-warning">
                      <TriangleAlert className="size-4 shrink-0" aria-hidden /> Perfil com mais de 2 anos — refaça o questionário.
                    </p>
                  )}
                </>
              ) : (
                <>
                  <p className="text-sm text-muted-foreground">
                    8 perguntas sobre objetivo, prazo e como você reage a perdas definem se você é conservador, moderado ou arrojado — e qual carteira combina com você.
                  </p>
                  <Button onClick={() => setQuizOpen(true)}>
                    <ClipboardList aria-hidden /> Fazer o questionário (1 min)
                  </Button>
                </>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Plano de {monthLabel(month).split(' ')[0].toLowerCase()}</CardTitle>
              <CardDescription>
                {rec.income > 0 ? `${formatBRL(rec.futurePlanned)} para guardar, pela sua regra de distribuição` : 'Cadastre sua renda para calcular o plano'}
              </CardDescription>
            </CardHeader>
            <CardContent className="grid gap-3 text-sm">
              <div className="flex items-start gap-3 rounded-lg bg-muted/60 p-3">
                <ShieldCheck className="mt-0.5 size-4 shrink-0 text-primary" aria-hidden />
                <div className="min-w-0 flex-1">
                  <div className="flex justify-between gap-2">
                    <span className="font-medium">Reserva de emergência</span>
                    <span className="font-semibold tabular">{formatBRL(rec.toReserve)}</span>
                  </div>
                  <p className="text-xs text-muted-foreground">
                    {rec.reserve.gap === 0 && rec.reserve.target > 0 ? 'Reserva completa' : 'Tesouro Selic ou CDB com liquidez diária'}
                  </p>
                </div>
              </div>
              {profile ? (
                rec.toInvest > 0 ? (
                  <ul className="grid gap-2">
                    {plan.map((i) => (
                      <li key={i.cls.id} className="flex items-baseline justify-between gap-2">
                        <span className="min-w-0 truncate text-muted-foreground">{i.cls.name}</span>
                        <span className="shrink-0 font-medium tabular">{formatBRL(i.amount)}</span>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="text-muted-foreground">Neste mês todo o valor vai para a reserva. Ajuste a prioridade em Metas.</p>
                )
              ) : (
                <p className="text-muted-foreground">Faça o questionário para ver como dividir {formatBRL(rec.toInvest)} entre os investimentos.</p>
              )}
              <Button variant="outline" onClick={simulate} disabled={rec.futurePlanned <= 0}>
                <Calculator aria-hidden /> Simular no longo prazo
              </Button>
            </CardContent>
          </Card>
        </div>
      </div>

      {profile && <AllocationCard />}

      <ProfileQuiz open={quizOpen} onOpenChange={setQuizOpen} />
    </>
  )
}

function AllocationCard() {
  const profile = useStore((s) => s.profile)!
  const setProfile = useStore((s) => s.setProfile)
  const [editing, setEditing] = useState(false)
  const alloc = allocationFor(profile)
  const total = Object.values(alloc).reduce((a, b) => a + b, 0)
  const customized = !!profile.custom && Object.keys(profile.custom).length > 0

  function setPct(id: AssetClassId, value: number) {
    setProfile({ ...profile, custom: { ...profile.custom, [id]: Math.max(0, Math.min(100, Math.round(value))) } })
  }

  return (
    <Card id="carteira" className="mt-4 scroll-mt-24">
      <CardHeader>
        <CardTitle>Carteira sugerida — perfil {PROFILES[profile.profile].name.toLowerCase()}</CardTitle>
        <CardDescription>Como dividir o dinheiro investido (fora a reserva). {customized && 'Você personalizou os percentuais.'}</CardDescription>
        <CardAction className="flex gap-2">
          {customized && (
            <Button variant="ghost" size="sm" onClick={() => setProfile({ ...profile, custom: undefined })}>
              <RotateCcw aria-hidden /> Padrão
            </Button>
          )}
          <Button variant={editing ? 'default' : 'outline'} size="sm" onClick={() => setEditing((e) => !e)} aria-pressed={editing}>
            <Pencil aria-hidden /> {editing ? 'Concluir' : 'Personalizar'}
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent className="grid gap-4">
        {total !== 100 && (
          <p role="status" className="flex items-center gap-2 rounded-lg bg-warning/12 px-3 py-2 text-sm text-warning">
            <TriangleAlert className="size-4 shrink-0" aria-hidden /> Os percentuais somam {total}%. O plano do mês é calculado proporcionalmente.
          </p>
        )}
        <ul className="grid gap-4 md:grid-cols-2">
          {ASSET_CLASSES.map((cls) => {
            const pct = alloc[cls.id]
            const def = DEFAULT_ALLOCATION[profile.profile][cls.id]
            return (
              <li key={cls.id} className={cn('grid gap-2 rounded-xl border p-4', pct === 0 && !editing && 'opacity-60')}>
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="font-medium">{cls.name}</p>
                    <p className="text-xs text-muted-foreground">{cls.examples}</p>
                  </div>
                  {editing ? (
                    <NumberInput value={pct} onValueChange={(v) => setPct(cls.id, v)} aria-label={`Percentual de ${cls.name}`} className="h-9 w-20 text-right" />
                  ) : (
                    <span className="text-xl font-semibold tabular">{pct}%</span>
                  )}
                </div>
                <Meter value={pct} max={100} label={`${cls.name}: ${pct}%`} />
                <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
                  <span className="flex items-center gap-1.5">
                    Risco:
                    <span className="flex gap-0.5" aria-hidden>
                      {[1, 2, 3, 4, 5].map((n) => (
                        <span key={n} className={cn('size-1.5 rounded-full bg-muted', n <= cls.risk && 'bg-foreground/70')} />
                      ))}
                    </span>
                    <span className="font-medium text-foreground">{RISK_LABEL[cls.risk]}</span>
                  </span>
                  {editing && pct !== def && <span>(padrão {def}%)</span>}
                </div>
                <p className="text-sm">{cls.purpose}</p>
                <dl className="grid gap-0.5 text-xs text-muted-foreground">
                  <div>
                    <dt className="inline font-medium text-foreground">Liquidez: </dt>
                    <dd className="inline">{cls.liquidity}</dd>
                  </div>
                  <div>
                    <dt className="inline font-medium text-foreground">Imposto: </dt>
                    <dd className="inline">{cls.tax}</dd>
                  </div>
                </dl>
              </li>
            )
          })}
        </ul>
        <p className="text-xs text-muted-foreground">
          Carteiras de referência para fins educativos, baseadas em práticas comuns de alocação por perfil. Não é recomendação de investimento: avalie custos, emissores e prazos, e consulte um
          profissional habilitado se precisar.
        </p>
      </CardContent>
    </Card>
  )
}
