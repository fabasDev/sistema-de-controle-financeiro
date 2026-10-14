// Casos de legacy-web/src/lib/advisor.test.ts (perfil e chat). Rodar: node --test "src/test/js/**/*.test.mjs"
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { answer, detectIntent } from '../../main/resources/static/js/lib/advisor.js'
import { MAX_SCORE, QUIZ, scoreProfile } from '../../main/resources/static/js/lib/quiz.js'

const all = (i) =>
  Object.fromEntries(
    QUIZ.map((q) => {
      if (i === 'max') return [q.id, q.options.reduce((best, o, j) => (o.points > q.options[best].points ? j : best), 0)]
      if (i === 'min') return [q.id, q.options.reduce((best, o, j) => (o.points < q.options[best].points ? j : best), 0)]
      return [q.id, i]
    }),
  )

test('pontuação máxima = arrojado, mínima = conservador', () => {
  assert.deepEqual(scoreProfile(all('max')), { score: MAX_SCORE, profile: 'arrojado', serverProfile: 'AGGRESSIVE' })
  assert.equal(scoreProfile(all('min')).profile, 'conservador')
})

test('a pontuação máxima é a mesma do servidor (ProfileService.MAX_SCORE = 25)', () => {
  assert.equal(MAX_SCORE, 25)
})

test('prazo menor que 1 ano ou vender tudo na queda limita a conservador', () => {
  assert.equal(scoreProfile({ ...all('max'), prazo: 0 }).profile, 'conservador')
  assert.equal(scoreProfile({ ...all('max'), queda: 0 }).profile, 'conservador')
})

test('prazo de 1 a 3 anos limita a moderado', () => {
  assert.equal(scoreProfile({ ...all('max'), prazo: 1 }).profile, 'moderado')
})

test('respostas intermediárias = moderado', () => {
  const answers = { objetivo: 2, prazo: 3, queda: 2, experiencia: 1, conhecimento: 1, renda: 1, patrimonio: 1, idade: 1 }
  assert.equal(scoreProfile(answers).profile, 'moderado')
})

const intents = [
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
]
for (const [question, intent] of intents) {
  test(`"${question}" → ${intent}`, () => assert.equal(detectIntent(question), intent))
}

/** Contexto como o que /api/assistente devolve, para uma conta nova. */
const ctx = {
  displayName: '',
  reserveMonths: 6,
  maxScore: 25,
  recommendation: { income: 0, futurePlanned: 0, toReserve: 0, toInvest: 0, savedThisMonth: 0, remainingToSave: 0, monthsToReserve: null, reserve: { monthlyCost: 0, target: 0, balance: 0, gap: 0, progress: 0, monthsCovered: 0, basis: '' } },
  income: 0,
  spent: 0,
  spentShare: 0,
  topCategories: [],
  bucketsOverPlan: [],
  profile: null,
  profileScore: null,
  profileExpired: false,
  plan: [],
  goalCount: 0,
}

test('sem perfil, pergunta sobre carteira pede o questionário', () => {
  const blocks = answer('onde investir', ctx)
  assert.ok(blocks.some((b) => b.t === 'actions' && b.actions.some((a) => a.href === '#questionario')))
})

test('pergunta desconhecida recebe resposta de fallback', () => {
  assert.equal(detectIntent('qual a capital da França'), null)
  assert.equal(answer('qual a capital da França', ctx)[0].t, 'p')
})

test('saudação genérica perde para um assunto específico', () => {
  assert.equal(detectIntent('oi, quanto devo investir este mês?'), 'quanto')
  assert.equal(detectIntent('oi'), 'ajuda')
})
