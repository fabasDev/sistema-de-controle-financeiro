/**
 * Assistente de investimentos 100% local (regras + textos educativos).
 * Nada aqui faz requisições: as respostas são montadas com os dados do próprio usuário.
 */
import type { MonthSummary, Recommendation } from '@/lib/finance'
import { formatBRL, formatDuration, formatPercent } from '@/lib/format'
import { diffMonths, type MonthKey } from '@/lib/month'
import type { DataState, InvestorProfile, ProfileId } from '@/store/types'

// ---------- Questionário de perfil (suitability) ----------

export interface QuizQuestion {
  id: string
  question: string
  help?: string
  options: { label: string; points: number }[]
}

export const QUIZ: QuizQuestion[] = [
  {
    id: 'objetivo',
    question: 'Qual é o seu principal objetivo ao investir?',
    options: [
      { label: 'Proteger o que já tenho, sem correr risco de perder', points: 0 },
      { label: 'Render mais que a poupança, com pouco risco', points: 1 },
      { label: 'Equilibrar segurança e crescimento', points: 2 },
      { label: 'Fazer o patrimônio crescer bastante no longo prazo', points: 4 },
    ],
  },
  {
    id: 'prazo',
    question: 'Por quanto tempo você pretende deixar o dinheiro investido?',
    help: 'Pense no dinheiro que vai além da reserva de emergência.',
    options: [
      { label: 'Menos de 1 ano', points: 0 },
      { label: 'De 1 a 3 anos', points: 1 },
      { label: 'De 3 a 5 anos', points: 2 },
      { label: 'De 5 a 10 anos', points: 3 },
      { label: 'Mais de 10 anos', points: 4 },
    ],
  },
  {
    id: 'queda',
    question: 'Se seus investimentos caíssem 15% em um mês, o que você faria?',
    options: [
      { label: 'Venderia tudo para não perder mais', points: 0 },
      { label: 'Venderia uma parte', points: 1 },
      { label: 'Manteria e esperaria recuperar', points: 3 },
      { label: 'Aproveitaria para investir mais', points: 4 },
    ],
  },
  {
    id: 'experiencia',
    question: 'Em quais investimentos você já aplicou?',
    options: [
      { label: 'Só poupança, ou nunca investi', points: 0 },
      { label: 'Tesouro Direto, CDB ou LCI/LCA', points: 1 },
      { label: 'Fundos de investimento ou fundos imobiliários', points: 2 },
      { label: 'Ações, ETFs ou investimentos no exterior', points: 3 },
    ],
  },
  {
    id: 'conhecimento',
    question: 'Como você avalia seu conhecimento sobre investimentos?',
    options: [
      { label: 'Iniciante: estou começando a aprender', points: 0 },
      { label: 'Intermediário: entendo risco, liquidez e rentabilidade', points: 2 },
      { label: 'Avançado: acompanho o mercado e sei analisar ativos', points: 3 },
    ],
  },
  {
    id: 'renda',
    question: 'Como é a sua renda?',
    options: [
      { label: 'Instável, ou estou sem renda fixa', points: 0 },
      { label: 'Razoavelmente estável (ex.: autônomo com clientes fixos)', points: 1 },
      { label: 'Muito estável (ex.: CLT, servidor público, aposentadoria)', points: 2 },
    ],
  },
  {
    id: 'patrimonio',
    question: 'Que parte do seu patrimônio pode ficar investida sem você precisar mexer?',
    options: [
      { label: 'Menos de 25%', points: 0 },
      { label: 'Entre 25% e 50%', points: 1 },
      { label: 'Entre 50% e 75%', points: 2 },
      { label: 'Mais de 75%', points: 3 },
    ],
  },
  {
    id: 'idade',
    question: 'Qual é a sua faixa de idade?',
    help: 'Quanto mais tempo pela frente, mais tempo para recuperar quedas.',
    options: [
      { label: 'Até 30 anos', points: 2 },
      { label: 'De 31 a 45 anos', points: 2 },
      { label: 'De 46 a 60 anos', points: 1 },
      { label: 'Mais de 60 anos', points: 0 },
    ],
  },
]

export const PROFILES: Record<ProfileId, { name: string; summary: string; detail: string }> = {
  conservador: {
    name: 'Conservador',
    summary: 'Prioriza segurança e liquidez. Aceita render menos para não ver o saldo cair.',
    detail: 'A maior parte vai para renda fixa de baixo risco. Renda variável fica de fora ou em fatia mínima.',
  },
  moderado: {
    name: 'Moderado',
    summary: 'Busca equilíbrio: aceita oscilações moderadas em troca de mais rentabilidade no médio/longo prazo.',
    detail: 'A base é renda fixa, com uma parte em fundos imobiliários, ações e exterior para crescer.',
  },
  arrojado: {
    name: 'Arrojado',
    summary: 'Foca em crescimento no longo prazo e tolera quedas fortes no caminho.',
    detail: 'Maior fatia em renda variável e exterior, mantendo renda fixa para estabilidade e oportunidades.',
  },
}

/** Calcula o perfil. Algumas respostas limitam o perfil, independentemente da pontuação. */
export function scoreProfile(answers: Record<string, number>): { score: number; profile: ProfileId } {
  const points = (id: string) => {
    const q = QUIZ.find((x) => x.id === id)!
    return q.options[answers[id]]?.points ?? 0
  }
  const score = QUIZ.reduce((a, q) => a + points(q.id), 0)
  let profile: ProfileId = score <= 9 ? 'conservador' : score <= 17 ? 'moderado' : 'arrojado'
  // Prazo curto ou intolerância a quedas: renda variável não é adequada.
  if (answers.prazo === 0 || answers.queda === 0) profile = 'conservador'
  else if (answers.prazo === 1 && profile === 'arrojado') profile = 'moderado'
  return { score, profile }
}

export const MAX_SCORE = QUIZ.reduce((a, q) => a + Math.max(...q.options.map((o) => o.points)), 0)

// ---------- Classes de ativos e carteiras ----------

export type AssetClassId = 'posfixado' | 'inflacao' | 'prefixado' | 'isentos' | 'fiis' | 'acoes' | 'exterior'

export interface AssetClass {
  id: AssetClassId
  name: string
  examples: string
  /** 1 (muito baixo) a 5 (muito alto) */
  risk: 1 | 2 | 3 | 4 | 5
  liquidity: string
  tax: string
  purpose: string
}

export const ASSET_CLASSES: AssetClass[] = [
  {
    id: 'posfixado',
    name: 'Renda fixa pós-fixada',
    examples: 'Tesouro Selic, CDB e LC com liquidez diária que paguem perto de 100% do CDI',
    risk: 1,
    liquidity: 'Diária na maioria dos produtos',
    tax: 'IR regressivo: 22,5% (até 6 meses) até 15% (mais de 2 anos)',
    purpose: 'Base segura da carteira e lugar ideal para a reserva de emergência. Acompanha os juros básicos.',
  },
  {
    id: 'inflacao',
    name: 'Renda fixa atrelada à inflação',
    examples: 'Tesouro IPCA+, CDBs e debêntures IPCA+',
    risk: 2,
    liquidity: 'Pode resgatar antes, mas o preço oscila até o vencimento',
    tax: 'IR regressivo (debêntures incentivadas podem ser isentas)',
    purpose: 'Proteger o poder de compra no longo prazo — ótimo para aposentadoria e metas distantes.',
  },
  {
    id: 'prefixado',
    name: 'Renda fixa prefixada',
    examples: 'Tesouro Prefixado, CDB prefixado',
    risk: 2,
    liquidity: 'Pode resgatar antes, mas o preço oscila até o vencimento',
    tax: 'IR regressivo',
    purpose: 'Travar uma taxa conhecida. Vale mais a pena quando a expectativa é de queda dos juros.',
  },
  {
    id: 'isentos',
    name: 'Renda fixa isenta',
    examples: 'LCI e LCA (com FGC); CRI e CRA (sem FGC, mais risco)',
    risk: 2,
    liquidity: 'Geralmente com carência de alguns meses',
    tax: 'Isentas de IR para pessoa física (regra vigente até 2025 — confirme a atual)',
    purpose: 'Renda fixa sem IR para prazos médios. Compare sempre com o CDB já descontado o imposto.',
  },
  {
    id: 'fiis',
    name: 'Fundos imobiliários (FIIs)',
    examples: 'Fundos de tijolo (shoppings, galpões, lajes) e de papel (recebíveis)',
    risk: 3,
    liquidity: 'Negociados na bolsa (dinheiro em 2 dias úteis), com preço variável',
    tax: 'Rendimentos mensais em geral isentos para PF; lucro na venda paga 20%',
    purpose: 'Renda mensal e exposição a imóveis sem precisar comprar um.',
  },
  {
    id: 'acoes',
    name: 'Ações e ETFs brasileiros',
    examples: 'ETFs que replicam o Ibovespa ou ações de empresas sólidas',
    risk: 4,
    liquidity: 'Negociados na bolsa (2 dias úteis), com preço bem variável',
    tax: '15% sobre o lucro; vendas de ações de até R$ 20 mil/mês são isentas (não vale para ETFs)',
    purpose: 'Crescimento no longo prazo e participação nos lucros das empresas (dividendos).',
  },
  {
    id: 'exterior',
    name: 'Investimentos no exterior',
    examples: 'ETFs de índices globais ou dos EUA negociados na B3, BDRs',
    risk: 4,
    liquidity: 'Negociados na bolsa (2 dias úteis), variam com o mercado e o dólar',
    tax: '15% sobre o lucro na B3',
    purpose: 'Diversificar em outras moedas e economias, reduzindo a dependência do Brasil.',
  },
]

export const DEFAULT_ALLOCATION: Record<ProfileId, Record<AssetClassId, number>> = {
  conservador: { posfixado: 45, inflacao: 25, prefixado: 5, isentos: 20, fiis: 5, acoes: 0, exterior: 0 },
  moderado: { posfixado: 25, inflacao: 20, prefixado: 5, isentos: 10, fiis: 15, acoes: 15, exterior: 10 },
  arrojado: { posfixado: 10, inflacao: 15, prefixado: 5, isentos: 5, fiis: 15, acoes: 30, exterior: 20 },
}

export function allocationFor(profile: InvestorProfile): Record<AssetClassId, number> {
  return { ...DEFAULT_ALLOCATION[profile.profile], ...profile.custom }
}

export interface PlanItem {
  cls: AssetClass
  percent: number
  amount: number
}

/** Divide o valor a investir do mês entre as classes, fechando os centavos. */
export function splitPlan(total: number, allocation: Record<AssetClassId, number>): PlanItem[] {
  const sum = Object.values(allocation).reduce((a, b) => a + b, 0) || 1
  const items = ASSET_CLASSES.filter((c) => allocation[c.id] > 0).map((cls) => ({
    cls,
    percent: (allocation[cls.id] / sum) * 100,
    amount: Math.floor(((total * allocation[cls.id]) / sum) * 100) / 100,
  }))
  const diff = Math.round((total - items.reduce((a, i) => a + i.amount, 0)) * 100) / 100
  if (items.length && diff) items[0].amount = Math.round((items[0].amount + diff) * 100) / 100
  return items.sort((a, b) => b.percent - a.percent)
}

/** O perfil deve ser refeito a cada 2 anos (ou quando a vida mudar). */
export function profileExpired(profile: InvestorProfile, month: MonthKey) {
  return diffMonths(profile.date.slice(0, 7), month) >= 24
}

// ---------- Chat ----------

export type Rich = string | { b: string }
export type ChatAction =
  | { type: 'quiz' }
  | { type: 'route'; to: string }
  | { type: 'ask'; text: string }
  | { type: 'simulate'; monthly: number }

export type Block =
  | { t: 'p'; text: Rich[] }
  | { t: 'ul'; items: Rich[][] }
  | { t: 'kv'; rows: [string, string][] }
  | { t: 'actions'; actions: { label: string; action: ChatAction }[] }
  | { t: 'note'; text: string }

export interface AdvisorContext {
  data: DataState
  month: MonthKey
  rec: Recommendation
  summary: MonthSummary
}

const b = (s: string): Rich => ({ b: s })
const p = (...text: Rich[]): Block => ({ t: 'p', text })
const ul = (...items: Rich[][]): Block => ({ t: 'ul', items })
const note = (text: string): Block => ({ t: 'note', text })
const actions = (...a: { label: string; action: ChatAction }[]): Block => ({ t: 'actions', actions: a })

/** Só aparecem antes da primeira pergunta. */
export const SUGGESTIONS = ['Quanto devo investir este mês?', 'Onde investir meu dinheiro?', 'Onde deixar minha reserva?']

export function normalize(text: string) {
  return ` ${text
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9&%+ ]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()} `
}

type Handler = (ctx: AdvisorContext) => Block[]

function needProfile(): Block[] {
  return [
    p('Para sugerir onde investir eu preciso conhecer seu ', b('perfil de investidor'), '. São 8 perguntas rápidas sobre objetivo, prazo e como você reage a perdas.'),
    actions({ label: 'Descobrir meu perfil', action: { type: 'quiz' } }),
  ]
}

function reserveLine(ctx: AdvisorContext): Block {
  const r = ctx.rec.reserve
  if (r.target > 0 && r.gap === 0) return p('Sua reserva de emergência está ', b('completa'), ` (${formatBRL(r.balance)}). Pode focar em investir.`)
  if (r.target === 0) return p('Ainda não consigo calcular sua reserva: registre seus gastos do mês para eu estimar o custo de vida.')
  return p(
    'Sua reserva tem ',
    b(formatBRL(r.balance)),
    ` de ${formatBRL(r.target)} (${formatPercent(r.progress)}). Faltam `,
    b(formatBRL(r.gap)),
    ctx.rec.monthsToReserve ? ` — no ritmo sugerido, cerca de ${formatDuration(ctx.rec.monthsToReserve)}.` : '.',
  )
}

const handlers: Record<string, { keywords: string[]; run: Handler; weak?: boolean }> = {
  ajuda: {
    weak: true,
    keywords: ['oi', 'ola', 'ajuda', 'bom dia', 'boa tarde', 'boa noite', 'quem e voce', 'o que voce', 'como funciona', 'menu'],
    run: (ctx) => [
      p(`Olá${ctx.data.settings.userName ? `, ${ctx.data.settings.userName}` : ''}! Sou o assistente de investimentos do app. Eu uso seus números (renda, gastos, reserva e metas) para responder — tudo aqui no aparelho, sem internet.`),
      p('Pode perguntar sobre quanto e onde investir, reserva de emergência, Tesouro, CDB, ações, fundos imobiliários, imposto ou suas metas.'),
    ],
  },

  perfil: {
    keywords: ['perfil', 'suitability', 'conservador', 'moderado', 'arrojado', 'agressivo', 'tipo de investidor', 'que investidor'],
    run: (ctx) => {
      const prof = ctx.data.profile
      if (!prof) return needProfile()
      const info = PROFILES[prof.profile]
      const out: Block[] = [
        p('Seu perfil é ', b(info.name), ` (${prof.score} de ${MAX_SCORE} pontos).`),
        p(info.summary),
        p(info.detail),
      ]
      if (profileExpired(prof, ctx.month)) out.push(p('Seu perfil tem mais de 2 anos. Vale refazer o questionário.'))
      return out
    },
  },

  quanto: {
    keywords: ['quanto investir', 'quanto guardar', 'quanto devo', 'quanto posso', 'quanto por mes', 'quanto separar', 'valor para investir', 'quanto aplicar', 'investir este mes', 'investir esse mes'],
    run: (ctx) => {
      const { rec } = ctx
      if (rec.income <= 0)
        return [p('Ainda não sei sua renda deste mês. Lance suas receitas (ou cadastre o salário em Contas fixas) que eu calculo.')]
      return [
        p('Pela sua regra de distribuição, separe ', b(formatBRL(rec.futurePlanned)), ` neste mês (renda de ${formatBRL(rec.income)}):`),
        { t: 'kv', rows: [['Reserva de emergência', formatBRL(rec.toReserve)], ['Investimentos', formatBRL(rec.toInvest)], ['Já guardado no mês', formatBRL(rec.savedThisMonth)]] },
        reserveLine(ctx),
        p('Dica: invista assim que o salário cair ("pague-se primeiro") — o que sobra no fim do mês costuma ser zero.'),
      ]
    },
  },

  onde: {
    keywords: ['onde investir', 'em que investir', 'em oque investir', 'o que investir', 'no que investir', 'carteira', 'alocacao', 'distribuir', 'diversific', 'como investir', 'investir meu dinheiro', 'recomenda', 'sugere', 'sugestao', 'aplicar meu dinheiro'],
    run: (ctx) => {
      const prof = ctx.data.profile
      if (!prof) return needProfile()
      const items = splitPlan(ctx.rec.toInvest, allocationFor(prof))
      const out: Block[] = [p('Para o perfil ', b(PROFILES[prof.profile].name), ', uma carteira equilibrada fica assim:')]
      if (ctx.rec.toReserve > 0) out.push(p('1º ', b(`${formatBRL(ctx.rec.toReserve)} para a reserva`), ' em Tesouro Selic ou CDB com liquidez diária.'))
      out.push({
        t: 'kv',
        rows: items.map((i) => [`${i.cls.name} (${Math.round(i.percent)}%)`, ctx.rec.toInvest > 0 ? formatBRL(i.amount) : '—']),
      })
      out.push(ul(...items.slice(0, 3).map((i) => [b(i.cls.name), `: ${i.cls.examples}.`])))
      out.push(p('Na prática: use uma corretora ou banco com Tesouro Direto e taxa zero, prefira emissores cobertos pelo FGC na renda fixa e faça aportes todo mês. Rebalanceie 1–2 vezes por ano.'))
      return out
    },
  },

  reserva: {
    keywords: ['reserva', 'emergencia', 'imprevisto', 'colchao'],
    run: (ctx) => [
      reserveLine(ctx),
      p('Onde deixar: precisa ser ', b('seguro'), ' e com ', b('resgate no mesmo dia'), '. Boas opções:'),
      ul([b('Tesouro Selic'), ': risco mínimo (governo federal), resgate em D+0 em dias úteis no horário do Tesouro.'], [b('CDB com liquidez diária'), ' pagando ~100% do CDI, de banco coberto pelo FGC.'], ['Evite ações, FIIs ou cripto: podem estar em queda justamente quando você precisar.']),
      p(`Tamanho: ${ctx.data.settings.reserveMonths} meses do seu custo de vida (${formatBRL(ctx.rec.reserve.monthlyCost)}/mês). Ajuste em Metas.`),
    ],
  },

  tesouro: {
    keywords: ['tesouro', 'selic', 'ipca', 'prefixado', 'titulo publico', 'titulos publicos'],
    run: () => [
      p('O ', b('Tesouro Direto'), ' é empréstimo ao governo federal — o investimento de menor risco de crédito do país. Três famílias:'),
      ul([b('Tesouro Selic'), ': segue a taxa básica de juros. Quase não oscila. Ideal para reserva e curto prazo.'], [b('Tesouro IPCA+'), ': paga inflação + uma taxa fixa. Protege o poder de compra no longo prazo, mas oscila se vender antes.'], [b('Tesouro Prefixado'), ': taxa travada na compra. Bom se os juros caírem; oscila antes do vencimento.']),
      p('Custos: taxa de custódia da B3 (em geral 0,2% ao ano, com isenção parcial no Tesouro Selic) e IR regressivo. Compre pelo site do Tesouro Direto ou por qualquer banco/corretora habilitada.'),
    ],
  },

  cdb: {
    keywords: ['cdb', 'lci', 'lca', 'fgc', 'renda fixa', 'cri', 'cra', 'debenture', 'tesouro ou cdb'],
    run: () => [
      p(b('CDB'), ' é empréstimo a um banco; ', b('LCI/LCA'), ' também, mas lastreadas em imóveis/agro e isentas de IR para pessoa física.'),
      ul(['CDB, LCI e LCA têm proteção do ', b('FGC'), ': até R$ 250 mil por CPF por instituição (limite global de R$ 1 milhão a cada 4 anos).'], ['CRI, CRA e debêntures ', b('não'), ' têm FGC — risco é da empresa emissora.'], ['Compare sempre a taxa líquida: uma LCI a 90% do CDI pode render mais que um CDB a 105% do CDI após o IR, dependendo do prazo.']),
      p(b('Tesouro ou CDB?'), ' Na reserva, os dois funcionam se tiverem liquidez diária. CDBs de bancos médios costumam pagar mais (com FGC); o Tesouro tem o menor risco de crédito.'),
    ],
  },

  poupanca: {
    keywords: ['poupanca'],
    run: () => [
      p('A poupança é simples e isenta de IR, mas costuma render ', b('menos'), ' que as alternativas seguras:'),
      ul(
        ['Quando a Selic está acima de 8,5% ao ano, ela rende 0,5% ao mês + TR.'],
        ['Com Selic até 8,5%, rende 70% da Selic + TR.'],
        ['O rendimento só cai no "aniversário" mensal — resgatar antes perde o mês.'],
      ),
      p('Tesouro Selic e CDBs com liquidez diária a 100% do CDI normalmente rendem mais, mesmo depois do IR, e com segurança parecida.'),
    ],
  },

  acoes: {
    keywords: ['acao', 'acoes', 'bolsa', 'ibovespa', 'dividendo', 'etf', 'renda variavel', 'b3'],
    run: (ctx) => [
      p('Ações são pedaços de empresas; ', b('ETFs'), ' são cestas de ações em um único papel (ex.: um ETF que replica o Ibovespa).'),
      ul(['Para começar, ETFs dão diversificação imediata e custo baixo.'], ['Pense em prazos de 5+ anos: no curto prazo a bolsa pode cair 20–30%.'], ['Aporte todo mês em vez de tentar acertar o melhor momento.'], ['Nunca invista em bolsa o dinheiro da reserva.']),
      ctx.data.profile?.profile === 'conservador'
        ? p('Pelo seu perfil ', b('conservador'), ', a sugestão é não ter ações por enquanto — ou começar com uma fatia bem pequena após completar a reserva.')
        : p('No seu perfil, a fatia sugerida de ações/ETFs é ', b(`${ctx.data.profile ? allocationFor(ctx.data.profile).acoes : '—'}%`), ' dos investimentos.'),
    ],
  },

  fiis: {
    keywords: ['fii', 'fiis', 'fundo imobiliario', 'fundos imobiliarios', 'imobiliario'],
    run: () => [
      p('Os ', b('fundos imobiliários'), ' juntam dinheiro de muitos investidores para ter imóveis (shoppings, galpões, escritórios) ou títulos de crédito imobiliário.'),
      ul(['Pagam rendimentos em geral ', b('mensais'), ', normalmente isentos de IR para pessoa física.'], ['Cotas são negociadas na bolsa: o preço oscila.'], ['Diversifique entre fundos de tijolo e de papel e entre gestoras.'], ['Olhe vacância, qualidade dos inquilinos, P/VP e histórico de rendimentos — não só o "dividend yield".']),
    ],
  },

  exterior: {
    keywords: ['exterior', 'dolar', 'eua', 'estados unidos', 'sp500', 's&p', 'internacional', 'bdr', 'global'],
    run: () => [
      p('Investir no exterior diversifica ', b('moeda e economia'), ': se o Brasil vai mal e o real cai, essa parte tende a compensar.'),
      ul(['Jeito mais simples: ETFs de índices globais ou dos EUA negociados na própria B3 (em reais).'], ['BDRs dão acesso a ações estrangeiras pela B3.'], ['Conta em corretora no exterior tem regras próprias de IR e declaração — pesquise antes.']),
      p('A fatia costuma ficar entre 10% e 25% da carteira em perfis moderados e arrojados.'),
    ],
  },

  cripto: {
    keywords: ['cripto', 'criptomoeda', 'bitcoin', 'btc', 'ethereum', 'moeda digital', 'blockchain'],
    run: (ctx) => [
      p('Criptoativos são ', b('muito voláteis'), ': quedas de 50% ou mais já aconteceram várias vezes.'),
      ul(['Só considere depois de ter reserva completa e uma base sólida.'], ['Se entrar, use uma fatia pequena (algo como até 5% dos investimentos) que você aceitaria perder.'], ['Prefira ETFs de cripto na B3 ou corretoras conhecidas; desconfie de promessas de retorno garantido.']),
      ctx.data.profile?.profile === 'conservador' ? p('Para o perfil conservador, cripto não é indicada.') : p('Por isso ela não entra na carteira sugerida automática — fica a seu critério.'),
    ],
  },

  ir: {
    keywords: ['imposto', 'ir', 'tributa', 'come cotas', 'isento', 'isencao', 'leao', 'tabela regressiva', 'declarar'],
    run: () => [
      p('Regras gerais para pessoa física (vigentes até 2025 — a tributação de investimentos está em discussão, confirme as atuais):'),
      { t: 'kv', rows: [['Renda fixa até 180 dias', '22,5%'], ['181 a 360 dias', '20%'], ['361 a 720 dias', '17,5%'], ['Acima de 720 dias', '15%'], ['LCI, LCA, CRI, CRA, poupança', 'Isentos'], ['Ações (venda até R$ 20 mil/mês)', 'Isento'], ['Ações e ETFs (lucro)', '15%'], ['FIIs: rendimentos / lucro na venda', 'Isentos / 20%']] },
      p('O IR da renda fixa é retido automaticamente no resgate. Na bolsa, você mesmo calcula e paga via DARF quando houver lucro tributável.'),
      note('Confira sempre a legislação vigente ou um contador.'),
    ],
  },

  dividas: {
    keywords: ['divida', 'dividas', 'cartao', 'emprestimo', 'cheque especial', 'rotativo', 'endividado', 'negativado'],
    run: () => [
      p('Antes de investir, ', b('quite dívidas caras'), '. O rotativo do cartão e o cheque especial cobram juros muito acima de qualquer investimento.'),
      ul(['Liste as dívidas pela taxa de juros e ataque a mais cara primeiro.'], ['Tente trocar dívida cara por uma mais barata (consignado, portabilidade).'], ['Mantenha só uma reserva mínima enquanto paga.'], ['Registre as parcelas em Contas fixas para enxergar o impacto no mês.']),
    ],
  },

  simular: {
    keywords: ['simul', 'juros compostos', 'quanto terei', 'quanto vou ter', 'futuro', 'aposentar', 'aposentadoria', 'milhao', 'longo prazo', 'rende quanto'],
    run: (ctx) => {
      const monthly = ctx.rec.toInvest || ctx.rec.futurePlanned
      return [
        p('Os juros compostos fazem o tempo trabalhar a seu favor: quanto antes começar, menos precisa aportar.'),
        monthly > 0
          ? p('Investindo ', b(`${formatBRL(monthly)}/mês`), ', abra o simulador para ver quanto acumula em 10, 20 ou 30 anos e quanto precisa para uma meta (como o primeiro milhão).')
          : p('Defina sua renda e sua regra de distribuição para eu sugerir um aporte mensal.'),
        actions({ label: 'Abrir simulador com meu aporte', action: { type: 'simulate', monthly } }),
      ]
    },
  },

  risco: {
    keywords: ['risco', 'liquidez', 'volatil', 'volatilidade', 'perder dinheiro', 'garantia', 'seguro investir', 'e seguro'],
    run: () => [
      p('Todo investimento equilibra três coisas: ', b('segurança'), ', ', b('liquidez'), ' (resgatar rápido) e ', b('rentabilidade'), '. Nenhum tem as três no máximo.'),
      ul(['Risco de crédito: quem pegou o dinheiro não pagar (mitigado pelo FGC e pelo Tesouro).'], ['Risco de mercado: o preço oscila (bolsa, FIIs, títulos antes do vencimento).'], ['Risco de liquidez: não conseguir vender quando precisar.'], ['Diversificar entre classes reduz o impacto de qualquer um deles.']),
    ],
  },

  inflacao: {
    keywords: ['inflacao', 'poder de compra', 'ipca'],
    run: () => [
      p('A inflação corrói o dinheiro parado. O que importa é o ', b('ganho real'), ': rentabilidade acima da inflação.'),
      ul(['Títulos IPCA+ garantem inflação + juros reais se levados ao vencimento.'], ['No simulador, veja o valor "em valores de hoje" para enxergar o poder de compra futuro.']),
    ],
  },

  gastos: {
    keywords: ['gasto', 'gastos', 'gastando', 'economizar', 'cortar', 'sobrar', 'sobra', 'despesas'],
    run: (ctx) => {
      const top = ctx.summary.categories.filter((c) => c.category.kind !== 'futuro').slice(0, 3)
      const over = ctx.summary.buckets.filter((b2) => b2.bucket.kind !== 'futuro' && b2.actual > b2.planned)
      return [
        p('Neste mês você gastou ', b(formatBRL(ctx.summary.spent)), ctx.summary.income > 0 ? ` (${formatPercent(ctx.summary.spent / ctx.summary.income)} da renda).` : '.'),
        top.length ? p('Maiores categorias: ', top.map((c) => `${c.category.name} (${formatBRL(c.actual)})`).join(', '), '.') : p('Ainda não há gastos lançados neste mês.'),
        over.length ? p('Acima do plano: ', b(over.map((o) => o.bucket.name).join(', ')), '. Cortar aí libera dinheiro para investir.') : p('Seus potes de gasto estão dentro do planejado.'),
      ]
    },
  },

  metas: {
    keywords: ['meta', 'metas', 'objetivo', 'sonho', 'viagem', 'carro', 'casa propria', 'comprar'],
    run: (ctx) => {
      const goals = ctx.data.goals.filter((g) => g.kind === 'meta')
      return [
        goals.length ? p(`Você tem ${goals.length} ${goals.length === 1 ? 'meta' : 'metas'}. Para cada uma, o app calcula quanto guardar por mês até o prazo.`) : p('Você ainda não criou metas. Crie uma com valor e prazo e eu mostro quanto guardar por mês.'),
        p('Onde investir depende do prazo: ', b('até 2 anos'), ' → renda fixa com liquidez ou vencimento próximo; ', b('3–5 anos'), ' → Tesouro IPCA+/prefixado ou LCI/LCA; ', b('5+ anos'), ' → pode incluir renda variável conforme seu perfil.'),
      ]
    },
  },
}

/** Escolhe a resposta pela intenção com mais palavras-chave encontradas. */
export function detectIntent(question: string): string | null {
  const text = normalize(question)
  let best: string | null = null
  let bestScore = 0
  for (const [id, h] of Object.entries(handlers)) {
    // Saudações/frases genéricas só vencem quando nenhum assunto específico aparece.
    const score = h.keywords.reduce((a, k) => (text.includes(` ${k}`) ? a + k.split(' ').length : a), 0) * (h.weak ? 0.25 : 1)
    if (score > bestScore) {
      best = id
      bestScore = score
    }
  }
  return best
}

export function answer(question: string, ctx: AdvisorContext): Block[] {
  const intent = detectIntent(question)
  if (intent) return handlers[intent].run(ctx)
  return [
    p('Não entendi essa. Tente perguntar de outro jeito — por exemplo, sobre quanto investir, reserva, Tesouro, CDB, ações, imposto ou suas metas.'),
  ]
}

export function greeting(ctx: AdvisorContext): Block[] {
  const name = ctx.data.settings.userName
  const hello = `Olá${name ? `, ${name}` : ''}! Pergunte o que quiser sobre seus investimentos — respondo com os seus números, sem internet.`
  if (!ctx.data.profile) return [p(hello), p('Dica: faça o questionário de perfil para eu sugerir onde investir.')]
  return [p(hello)]
}
