import { describe, expect, it } from 'vitest'
import {
  goalPlan,
  monthsToTarget,
  normalizePercents,
  recommendation,
  requiredMonthly,
  reserveInfo,
  simulate,
  summarizeMonth,
} from '@/lib/finance'
import { formatDuration, parseBRL } from '@/lib/format'
import { addMonths, diffMonths, monthRange } from '@/lib/month'
import { applyPresetTo, createInitialData, RESERVE_GOAL_ID } from '@/lib/presets'
import type { DataState, Transaction } from '@/store/types'

const M = '2026-10'

function tx(partial: Partial<Transaction>): Transaction {
  return {
    id: Math.random().toString(36),
    type: 'despesa',
    month: M,
    date: `${M}-05`,
    description: '',
    amount: 0,
    paid: true,
    ...partial,
  }
}

function withTxs(transactions: Transaction[], patch: Partial<DataState> = {}): DataState {
  return { ...createInitialData(), transactions, ...patch }
}

describe('parseBRL', () => {
  it.each([
    ['1.234,56', 1234.56],
    ['1234,5', 1234.5],
    ['R$ 2.500', 2500],
    ['12.5', 12.5],
    ['1.000.000', 1000000],
    ['', null],
    ['abc', null],
  ])('%s → %s', (input, expected) => {
    expect(parseBRL(input)).toBe(expected)
  })
})

describe('meses', () => {
  it('soma e subtrai atravessando o ano', () => {
    expect(addMonths('2026-11', 3)).toBe('2027-02')
    expect(addMonths('2026-01', -1)).toBe('2025-12')
    expect(diffMonths('2026-10', '2027-04')).toBe(6)
    expect(monthRange('2026-02', 3)).toEqual(['2025-12', '2026-01', '2026-02'])
  })

  it('formata durações', () => {
    expect(formatDuration(27)).toBe('2 anos e 3 meses')
    expect(formatDuration(12)).toBe('1 ano')
    expect(formatDuration(1)).toBe('1 mês')
  })
})

describe('summarizeMonth', () => {
  it('separa consumo de aportes e calcula o plano por pote', () => {
    const state = withTxs([
      tx({ type: 'receita', amount: 5000 }),
      tx({ categoryId: 'cat-moradia', amount: 1500 }),
      tx({ categoryId: 'cat-lazer', amount: 500, paid: false }),
      tx({ categoryId: 'cat-reserva', amount: 800, goalId: RESERVE_GOAL_ID }),
      tx({ categoryId: 'cat-mercado', amount: 999, month: '2026-09', date: '2026-09-01' }),
    ])
    const s = summarizeMonth(state, M)
    expect(s.income).toBe(5000)
    expect(s.spent).toBe(2000)
    expect(s.saved).toBe(800)
    expect(s.balance).toBe(2200)
    expect(s.unpaid).toBe(500)
    expect(s.savingsRate).toBeCloseTo(0.16)
    expect(s.buckets.map((b) => [b.bucket.name, b.planned, b.actual])).toEqual([
      ['Necessidades', 2500, 1500],
      ['Desejos', 1500, 500],
      ['Futuro', 1000, 800],
    ])
  })

  it('usa as receitas fixas como base quando o mês ainda não tem renda', () => {
    const state = withTxs([], {
      recurring: [
        { id: 'r', type: 'receita', description: 'Salário', amount: 4000, day: 5, startMonth: '2026-01', active: true, skipped: [] },
      ],
    })
    expect(summarizeMonth(state, M).planIncome).toBe(4000)
  })
})

describe('reserva e recomendação', () => {
  it('meta = média dos gastos essenciais × meses; prioridade divide o valor do futuro', () => {
    const state = withTxs([
      tx({ type: 'receita', amount: 10000 }),
      tx({ categoryId: 'cat-moradia', amount: 3000 }),
      tx({ categoryId: 'cat-moradia', amount: 1000, month: '2026-09', date: '2026-09-05' }),
      tx({ categoryId: 'cat-lazer', amount: 5000 }),
    ])
    state.goals = state.goals.map((g) => (g.id === RESERVE_GOAL_ID ? { ...g, initial: 2000 } : g))
    const r = reserveInfo(state, M)
    expect(r.monthlyCost).toBe(2000)
    expect(r.target).toBe(12000)
    expect(r.gap).toBe(10000)
    expect(r.monthsCovered).toBe(1)

    const rec = recommendation(state, M)
    expect(rec.futurePlanned).toBe(2000)
    expect(rec.toReserve).toBe(1400)
    expect(rec.toInvest).toBe(600)
    expect(rec.monthsToReserve).toBe(8)
  })

  it('com a reserva completa, todo o futuro vai para investimentos', () => {
    const state = withTxs([tx({ type: 'receita', amount: 10000 }), tx({ categoryId: 'cat-moradia', amount: 1000 })])
    state.goals = state.goals.map((g) => (g.id === RESERVE_GOAL_ID ? { ...g, initial: 50000 } : g))
    const rec = recommendation(state, M)
    expect(rec.toReserve).toBe(0)
    expect(rec.toInvest).toBe(2000)
    expect(rec.monthsToReserve).toBeNull()
  })

  it('aportes não pagos não contam no saldo da meta', () => {
    const state = withTxs([tx({ categoryId: 'cat-reserva', goalId: RESERVE_GOAL_ID, amount: 300, paid: false })])
    expect(reserveInfo(state, M).balance).toBe(0)
  })
})

describe('metas', () => {
  it('calcula quanto guardar por mês até o prazo', () => {
    const plan = goalPlan(
      { id: 'g', name: 'Viagem', icon: 'Plane', kind: 'meta', target: 6000, deadline: '2027-03', initial: 1200 },
      [],
      M,
    )
    expect(plan.monthsLeft).toBe(6)
    expect(plan.monthlyNeeded).toBe(800)
    expect(plan.progress).toBeCloseTo(0.2)
  })
})

describe('regras', () => {
  it('normaliza percentuais para 100', () => {
    const buckets = normalizePercents([
      { id: 'a', name: 'A', percent: 30, kind: 'necessidades' },
      { id: 'b', name: 'B', percent: 30, kind: 'desejos' },
      { id: 'c', name: 'C', percent: 30, kind: 'futuro' },
    ])
    expect(buckets.reduce((a, b) => a + b.percent, 0)).toBe(100)
  })

  it('6 potes direciona categorias reivindicadas e as demais por tipo', () => {
    const { buckets, categories } = applyPresetTo(createInitialData(), '6-potes')
    const bucketOf = (catId: string) => buckets.find((b) => b.id === categories.find((c) => c.id === catId)!.bucketId)!.name
    expect(bucketOf('cat-reserva')).toBe('Longo prazo')
    expect(bucketOf('cat-investimentos')).toBe('Liberdade financeira')
    expect(bucketOf('cat-educacao')).toBe('Educação')
    expect(bucketOf('cat-lazer')).toBe('Diversão')
    expect(bucketOf('cat-mercado')).toBe('Necessidades')
  })
})

describe('simulador', () => {
  it('sem juros, total = inicial + aportes', () => {
    const pts = simulate({ initial: 1000, monthly: 100, annualRate: 0, years: 1, inflation: 0, yearlyIncrease: 0 })
    expect(pts).toHaveLength(13)
    expect(pts.at(-1)!.total).toBe(2200)
    expect(pts.at(-1)!.invested).toBe(2200)
  })

  it('juros compostos anuais equivalentes', () => {
    const pts = simulate({ initial: 1000, monthly: 0, annualRate: 10, years: 2, inflation: 0, yearlyIncrease: 0 })
    expect(pts.at(-1)!.total).toBeCloseTo(1210, 1)
  })

  it('aporte necessário e tempo até a meta são coerentes', () => {
    const monthly = requiredMonthly(0, 10, 120, 100000)
    const months = monthsToTarget({ initial: 0, monthly, annualRate: 10, yearlyIncrease: 0 }, 100000)
    expect(months).toBe(120)
  })
})
