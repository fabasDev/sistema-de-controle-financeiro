/**
 * Questionário de perfil de investidor (suitability). Porte de QUIZ e scoreProfile
 * (legacy-web/src/lib/advisor.ts). Roda só no navegador: as respostas não são enviadas,
 * só o perfil e a pontuação.
 */

export const QUIZ = [
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

export const MAX_SCORE = QUIZ.reduce((a, q) => a + Math.max(...q.options.map((o) => o.points)), 0)

/** Nomes do perfil no servidor (enum RiskProfile). */
const SERVER_PROFILE = { conservador: 'CONSERVATIVE', moderado: 'MODERATE', arrojado: 'AGGRESSIVE' }

/**
 * Calcula o perfil. Algumas respostas limitam o perfil, independentemente da pontuação:
 * prazo curto ou vender tudo na queda → conservador; prazo de 1 a 3 anos → no máximo moderado.
 * {@code answers}: id da pergunta → índice da opção escolhida.
 */
export function scoreProfile(answers) {
  const points = (id) => QUIZ.find((q) => q.id === id).options[answers[id]]?.points ?? 0
  const score = QUIZ.reduce((a, q) => a + points(q.id), 0)
  let profile = score <= 9 ? 'conservador' : score <= 17 ? 'moderado' : 'arrojado'
  if (answers.prazo === 0 || answers.queda === 0) profile = 'conservador'
  else if (answers.prazo === 1 && profile === 'arrojado') profile = 'moderado'
  return { score, profile, serverProfile: SERVER_PROFILE[profile] }
}
