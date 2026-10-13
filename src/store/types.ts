import type { MonthKey } from '@/lib/month'

/** Natureza de um gasto — usada para remapear categorias ao trocar de regra. */
export type BucketKind = 'necessidades' | 'desejos' | 'futuro' | 'outros'

export type PresetId = '50-30-20' | '70-20-10' | '6-potes' | '80-20' | 'custom'

/** Um "pote" da regra de distribuição da renda (ex.: Necessidades 50%). */
export interface Bucket {
  id: string
  name: string
  percent: number
  kind: BucketKind
  description?: string
}

export interface Category {
  id: string
  name: string
  icon: string
  bucketId: string
  kind: BucketKind
  /** Limite mensal opcional. */
  budget?: number
  /** Meta que recebe automaticamente os lançamentos desta categoria. */
  goalId?: string
  archived?: boolean
}

export type TxType = 'receita' | 'despesa'

export interface Transaction {
  id: string
  type: TxType
  month: MonthKey
  /** YYYY-MM-DD */
  date: string
  description: string
  amount: number
  categoryId?: string
  goalId?: string
  recurringId?: string
  /** Pago (despesa) ou recebido (receita). */
  paid: boolean
}

export interface Recurring {
  id: string
  type: TxType
  description: string
  amount: number
  categoryId?: string
  goalId?: string
  /** Dia do mês (1–31). */
  day: number
  startMonth: MonthKey
  /** Último mês (inclusive) — usado para parcelamentos. */
  endMonth?: MonthKey
  active: boolean
  /** Meses em que o lançamento foi excluído manualmente. */
  skipped: MonthKey[]
}

export type GoalKind = 'reserva' | 'investimento' | 'meta'

export interface Goal {
  id: string
  name: string
  icon: string
  kind: GoalKind
  /** Valor-alvo (0 = sem alvo). Ignorado na reserva, que é calculada. */
  target: number
  deadline?: MonthKey
  /** Saldo que já existia antes dos lançamentos registrados no app. */
  initial: number
}

export type ReserveBase = 'necessidades' | 'gastos' | 'manual'

export interface SimSettings {
  initial: number
  monthly: number
  annualRate: number
  years: number
  inflation: number
  yearlyIncrease: number
  target: number
}

export interface Settings {
  userName: string
  presetId: PresetId
  reserveMonths: number
  reserveBase: ReserveBase
  reserveManualCost: number
  /** % do valor "Futuro" direcionado à reserva enquanto ela não estiver completa. */
  reservePriority: number
  sim: SimSettings
  onboarded: boolean
  lastExport?: string
  /** Minutos sem uso até a sessão ser bloqueada (0 = nunca). */
  autoLockMinutes: number
}

export type ProfileId = 'conservador' | 'moderado' | 'arrojado'

/** Resultado do questionário de perfil de investidor. */
export interface InvestorProfile {
  /** Índice da opção escolhida em cada pergunta. */
  answers: Record<string, number>
  score: number
  profile: ProfileId
  /** ISO — o perfil deve ser refeito a cada 2 anos. */
  date: string
  /** Percentuais ajustados pelo usuário por classe de ativo. */
  custom?: Partial<Record<'posfixado' | 'inflacao' | 'prefixado' | 'isentos' | 'fiis' | 'acoes' | 'exterior', number>>
}

export interface DataState {
  buckets: Bucket[]
  categories: Category[]
  transactions: Transaction[]
  recurring: Recurring[]
  goals: Goal[]
  settings: Settings
  profile?: InvestorProfile
}
