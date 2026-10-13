import { describe, expect, it } from 'vitest'
import {
  answer,
  ASSET_CLASSES,
  DEFAULT_ALLOCATION,
  detectIntent,
  MAX_SCORE,
  QUIZ,
  scoreProfile,
  splitPlan,
  type AdvisorContext,
} from '@/lib/advisor'
import { recommendation, summarizeMonth } from '@/lib/finance'
import { createInitialData } from '@/lib/presets'

const all = (i: number | 'max' | 'min') =>
  Object.fromEntries(
    QUIZ.map((q) => {
      if (i === 'max') return [q.id, q.options.reduce((best, o, j) => (o.points > q.options[best].points ? j : best), 0)]
      if (i === 'min') return [q.id, q.options.reduce((best, o, j) => (o.points < q.options[best].points ? j : best), 0)]
      return [q.id, i]
    }),
  )

describe('perfil de investidor', () => {
  it('pontuação máxima = arrojado, mínima = conservador', () => {
    expect(scoreProfile(all('max'))).toEqual({ score: MAX_SCORE, profile: 'arrojado' })
    expect(scoreProfile(all('min')).profile).toBe('conservador')
  })

  it('prazo menor que 1 ano ou vender tudo na queda limita a conservador', () => {
    expect(scoreProfile({ ...all('max'), prazo: 0 }).profile).toBe('conservador')
    expect(scoreProfile({ ...all('max'), queda: 0 }).profile).toBe('conservador')
  })

  it('prazo de 1 a 3 anos limita a moderado', () => {
    expect(scoreProfile({ ...all('max'), prazo: 1 }).profile).toBe('moderado')
  })

  it('respostas intermediárias = moderado', () => {
    const answers = { objetivo: 2, prazo: 3, queda: 2, experiencia: 1, conhecimento: 1, renda: 1, patrimonio: 1, idade: 1 }
    expect(scoreProfile(answers).profile).toBe('moderado')
  })
})

describe('carteiras', () => {
  it('cada perfil soma 100% e cobre todas as classes', () => {
    for (const alloc of Object.values(DEFAULT_ALLOCATION)) {
      expect(Object.values(alloc).reduce((a, b) => a + b, 0)).toBe(100)
      expect(Object.keys(alloc).sort()).toEqual(ASSET_CLASSES.map((c) => c.id).sort())
    }
  })

  it('divide o valor sem perder centavos', () => {
    const items = splitPlan(1000.01, DEFAULT_ALLOCATION.moderado)
    expect(items.reduce((a, i) => a + i.amount, 0)).toBeCloseTo(1000.01, 2)
    expect(items[0].percent).toBeGreaterThanOrEqual(items.at(-1)!.percent)
  })

  it('ignora classes com 0%', () => {
    expect(splitPlan(500, DEFAULT_ALLOCATION.conservador).map((i) => i.cls.id)).not.toContain('acoes')
  })
})

describe('chat', () => {
  it.each([
    ['Qual é o meu perfil?', 'perfil'],
    ['quanto devo investir esse mês', 'quanto'],
    ['Onde investir meu dinheiro?', 'onde'],
    ['onde deixo a reserva de emergência', 'reserva'],
    ['O que é Tesouro Selic?', 'tesouro'],
    ['vale a pena LCI ou CDB', 'cdb'],
    ['poupança compensa?', 'poupanca'],
    ['como funciona o imposto de renda', 'ir'],
    ['devo comprar bitcoin', 'cripto'],
    ['fundos imobiliários pagam bem?', 'fiis'],
    ['estou com dívida no cartão', 'dividas'],
    ['quanto vou ter para me aposentar', 'simular'],
  ])('"%s" → %s', (q, intent) => {
    expect(detectIntent(q)).toBe(intent)
  })

  it('sem perfil, pergunta sobre carteira pede o questionário', () => {
    const data = createInitialData()
    const ctx: AdvisorContext = { data, month: '2026-10', rec: recommendation(data, '2026-10'), summary: summarizeMonth(data, '2026-10') }
    const blocks = answer('onde investir', ctx)
    expect(blocks.some((b) => b.t === 'actions' && b.actions.some((a) => a.action.type === 'quiz'))).toBe(true)
  })

  it('pergunta desconhecida recebe resposta de fallback', () => {
    const data = createInitialData()
    const ctx: AdvisorContext = { data, month: '2026-10', rec: recommendation(data, '2026-10'), summary: summarizeMonth(data, '2026-10') }
    expect(detectIntent('qual a capital da França')).toBeNull()
    expect(answer('qual a capital da França', ctx)[0].t).toBe('p')
  })
})
