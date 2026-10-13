import { RESERVE_GOAL_ID } from '@/lib/presets'
import { diffMonths, monthRange, type MonthKey } from '@/lib/month'
import type { Bucket, Category, DataState, Goal, Transaction } from '@/store/types'

const sum = (values: number[]) => values.reduce((a, b) => a + b, 0)
const round2 = (v: number) => Math.round(v * 100) / 100

export function categoryMap(categories: Category[]) {
  return new Map(categories.map((c) => [c.id, c]))
}

/** Despesas em categorias do tipo "futuro" são dinheiro guardado, não consumo. */
export function isSaving(tx: Transaction, cats: Map<string, Category>) {
  return tx.type === 'despesa' && cats.get(tx.categoryId ?? '')?.kind === 'futuro'
}

export interface BucketSummary {
  bucket: Bucket
  index: number
  planned: number
  actual: number
}

export interface CategorySummary {
  category: Category
  actual: number
  count: number
}

export interface MonthSummary {
  month: MonthKey
  income: number
  /** Consumo: despesas fora de "futuro". */
  spent: number
  /** Aportes: despesas em categorias "futuro". */
  saved: number
  /** Todas as saídas (consumo + aportes). */
  outflow: number
  /** O que sobra depois de gastos e aportes. */
  balance: number
  savingsRate: number
  unpaid: number
  pendingIncome: number
  /** Renda usada como base do planejamento (renda do mês ou receitas fixas). */
  planIncome: number
  buckets: BucketSummary[]
  categories: CategorySummary[]
  hasData: boolean
}

/** Soma das receitas fixas ativas no mês — base do plano quando o mês ainda não tem renda. */
export function recurringIncome(state: Pick<DataState, 'recurring'>, month: MonthKey) {
  return sum(
    state.recurring
      .filter(
        (r) =>
          r.type === 'receita' &&
          r.active &&
          r.startMonth <= month &&
          (!r.endMonth || month <= r.endMonth),
      )
      .map((r) => r.amount),
  )
}

export function summarizeMonth(state: DataState, month: MonthKey): MonthSummary {
  const cats = categoryMap(state.categories)
  const txs = state.transactions.filter((t) => t.month === month)
  const incomes = txs.filter((t) => t.type === 'receita')
  const expenses = txs.filter((t) => t.type === 'despesa')

  const income = sum(incomes.map((t) => t.amount))
  const saved = sum(expenses.filter((t) => isSaving(t, cats)).map((t) => t.amount))
  const outflow = sum(expenses.map((t) => t.amount))
  const spent = outflow - saved
  const planIncome = income > 0 ? income : recurringIncome(state, month)

  const byCategory = new Map<string, CategorySummary>()
  for (const t of expenses) {
    const category = cats.get(t.categoryId ?? '') ?? cats.get('cat-outros')
    if (!category) continue
    const entry = byCategory.get(category.id) ?? { category, actual: 0, count: 0 }
    entry.actual += t.amount
    entry.count += 1
    byCategory.set(category.id, entry)
  }

  const buckets = state.buckets.map((bucket, index) => ({
    bucket,
    index,
    planned: round2((planIncome * bucket.percent) / 100),
    actual: round2(
      sum([...byCategory.values()].filter((c) => c.category.bucketId === bucket.id).map((c) => c.actual)),
    ),
  }))

  return {
    month,
    income: round2(income),
    spent: round2(spent),
    saved: round2(saved),
    outflow: round2(outflow),
    balance: round2(income - outflow),
    savingsRate: income > 0 ? saved / income : 0,
    unpaid: round2(sum(expenses.filter((t) => !t.paid).map((t) => t.amount))),
    pendingIncome: round2(sum(incomes.filter((t) => !t.paid).map((t) => t.amount))),
    planIncome: round2(planIncome),
    buckets,
    categories: [...byCategory.values()].sort((a, b) => b.actual - a.actual),
    hasData: txs.length > 0,
  }
}

/** Saldo de uma meta: saldo inicial + aportes pagos vinculados a ela. */
export function goalBalance(goal: Goal, transactions: Transaction[]) {
  return round2(
    goal.initial +
      sum(transactions.filter((t) => t.goalId === goal.id && t.type === 'despesa' && t.paid).map((t) => t.amount)),
  )
}

export interface ReserveInfo {
  monthlyCost: number
  target: number
  balance: number
  gap: number
  progress: number
  /** Quantos meses de custo a reserva atual cobre. */
  monthsCovered: number
  basis: string
}

/**
 * Meta da reserva = custo mensal × meses desejados.
 * O custo mensal vem da média dos últimos 3 meses com gastos (ou do plano, se não houver histórico).
 */
export function reserveInfo(state: DataState, month: MonthKey): ReserveInfo {
  const { reserveBase, reserveMonths, reserveManualCost } = state.settings
  const cats = categoryMap(state.categories)
  const goal = state.goals.find((g) => g.id === RESERVE_GOAL_ID) ?? state.goals.find((g) => g.kind === 'reserva')
  const balance = goal ? goalBalance(goal, state.transactions) : 0

  let monthlyCost = 0
  let basis = ''
  if (reserveBase === 'manual') {
    monthlyCost = reserveManualCost
    basis = 'valor definido por você'
  } else {
    const wanted = (t: Transaction) => {
      const kind = cats.get(t.categoryId ?? '')?.kind
      return reserveBase === 'necessidades' ? kind === 'necessidades' : kind !== 'futuro'
    }
    const totals = monthRange(month, 3)
      .map((m) => sum(state.transactions.filter((t) => t.month === m && t.type === 'despesa' && wanted(t)).map((t) => t.amount)))
      .filter((v) => v > 0)
    const label = reserveBase === 'necessidades' ? 'gastos essenciais' : 'gastos totais'
    if (totals.length) {
      monthlyCost = sum(totals) / totals.length
      basis = `média de ${label} dos últimos ${totals.length === 1 ? 'mês' : `${totals.length} meses`}`
    } else {
      const summary = summarizeMonth(state, month)
      const share = sum(
        summary.buckets
          .filter((b) => (reserveBase === 'necessidades' ? b.bucket.kind === 'necessidades' : b.bucket.kind !== 'futuro'))
          .map((b) => b.planned),
      )
      monthlyCost = share
      basis = `${label} planejados (ainda sem histórico)`
    }
  }

  monthlyCost = round2(monthlyCost)
  const target = round2(monthlyCost * reserveMonths)
  const gap = round2(Math.max(0, target - balance))
  return {
    monthlyCost,
    target,
    balance,
    gap,
    progress: target > 0 ? Math.min(1, balance / target) : 0,
    monthsCovered: monthlyCost > 0 ? balance / monthlyCost : 0,
    basis,
  }
}

export interface Recommendation {
  income: number
  /** Quanto a regra manda separar para o futuro neste mês. */
  futurePlanned: number
  toReserve: number
  toInvest: number
  /** Quanto já foi guardado neste mês. */
  savedThisMonth: number
  remainingToSave: number
  monthsToReserve: number | null
  reserve: ReserveInfo
}

export function recommendation(state: DataState, month: MonthKey): Recommendation {
  const summary = summarizeMonth(state, month)
  const reserve = reserveInfo(state, month)
  const futurePlanned = round2(sum(summary.buckets.filter((b) => b.bucket.kind === 'futuro').map((b) => b.planned)))
  const priority = state.settings.reservePriority / 100
  const toReserve = reserve.gap > 0 ? round2(Math.min(reserve.gap, futurePlanned * priority)) : 0
  const toInvest = round2(futurePlanned - toReserve)
  return {
    income: summary.planIncome,
    futurePlanned,
    toReserve,
    toInvest,
    savedThisMonth: summary.saved,
    remainingToSave: round2(Math.max(0, futurePlanned - summary.saved)),
    monthsToReserve: reserve.gap > 0 && toReserve > 0 ? Math.ceil(reserve.gap / toReserve) : null,
    reserve,
  }
}

export interface GoalPlan {
  balance: number
  remaining: number
  progress: number
  monthsLeft: number | null
  monthlyNeeded: number | null
  done: boolean
}

export function goalPlan(goal: Goal, transactions: Transaction[], month: MonthKey): GoalPlan {
  const balance = goalBalance(goal, transactions)
  const remaining = round2(Math.max(0, goal.target - balance))
  const monthsLeft = goal.deadline ? Math.max(1, diffMonths(month, goal.deadline) + 1) : null
  return {
    balance,
    remaining,
    progress: goal.target > 0 ? Math.min(1, balance / goal.target) : 0,
    monthsLeft,
    monthlyNeeded: monthsLeft && goal.target > 0 ? round2(remaining / monthsLeft) : null,
    done: goal.target > 0 && balance >= goal.target,
  }
}

/** Ajusta os percentuais para somarem exatamente 100, mantendo a proporção. */
export function normalizePercents(buckets: Bucket[]): Bucket[] {
  const total = sum(buckets.map((b) => b.percent))
  if (total <= 0) {
    const even = Math.floor(100 / buckets.length)
    return buckets.map((b, i) => ({ ...b, percent: i === 0 ? 100 - even * (buckets.length - 1) : even }))
  }
  const scaled = buckets.map((b) => ({ ...b, percent: Math.round((b.percent / total) * 100) }))
  const diff = 100 - sum(scaled.map((b) => b.percent))
  const largest = scaled.reduce((best, b, i) => (b.percent > scaled[best].percent ? i : best), 0)
  scaled[largest] = { ...scaled[largest], percent: scaled[largest].percent + diff }
  return scaled
}

// ---------- Simulador de juros compostos ----------

export interface SimParams {
  initial: number
  monthly: number
  /** % ao ano */
  annualRate: number
  years: number
  /** % ao ano */
  inflation: number
  /** % de aumento do aporte a cada 12 meses */
  yearlyIncrease: number
}

export interface SimPoint {
  month: number
  invested: number
  total: number
  /** Total em valores de hoje (descontada a inflação). */
  real: number
}

export const monthlyRate = (annualPercent: number) => Math.pow(1 + annualPercent / 100, 1 / 12) - 1

/** Aportes no fim de cada mês, juros compostos mensais equivalentes à taxa anual. */
export function simulate(p: SimParams): SimPoint[] {
  const r = monthlyRate(p.annualRate)
  const inf = monthlyRate(p.inflation)
  const months = Math.max(1, Math.round(p.years * 12))
  let total = p.initial
  let invested = p.initial
  let contribution = p.monthly
  const points: SimPoint[] = [{ month: 0, invested, total, real: total }]
  for (let m = 1; m <= months; m++) {
    if (m > 1 && (m - 1) % 12 === 0) contribution *= 1 + p.yearlyIncrease / 100
    total = total * (1 + r) + contribution
    invested += contribution
    points.push({ month: m, invested: round2(invested), total: round2(total), real: round2(total / Math.pow(1 + inf, m)) })
  }
  return points
}

/** Meses até atingir `target` (limite de 100 anos). */
export function monthsToTarget(p: Omit<SimParams, 'years' | 'inflation'>, target: number): number | null {
  if (p.initial >= target) return 0
  const r = monthlyRate(p.annualRate)
  let total = p.initial
  let contribution = p.monthly
  for (let m = 1; m <= 1200; m++) {
    if (m > 1 && (m - 1) % 12 === 0) contribution *= 1 + p.yearlyIncrease / 100
    total = total * (1 + r) + contribution
    if (total >= target) return m
  }
  return null
}

/** Aporte mensal constante necessário para chegar a `target` em `months` meses. */
export function requiredMonthly(initial: number, annualRate: number, months: number, target: number) {
  const r = monthlyRate(annualRate)
  const grownInitial = initial * Math.pow(1 + r, months)
  if (grownInitial >= target) return 0
  // Arredonda para cima: é o mínimo que garante chegar ao alvo.
  const ceil2 = (v: number) => Math.ceil(v * 100) / 100
  if (r === 0) return ceil2((target - initial) / months)
  return ceil2(((target - grownInitial) * r) / (Math.pow(1 + r, months) - 1))
}

// ---------- Histórico ----------

export interface HistoryRow {
  month: MonthKey
  income: number
  spent: number
  saved: number
  balance: number
  savingsRate: number
}

export function history(state: DataState, end: MonthKey, count: number): HistoryRow[] {
  return monthRange(end, count).map((month) => {
    const s = summarizeMonth(state, month)
    return { month, income: s.income, spent: s.spent, saved: s.saved, balance: s.balance, savingsRate: s.savingsRate }
  })
}

/** Gasto por categoria em cada mês do intervalo (somente categorias com algum gasto). */
export function categoryHistory(state: DataState, end: MonthKey, count: number) {
  const months = monthRange(end, count)
  const cats = categoryMap(state.categories)
  const rows = new Map<string, { category: Category; values: number[]; total: number }>()
  months.forEach((month, i) => {
    for (const t of state.transactions) {
      if (t.month !== month || t.type !== 'despesa') continue
      const category = cats.get(t.categoryId ?? '') ?? cats.get('cat-outros')
      if (!category) continue
      const row = rows.get(category.id) ?? { category, values: months.map(() => 0), total: 0 }
      row.values[i] += t.amount
      row.total += t.amount
      rows.set(category.id, row)
    }
  })
  return { months, rows: [...rows.values()].sort((a, b) => b.total - a.total) }
}
