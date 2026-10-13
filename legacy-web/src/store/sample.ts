import { addMonths, currentMonth, dateInMonth, monthRange, type MonthKey } from '@/lib/month'
import { createInitialData, INVEST_GOAL_ID, RESERVE_GOAL_ID } from '@/lib/presets'
import type { DataState, Recurring, Transaction } from '@/store/types'

/** Gerador pseudoaleatório com semente, para o exemplo ser sempre igual. */
function rng(seed: number) {
  return () => {
    seed = (seed * 1664525 + 1013904223) % 4294967296
    return seed / 4294967296
  }
}

/** Seis meses de dados fictícios para explorar o app. */
export function createSampleData(): DataState {
  const base = createInitialData()
  const rand = rng(42)
  const between = (min: number, max: number) => Math.round((min + rand() * (max - min)) * 100) / 100
  const today = new Date().getDate()
  const now = currentMonth()
  const months = monthRange(now, 6)
  const start = months[0]
  let n = 0
  const id = (p: string) => `${p}-${++n}`

  const recurring: Recurring[] = [
    { id: 'rec-salario', type: 'receita', description: 'Salário', amount: 6500, day: 5, startMonth: start, active: true, skipped: [] },
    { id: 'rec-aluguel', type: 'despesa', description: 'Aluguel', amount: 1650, categoryId: 'cat-moradia', day: 10, startMonth: start, active: true, skipped: [] },
    { id: 'rec-condominio', type: 'despesa', description: 'Condomínio', amount: 420, categoryId: 'cat-moradia', day: 10, startMonth: start, active: true, skipped: [] },
    { id: 'rec-internet', type: 'despesa', description: 'Internet', amount: 119.9, categoryId: 'cat-contas', day: 18, startMonth: start, active: true, skipped: [] },
    { id: 'rec-celular', type: 'despesa', description: 'Celular', amount: 59.9, categoryId: 'cat-contas', day: 18, startMonth: start, active: true, skipped: [] },
    { id: 'rec-academia', type: 'despesa', description: 'Academia', amount: 109.9, categoryId: 'cat-saude', day: 8, startMonth: start, active: true, skipped: [] },
    { id: 'rec-streaming', type: 'despesa', description: 'Streaming de vídeo e música', amount: 76.8, categoryId: 'cat-assinaturas', day: 12, startMonth: start, active: true, skipped: [] },
    { id: 'rec-reserva', type: 'despesa', description: 'Aporte na reserva', amount: 600, categoryId: 'cat-reserva', goalId: RESERVE_GOAL_ID, day: 6, startMonth: start, active: true, skipped: [] },
    { id: 'rec-invest', type: 'despesa', description: 'Aporte mensal (Tesouro/CDB)', amount: 400, categoryId: 'cat-investimentos', goalId: INVEST_GOAL_ID, day: 6, startMonth: start, active: true, skipped: [] },
    { id: 'rec-curso', type: 'despesa', description: 'Curso de inglês (parcela)', amount: 289, categoryId: 'cat-educacao', day: 15, startMonth: addMonths(start, 2), endMonth: addMonths(start, 9), active: true, skipped: [] },
  ]

  const goals = [
    ...base.goals.map((g) => (g.id === RESERVE_GOAL_ID ? { ...g, initial: 4800 } : g.id === INVEST_GOAL_ID ? { ...g, initial: 7200 } : g)),
    { id: 'goal-viagem', name: 'Viagem de férias', icon: 'Plane', kind: 'meta' as const, target: 9000, deadline: addMonths(now, 9), initial: 1500 },
    { id: 'goal-notebook', name: 'Notebook novo', icon: 'Laptop', kind: 'meta' as const, target: 6000, deadline: addMonths(now, 5), initial: 2200 },
  ]

  const transactions: Transaction[] = []
  const isPast = (month: MonthKey, day: number) => month < now || day <= today

  for (const month of months) {
    for (const r of recurring) {
      if (month < r.startMonth || (r.endMonth && month > r.endMonth)) continue
      transactions.push({
        id: id('tx'),
        type: r.type,
        month,
        date: dateInMonth(month, r.day),
        description: r.description,
        amount: r.amount,
        categoryId: r.categoryId,
        goalId: r.goalId,
        recurringId: r.id,
        paid: isPast(month, r.day),
      })
    }
    const add = (day: number, description: string, categoryId: string, amount: number, extra: Partial<Transaction> = {}) => {
      if (month === now && day > today) return
      transactions.push({
        id: id('tx'),
        type: 'despesa',
        month,
        date: dateInMonth(month, day),
        description,
        amount,
        categoryId,
        paid: true,
        ...extra,
      })
    }
    add(3, 'Supermercado', 'cat-mercado', between(380, 520))
    add(11, 'Feira e hortifruti', 'cat-mercado', between(90, 160))
    add(17, 'Supermercado', 'cat-mercado', between(300, 460))
    add(25, 'Padaria e açougue', 'cat-mercado', between(120, 210))
    add(14, 'Energia elétrica', 'cat-contas', between(150, 230))
    add(14, 'Água', 'cat-contas', between(70, 110))
    add(7, 'Combustível', 'cat-transporte', between(220, 320))
    add(21, 'Combustível', 'cat-transporte', between(180, 280))
    add(9, 'Aplicativo de transporte', 'cat-transporte', between(40, 90))
    add(13, 'Farmácia', 'cat-saude', between(45, 140))
    add(6, 'Restaurante', 'cat-restaurantes', between(90, 180))
    add(16, 'Delivery', 'cat-restaurantes', between(50, 95))
    add(27, 'Delivery', 'cat-restaurantes', between(45, 90))
    add(19, 'Cinema', 'cat-lazer', between(60, 120))
    add(23, 'Bar com amigos', 'cat-lazer', between(80, 190))
    add(20, 'Cabeleireiro', 'cat-cuidados', between(50, 90))
    if (rand() > 0.4) add(24, 'Roupas', 'cat-compras', between(150, 420))
    if (rand() > 0.6) add(26, 'Presente de aniversário', 'cat-presentes', between(80, 200))
    if (rand() > 0.5) add(28, 'Guardar para a viagem', 'cat-metas', 350, { goalId: 'goal-viagem' })
    if (rand() > 0.5) add(28, 'Guardar para o notebook', 'cat-metas', 300, { goalId: 'goal-notebook' })
    if (rand() > 0.55) {
      transactions.push({
        id: id('tx'),
        type: 'receita',
        month,
        date: dateInMonth(month, 22),
        description: 'Freelance',
        amount: between(600, 1400),
        paid: month < now,
      })
    }
  }

  return {
    ...base,
    recurring,
    goals,
    transactions,
    settings: { ...base.settings, userName: 'Visitante', onboarded: true },
  }
}
