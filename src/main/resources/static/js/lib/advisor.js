/**
 * Assistente de investimentos: regras e textos educativos, sem modelo de IA.
 * Porte de legacy-web/src/lib/advisor.ts. Os números (renda, reserva, carteira...) chegam prontos
 * do servidor (/api/assistente, em BigDecimal); aqui só se escolhe o texto. As perguntas não saem
 * do navegador.
 *
 * Uma resposta é uma lista de blocos:
 *   { t: 'p', text: [string | { b: string }] }   parágrafo (com trechos em negrito)
 *   { t: 'ul', items: [[...]] }                  lista
 *   { t: 'kv', rows: [[rótulo, valor]] }         tabela de dois campos
 *   { t: 'actions', actions: [{ label, href }] } links de ação
 *   { t: 'note', text: string }                  observação
 */
import { formatBRL, formatDuration, formatPercent } from './money.js'

export const PROFILES = {
  CONSERVATIVE: {
    name: 'Conservador',
    summary: 'Prioriza segurança e liquidez. Aceita render menos para não ver o saldo cair.',
    detail: 'A maior parte vai para renda fixa de baixo risco. Renda variável fica de fora ou em fatia mínima.',
  },
  MODERATE: {
    name: 'Moderado',
    summary: 'Busca equilíbrio: aceita oscilações moderadas em troca de mais rentabilidade no médio/longo prazo.',
    detail: 'A base é renda fixa, com uma parte em fundos imobiliários, ações e exterior para crescer.',
  },
  AGGRESSIVE: {
    name: 'Arrojado',
    summary: 'Foca em crescimento no longo prazo e tolera quedas fortes no caminho.',
    detail: 'Maior fatia em renda variável e exterior, mantendo renda fixa para estabilidade e oportunidades.',
  },
}

/** Classes de ativos (mesmos nomes do enum AssetClass do servidor). */
export const ASSET_CLASSES = {
  POST_FIXED: { name: 'Renda fixa pós-fixada', examples: 'Tesouro Selic, CDB e LC com liquidez diária que paguem perto de 100% do CDI' },
  INFLATION_LINKED: { name: 'Renda fixa atrelada à inflação', examples: 'Tesouro IPCA+, CDBs e debêntures IPCA+' },
  PRE_FIXED: { name: 'Renda fixa prefixada', examples: 'Tesouro Prefixado, CDB prefixado' },
  TAX_EXEMPT: { name: 'Renda fixa isenta', examples: 'LCI e LCA (com FGC); CRI e CRA (sem FGC, mais risco)' },
  REAL_ESTATE_FUNDS: { name: 'Fundos imobiliários (FIIs)', examples: 'Fundos de tijolo (shoppings, galpões, lajes) e de papel (recebíveis)' },
  STOCKS: { name: 'Ações e ETFs brasileiros', examples: 'ETFs que replicam o Ibovespa ou ações de empresas sólidas' },
  INTERNATIONAL: { name: 'Investimentos no exterior', examples: 'ETFs de índices globais ou dos EUA negociados na B3, BDRs' },
}

export const SUGGESTIONS = ['Quanto devo investir este mês?', 'Onde investir meu dinheiro?', 'Onde deixar minha reserva?']

const b = (s) => ({ b: s })
const p = (...text) => ({ t: 'p', text })
const ul = (...items) => ({ t: 'ul', items })
const note = (text) => ({ t: 'note', text })
const actions = (...a) => ({ t: 'actions', actions: a })

/** Minúsculas, sem acento e sem pontuação, com espaço nas pontas (para achar palavras inteiras). */
export function normalize(text) {
  return ` ${text
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9&%+ ]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()} `
}

const needProfile = () => [
  p('Para sugerir onde investir eu preciso conhecer seu ', b('perfil de investidor'), '. São 8 perguntas rápidas sobre objetivo, prazo e como você reage a perdas.'),
  actions({ label: 'Descobrir meu perfil', href: '#questionario' }),
]

function reserveLine(ctx) {
  const r = ctx.recommendation.reserve
  if (r.target > 0 && r.gap === 0) return p('Sua reserva de emergência está ', b('completa'), ` (${formatBRL(r.balance)}). Pode focar em investir.`)
  if (r.target === 0) return p('Ainda não consigo calcular sua reserva: registre seus gastos do mês para eu estimar o custo de vida.')
  return p(
    'Sua reserva tem ',
    b(formatBRL(r.balance)),
    ` de ${formatBRL(r.target)} (${formatPercent(r.progress)}). Faltam `,
    b(formatBRL(r.gap)),
    ctx.recommendation.monthsToReserve ? `. No ritmo sugerido, leva cerca de ${formatDuration(ctx.recommendation.monthsToReserve)}.` : '.',
  )
}

/** Fatia de uma classe na carteira do perfil, em % inteiro (só exibição). */
const share = (ctx, assetClass) => {
  const item = ctx.plan.find((i) => i.assetClass === assetClass)
  return item ? `${Math.round(item.percent)}%` : '0%'
}

const handlers = {
  ajuda: {
    weak: true,
    keywords: ['oi', 'ola', 'ajuda', 'bom dia', 'boa tarde', 'boa noite', 'quem e voce', 'o que voce', 'como funciona', 'menu'],
    run: (ctx) => [
      p(`Olá${ctx.displayName ? `, ${ctx.displayName}` : ''}! Sou o assistente de investimentos do app. Respondo com os seus números (renda, gastos, reserva e metas), e as suas perguntas não saem do navegador.`),
      p('Pode perguntar sobre quanto e onde investir, reserva de emergência, Tesouro, CDB, ações, fundos imobiliários, imposto ou suas metas.'),
    ],
  },

  perfil: {
    keywords: ['perfil', 'suitability', 'conservador', 'moderado', 'arrojado', 'agressivo', 'tipo de investidor', 'que investidor'],
    run: (ctx) => {
      if (!ctx.profile) return needProfile()
      const info = PROFILES[ctx.profile]
      const out = [p('Seu perfil é ', b(info.name), ctx.profileScore > 0 ? ` (${ctx.profileScore} de ${ctx.maxScore} pontos).` : '.'), p(info.summary), p(info.detail)]
      if (ctx.profileExpired) out.push(p('Seu perfil tem mais de 2 anos. Vale refazer o questionário.'))
      return out
    },
  },

  quanto: {
    keywords: ['quanto investir', 'quanto guardar', 'quanto devo', 'quanto posso', 'quanto por mes', 'quanto separar', 'valor para investir', 'quanto aplicar', 'investir este mes', 'investir esse mes'],
    run: (ctx) => {
      const rec = ctx.recommendation
      if (rec.income <= 0) return [p('Ainda não sei sua renda deste mês. Lance suas receitas (ou cadastre o salário em Contas fixas) que eu calculo.')]
      return [
        p('Pela sua regra de distribuição, separe ', b(formatBRL(rec.futurePlanned)), ` neste mês (renda de ${formatBRL(rec.income)}):`),
        { t: 'kv', rows: [['Reserva de emergência', formatBRL(rec.toReserve)], ['Investimentos', formatBRL(rec.toInvest)], ['Já guardado no mês', formatBRL(rec.savedThisMonth)]] },
        reserveLine(ctx),
        p('Dica: invista assim que o salário cair ("pague-se primeiro"). O que sobra no fim do mês costuma ser zero.'),
      ]
    },
  },

  onde: {
    keywords: ['onde investir', 'em que investir', 'em oque investir', 'o que investir', 'no que investir', 'carteira', 'alocacao', 'distribuir', 'diversific', 'como investir', 'investir meu dinheiro', 'recomenda', 'sugere', 'sugestao', 'aplicar meu dinheiro'],
    run: (ctx) => {
      if (!ctx.profile) return needProfile()
      const rec = ctx.recommendation
      const out = [p('Para o perfil ', b(PROFILES[ctx.profile].name), ', uma carteira equilibrada fica assim:')]
      if (rec.toReserve > 0) out.push(p('1º ', b(`${formatBRL(rec.toReserve)} para a reserva`), ' em Tesouro Selic ou CDB com liquidez diária.'))
      out.push({
        t: 'kv',
        rows: ctx.plan.map((i) => [`${ASSET_CLASSES[i.assetClass].name} (${Math.round(i.percent)}%)`, rec.toInvest > 0 ? formatBRL(i.amount) : '—']),
      })
      out.push(ul(...ctx.plan.slice(0, 3).map((i) => [b(ASSET_CLASSES[i.assetClass].name), `: ${ASSET_CLASSES[i.assetClass].examples}.`])))
      out.push(p('Na prática: use uma corretora ou banco com Tesouro Direto e taxa zero, prefira emissores cobertos pelo FGC na renda fixa e faça aportes todo mês. Rebalanceie 1 ou 2 vezes por ano.'))
      return out
    },
  },

  reserva: {
    keywords: ['reserva', 'emergencia', 'imprevisto', 'colchao'],
    run: (ctx) => [
      reserveLine(ctx),
      p('Onde deixar: precisa ser ', b('seguro'), ' e com ', b('resgate no mesmo dia'), '. Boas opções:'),
      ul([b('Tesouro Selic'), ': risco mínimo (governo federal), resgate em D+0 em dias úteis no horário do Tesouro.'], [b('CDB com liquidez diária'), ' pagando ~100% do CDI, de banco coberto pelo FGC.'], ['Evite ações, FIIs ou cripto: podem estar em queda justamente quando você precisar.']),
      p(`Tamanho: ${ctx.reserveMonths} meses do seu custo de vida (${formatBRL(ctx.recommendation.reserve.monthlyCost)}/mês). Ajuste em Metas.`),
    ],
  },

  tesouro: {
    keywords: ['tesouro', 'selic', 'ipca', 'prefixado', 'titulo publico', 'titulos publicos'],
    run: () => [
      p('O ', b('Tesouro Direto'), ' é empréstimo ao governo federal, o investimento de menor risco de crédito do país. Três famílias:'),
      ul([b('Tesouro Selic'), ': segue a taxa básica de juros. Quase não oscila. Ideal para reserva e curto prazo.'], [b('Tesouro IPCA+'), ': paga inflação + uma taxa fixa. Protege o poder de compra no longo prazo, mas oscila se vender antes.'], [b('Tesouro Prefixado'), ': taxa travada na compra. Bom se os juros caírem; oscila antes do vencimento.']),
      p('Custos: taxa de custódia da B3 (em geral 0,2% ao ano, com isenção parcial no Tesouro Selic) e IR regressivo. Compre pelo site do Tesouro Direto ou por qualquer banco ou corretora habilitada.'),
    ],
  },

  cdb: {
    keywords: ['cdb', 'lci', 'lca', 'fgc', 'renda fixa', 'cri', 'cra', 'debenture', 'tesouro ou cdb'],
    run: () => [
      p(b('CDB'), ' é empréstimo a um banco; ', b('LCI/LCA'), ' também, mas lastreadas em imóveis/agro e isentas de IR para pessoa física.'),
      ul(['CDB, LCI e LCA têm proteção do ', b('FGC'), ': até R$ 250 mil por CPF por instituição (limite global de R$ 1 milhão a cada 4 anos).'], ['CRI, CRA e debêntures ', b('não'), ' têm FGC: o risco é da empresa emissora.'], ['Compare sempre a taxa líquida: uma LCI a 90% do CDI pode render mais que um CDB a 105% do CDI depois do IR, dependendo do prazo.']),
      p(b('Tesouro ou CDB?'), ' Na reserva, os dois funcionam se tiverem liquidez diária. CDBs de bancos médios costumam pagar mais (com FGC); o Tesouro tem o menor risco de crédito.'),
    ],
  },

  poupanca: {
    keywords: ['poupanca'],
    run: () => [
      p('A poupança é simples e isenta de IR, mas costuma render ', b('menos'), ' que as alternativas seguras:'),
      ul(['Quando a Selic está acima de 8,5% ao ano, ela rende 0,5% ao mês + TR.'], ['Com Selic até 8,5%, rende 70% da Selic + TR.'], ['O rendimento só cai no "aniversário" mensal: resgatar antes perde o mês.']),
      p('Tesouro Selic e CDBs com liquidez diária a 100% do CDI normalmente rendem mais, mesmo depois do IR, e com segurança parecida.'),
    ],
  },

  acoes: {
    keywords: ['acao', 'acoes', 'bolsa', 'ibovespa', 'dividendo', 'etf', 'renda variavel', 'b3'],
    run: (ctx) => [
      p('Ações são pedaços de empresas; ', b('ETFs'), ' são cestas de ações em um único papel (ex.: um ETF que replica o Ibovespa).'),
      ul(['Para começar, ETFs dão diversificação imediata e custo baixo.'], ['Pense em prazos de 5 anos ou mais: no curto prazo a bolsa pode cair 20% a 30%.'], ['Aporte todo mês em vez de tentar acertar o melhor momento.'], ['Nunca invista em bolsa o dinheiro da reserva.']),
      ctx.profile === 'CONSERVATIVE'
        ? p('Pelo seu perfil ', b('conservador'), ', a sugestão é não ter ações por enquanto, ou começar com uma fatia bem pequena depois de completar a reserva.')
        : p('No seu perfil, a fatia sugerida de ações e ETFs é ', b(ctx.profile ? share(ctx, 'STOCKS') : '—'), ' dos investimentos.'),
    ],
  },

  fiis: {
    keywords: ['fii', 'fiis', 'fundo imobiliario', 'fundos imobiliarios', 'imobiliario'],
    run: () => [
      p('Os ', b('fundos imobiliários'), ' juntam dinheiro de muitos investidores para ter imóveis (shoppings, galpões, escritórios) ou títulos de crédito imobiliário.'),
      ul(['Pagam rendimentos em geral ', b('mensais'), ', normalmente isentos de IR para pessoa física.'], ['As cotas são negociadas na bolsa: o preço oscila.'], ['Diversifique entre fundos de tijolo e de papel e entre gestoras.'], ['Olhe vacância, qualidade dos inquilinos, P/VP e histórico de rendimentos, não só o "dividend yield".']),
    ],
  },

  exterior: {
    keywords: ['exterior', 'dolar', 'eua', 'estados unidos', 'sp500', 's&p', 'internacional', 'bdr', 'global'],
    run: () => [
      p('Investir no exterior diversifica ', b('moeda e economia'), ': se o Brasil vai mal e o real cai, essa parte tende a compensar.'),
      ul(['Jeito mais simples: ETFs de índices globais ou dos EUA negociados na própria B3 (em reais).'], ['BDRs dão acesso a ações estrangeiras pela B3.'], ['Conta em corretora no exterior tem regras próprias de IR e declaração. Pesquise antes.']),
      p('A fatia costuma ficar entre 10% e 25% da carteira em perfis moderados e arrojados.'),
    ],
  },

  cripto: {
    keywords: ['cripto', 'criptomoeda', 'bitcoin', 'btc', 'ethereum', 'moeda digital', 'blockchain'],
    run: (ctx) => [
      p('Criptoativos são ', b('muito voláteis'), ': quedas de 50% ou mais já aconteceram várias vezes.'),
      ul(['Só considere depois de ter a reserva completa e uma base sólida.'], ['Se entrar, use uma fatia pequena (algo como até 5% dos investimentos) que você aceitaria perder.'], ['Prefira ETFs de cripto na B3 ou corretoras conhecidas; desconfie de promessas de retorno garantido.']),
      ctx.profile === 'CONSERVATIVE' ? p('Para o perfil conservador, cripto não é indicada.') : p('Por isso ela não entra na carteira sugerida automática: fica a seu critério.'),
    ],
  },

  ir: {
    keywords: ['imposto', 'ir', 'tributa', 'come cotas', 'isento', 'isencao', 'leao', 'tabela regressiva', 'declarar'],
    run: () => [
      p('Regras gerais para pessoa física (vigentes até 2025; a tributação de investimentos está em discussão, confirme as atuais):'),
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
      const rec = ctx.recommendation
      const monthly = rec.toInvest > 0 ? rec.toInvest : rec.futurePlanned
      return [
        p('Os juros compostos fazem o tempo trabalhar a seu favor: quanto antes começar, menos precisa aportar.'),
        monthly > 0
          ? p('Investindo ', b(`${formatBRL(monthly)}/mês`), ', abra o simulador para ver quanto acumula em 10, 20 ou 30 anos e quanto precisa para uma meta (como o primeiro milhão).')
          : p('Defina sua renda e sua regra de distribuição para eu sugerir um aporte mensal.'),
        actions({ label: 'Abrir o simulador com meu aporte', href: `/simulador?mensal=${String(monthly).replace('.', ',')}` }),
      ]
    },
  },

  risco: {
    keywords: ['risco', 'liquidez', 'volatil', 'volatilidade', 'perder dinheiro', 'garantia', 'seguro investir', 'e seguro'],
    run: () => [
      p('Todo investimento equilibra três coisas: ', b('segurança'), ', ', b('liquidez'), ' (resgatar rápido) e ', b('rentabilidade'), '. Nenhum tem as três no máximo.'),
      ul(['Risco de crédito: quem pegou o dinheiro não pagar (o FGC e o Tesouro reduzem esse risco).'], ['Risco de mercado: o preço oscila (bolsa, FIIs, títulos antes do vencimento).'], ['Risco de liquidez: não conseguir vender quando precisar.'], ['Diversificar entre classes reduz o impacto de qualquer um deles.']),
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
    run: (ctx) => [
      p('Neste mês você gastou ', b(formatBRL(ctx.spent)), ctx.income > 0 ? ` (${formatPercent(ctx.spentShare)} da renda).` : '.'),
      ctx.topCategories.length
        ? p('Maiores categorias: ', ctx.topCategories.map((c) => `${c.name} (${formatBRL(c.amount)})`).join(', '), '.')
        : p('Ainda não há gastos lançados neste mês.'),
      ctx.bucketsOverPlan.length
        ? p('Acima do plano: ', b(ctx.bucketsOverPlan.join(', ')), '. Cortar aí libera dinheiro para investir.')
        : p('Seus potes de gasto estão dentro do planejado.'),
    ],
  },

  metas: {
    keywords: ['meta', 'metas', 'objetivo', 'sonho', 'viagem', 'carro', 'casa propria', 'comprar'],
    run: (ctx) => [
      ctx.goalCount
        ? p(`Você tem ${ctx.goalCount} ${ctx.goalCount === 1 ? 'meta' : 'metas'}. Para cada uma, o app calcula quanto guardar por mês até o prazo.`)
        : p('Você ainda não criou metas. Crie uma com valor e prazo e eu mostro quanto guardar por mês.'),
      p('Onde investir depende do prazo: ', b('até 2 anos'), ', renda fixa com liquidez ou vencimento próximo; ', b('3 a 5 anos'), ', Tesouro IPCA+/prefixado ou LCI/LCA; ', b('5 anos ou mais'), ', pode incluir renda variável conforme seu perfil.'),
    ],
  },
}

/** Escolhe a resposta pelo assunto com mais palavras-chave encontradas. */
export function detectIntent(question) {
  const text = normalize(question)
  let best = null
  let bestScore = 0
  for (const [id, h] of Object.entries(handlers)) {
    // Saudações e frases genéricas só vencem quando nenhum assunto específico aparece.
    const score = h.keywords.reduce((a, k) => (text.includes(` ${k}`) ? a + k.split(' ').length : a), 0) * (h.weak ? 0.25 : 1)
    if (score > bestScore) {
      best = id
      bestScore = score
    }
  }
  return best
}

export function answer(question, ctx) {
  const intent = detectIntent(question)
  if (intent) return handlers[intent].run(ctx)
  return [p('Não entendi essa. Tente perguntar de outro jeito, por exemplo sobre quanto investir, reserva, Tesouro, CDB, ações, imposto ou suas metas.')]
}

export function greeting(ctx) {
  const hello = `Olá${ctx.displayName ? `, ${ctx.displayName}` : ''}! Pergunte o que quiser sobre seus investimentos. Respondo com os seus números.`
  if (!ctx.profile) return [p(hello), p('Dica: faça o questionário de perfil para eu sugerir onde investir.')]
  return [p(hello)]
}
