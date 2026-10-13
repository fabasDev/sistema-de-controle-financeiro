import type { Bucket, BucketKind, Category, DataState, Goal, PresetId, Settings } from '@/store/types'

export interface PresetBucket extends Omit<Bucket, 'id'> {
  /** Categorias padrão que este pote "puxa" para si ao aplicar a regra. */
  claims?: string[]
}

export interface RulePreset {
  id: Exclude<PresetId, 'custom'>
  name: string
  short: string
  description: string
  buckets: PresetBucket[]
}

export const PRESETS: RulePreset[] = [
  {
    id: '50-30-20',
    name: 'Regra 50/30/20',
    short: '50/30/20',
    description: 'A clássica: metade para o essencial, 30% para o estilo de vida e 20% para o futuro.',
    buckets: [
      { name: 'Necessidades', percent: 50, kind: 'necessidades', description: 'Moradia, contas, mercado, transporte e saúde' },
      { name: 'Desejos', percent: 30, kind: 'desejos', description: 'Lazer, restaurantes, compras e assinaturas' },
      { name: 'Futuro', percent: 20, kind: 'futuro', description: 'Reserva de emergência, investimentos e metas' },
    ],
  },
  {
    id: '70-20-10',
    name: 'Regra 70/20/10',
    short: '70/20/10',
    description: '70% para o custo de vida, 20% para investir e 10% para sonhos, doações ou quitar dívidas.',
    buckets: [
      { name: 'Custo de vida', percent: 70, kind: 'necessidades', description: 'Todos os gastos do dia a dia' },
      { name: 'Futuro', percent: 20, kind: 'futuro', description: 'Reserva de emergência e investimentos' },
      { name: 'Sonhos e doações', percent: 10, kind: 'outros', description: 'Objetivos, doações ou quitar dívidas', claims: ['cat-metas', 'cat-presentes'] },
    ],
  },
  {
    id: '6-potes',
    name: 'Método dos 6 potes',
    short: '6 potes',
    description: 'Divide a renda em seis potes com objetivos claros, do essencial à liberdade financeira.',
    buckets: [
      { name: 'Necessidades', percent: 55, kind: 'necessidades', description: 'Custos essenciais do mês' },
      { name: 'Liberdade financeira', percent: 10, kind: 'futuro', description: 'Investimentos para gerar renda passiva', claims: ['cat-investimentos'] },
      { name: 'Longo prazo', percent: 10, kind: 'futuro', description: 'Reserva de emergência e grandes compras', claims: ['cat-reserva', 'cat-metas'] },
      { name: 'Educação', percent: 10, kind: 'outros', description: 'Cursos, livros e desenvolvimento', claims: ['cat-educacao'] },
      { name: 'Diversão', percent: 10, kind: 'desejos', description: 'Lazer, restaurantes e mimos sem culpa' },
      { name: 'Doações', percent: 5, kind: 'outros', description: 'Ajudar pessoas e causas', claims: ['cat-presentes'] },
    ],
  },
  {
    id: '80-20',
    name: 'Pague-se primeiro',
    short: '80/20',
    description: 'O mais simples: separe 20% para o futuro assim que receber e viva com os outros 80%.',
    buckets: [
      { name: 'Gastos do mês', percent: 80, kind: 'necessidades', description: 'Tudo o que você consome no mês' },
      { name: 'Futuro', percent: 20, kind: 'futuro', description: 'Reserva de emergência e investimentos' },
    ],
  },
]

export const MAX_BUCKETS = 6

export const KIND_LABEL: Record<BucketKind, string> = {
  necessidades: 'Necessidade',
  desejos: 'Desejo',
  futuro: 'Futuro (guardar/investir)',
  outros: 'Outro',
}

/** Para onde vai uma categoria quando a regra não tem um pote do mesmo tipo. */
const KIND_FALLBACK: Record<BucketKind, BucketKind[]> = {
  necessidades: ['necessidades', 'desejos', 'outros', 'futuro'],
  desejos: ['desejos', 'necessidades', 'outros', 'futuro'],
  futuro: ['futuro', 'outros', 'necessidades', 'desejos'],
  outros: ['outros', 'desejos', 'necessidades', 'futuro'],
}

export function bucketForCategory(
  category: Pick<Category, 'id' | 'kind'>,
  buckets: (Bucket & { claims?: string[] })[],
): string {
  const claimed = buckets.find((b) => b.claims?.includes(category.id))
  if (claimed) return claimed.id
  for (const kind of KIND_FALLBACK[category.kind]) {
    const match = buckets.find((b) => b.kind === kind)
    if (match) return match.id
  }
  return buckets[0].id
}

export function bucketsFromPreset(id: RulePreset['id']): (Bucket & { claims?: string[] })[] {
  const preset = PRESETS.find((p) => p.id === id) ?? PRESETS[0]
  return preset.buckets.map((b, i) => ({ ...b, id: `bucket-${id}-${i}` }))
}

/** Aplica uma regra pronta, remapeando cada categoria para o pote mais adequado. */
export function applyPresetTo(state: Pick<DataState, 'categories'>, id: RulePreset['id']) {
  const withClaims = bucketsFromPreset(id)
  const categories = state.categories.map((c) => ({ ...c, bucketId: bucketForCategory(c, withClaims) }))
  const buckets: Bucket[] = withClaims.map(({ claims: _claims, ...b }) => b)
  return { buckets, categories }
}

export const RESERVE_GOAL_ID = 'goal-reserva'
export const INVEST_GOAL_ID = 'goal-investimentos'

const RAW_CATEGORIES: Omit<Category, 'bucketId'>[] = [
  { id: 'cat-moradia', name: 'Moradia', icon: 'House', kind: 'necessidades' },
  { id: 'cat-contas', name: 'Contas da casa', icon: 'Zap', kind: 'necessidades' },
  { id: 'cat-mercado', name: 'Mercado', icon: 'ShoppingCart', kind: 'necessidades' },
  { id: 'cat-transporte', name: 'Transporte', icon: 'Car', kind: 'necessidades' },
  { id: 'cat-saude', name: 'Saúde', icon: 'HeartPulse', kind: 'necessidades' },
  { id: 'cat-educacao', name: 'Educação', icon: 'GraduationCap', kind: 'necessidades' },
  { id: 'cat-restaurantes', name: 'Restaurantes e delivery', icon: 'UtensilsCrossed', kind: 'desejos' },
  { id: 'cat-lazer', name: 'Lazer', icon: 'Gamepad2', kind: 'desejos' },
  { id: 'cat-assinaturas', name: 'Assinaturas', icon: 'Tv', kind: 'desejos' },
  { id: 'cat-compras', name: 'Compras', icon: 'ShoppingBag', kind: 'desejos' },
  { id: 'cat-viagens', name: 'Viagens', icon: 'Plane', kind: 'desejos' },
  { id: 'cat-cuidados', name: 'Cuidados pessoais', icon: 'Sparkles', kind: 'desejos' },
  { id: 'cat-reserva', name: 'Reserva de emergência', icon: 'ShieldCheck', kind: 'futuro', goalId: RESERVE_GOAL_ID },
  { id: 'cat-investimentos', name: 'Investimentos', icon: 'TrendingUp', kind: 'futuro', goalId: INVEST_GOAL_ID },
  { id: 'cat-metas', name: 'Metas e sonhos', icon: 'Target', kind: 'futuro' },
  { id: 'cat-presentes', name: 'Presentes e doações', icon: 'Gift', kind: 'outros' },
  { id: 'cat-outros', name: 'Outros', icon: 'Ellipsis', kind: 'outros' },
]

export const DEFAULT_GOALS: Goal[] = [
  { id: RESERVE_GOAL_ID, name: 'Reserva de emergência', icon: 'ShieldCheck', kind: 'reserva', target: 0, initial: 0 },
  { id: INVEST_GOAL_ID, name: 'Carteira de investimentos', icon: 'TrendingUp', kind: 'investimento', target: 0, initial: 0 },
]

export const DEFAULT_SETTINGS: Settings = {
  userName: '',
  presetId: '50-30-20',
  reserveMonths: 6,
  reserveBase: 'necessidades',
  reserveManualCost: 0,
  reservePriority: 70,
  sim: { initial: 0, monthly: 500, annualRate: 10, years: 10, inflation: 4.5, yearlyIncrease: 0, target: 0 },
  onboarded: false,
  autoLockMinutes: 15,
}

export function createInitialData(): DataState {
  const { buckets, categories } = applyPresetTo(
    { categories: RAW_CATEGORIES.map((c) => ({ ...c, bucketId: '' })) },
    '50-30-20',
  )
  return {
    buckets,
    categories,
    transactions: [],
    recurring: [],
    goals: DEFAULT_GOALS.map((g) => ({ ...g })),
    settings: structuredClone(DEFAULT_SETTINGS),
    profile: undefined,
  }
}

/** Sugestões de contas fixas usadas no assistente inicial. */
export const COMMON_BILLS: { description: string; categoryId: string; day: number }[] = [
  { description: 'Aluguel / financiamento', categoryId: 'cat-moradia', day: 10 },
  { description: 'Condomínio', categoryId: 'cat-moradia', day: 10 },
  { description: 'Energia elétrica', categoryId: 'cat-contas', day: 15 },
  { description: 'Água', categoryId: 'cat-contas', day: 15 },
  { description: 'Internet', categoryId: 'cat-contas', day: 20 },
  { description: 'Celular', categoryId: 'cat-contas', day: 20 },
  { description: 'Plano de saúde', categoryId: 'cat-saude', day: 5 },
  { description: 'Streaming', categoryId: 'cat-assinaturas', day: 12 },
]
