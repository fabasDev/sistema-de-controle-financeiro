import { create } from 'zustand'
import { normalizePercents } from '@/lib/finance'
import { addMonths, currentMonth, dateInMonth, type MonthKey } from '@/lib/month'
import {
  applyPresetTo,
  createInitialData,
  MAX_BUCKETS,
  RESERVE_GOAL_ID,
  type RulePreset,
} from '@/lib/presets'
import type {
  Bucket,
  Category,
  DataState,
  Goal,
  InvestorProfile,
  Recurring,
  Settings,
  Transaction,
} from '@/store/types'

export const uid = () => crypto.randomUUID()

type NewTx = Omit<Transaction, 'id'>
type NewRecurring = Omit<Recurring, 'id' | 'skipped' | 'active'> & Partial<Pick<Recurring, 'active'>>

export interface OnboardingPayload {
  userName: string
  presetId: RulePreset['id']
  income: { description: string; amount: number; day: number }
  bills: { description: string; amount: number; categoryId: string; day: number }[]
  reserveBalance: number
  reserveMonths: number
}

interface Actions {
  /** Cria no mês os lançamentos das contas recorrentes que ainda não existem. */
  ensureMonth: (month: MonthKey) => void
  addTransaction: (tx: NewTx) => string
  updateTransaction: (id: string, patch: Partial<Transaction>) => void
  removeTransaction: (id: string) => Transaction | undefined
  restoreTransaction: (tx: Transaction) => void
  togglePaid: (id: string) => void

  addRecurring: (r: NewRecurring) => string
  updateRecurring: (id: string, patch: Partial<Recurring>) => void
  removeRecurring: (id: string) => void

  applyPreset: (id: RulePreset['id']) => void
  updateBucket: (id: string, patch: Partial<Bucket>) => void
  addBucket: () => void
  removeBucket: (id: string) => void
  normalizeBuckets: () => void

  addCategory: (c: Omit<Category, 'id'>) => string
  updateCategory: (id: string, patch: Partial<Category>) => void
  /** Exclui se não houver uso; caso contrário arquiva. Retorna o que aconteceu. */
  removeCategory: (id: string) => 'removed' | 'archived'

  addGoal: (g: Omit<Goal, 'id'>) => string
  updateGoal: (id: string, patch: Partial<Goal>) => void
  removeGoal: (id: string) => void

  updateSettings: (patch: Partial<Settings>) => void
  completeOnboarding: (p: OnboardingPayload) => void
  replaceData: (data: DataState) => void
  setProfile: (profile: InvestorProfile | undefined) => void
  resetAll: () => void
}

export type Store = DataState & Actions

/**
 * Dados financeiros em memória. A persistência é feita de forma criptografada
 * pela sessão (`store/session.ts`) — nada é salvo em texto puro.
 */
export const useStore = create<Store>()((set, get) => ({
  ...createInitialData(),

  ensureMonth: (month) => {
    const { recurring, transactions } = get()
    const toAdd: Transaction[] = []
    for (const r of recurring) {
      if (!r.active || month < r.startMonth || (r.endMonth && month > r.endMonth)) continue
      if (r.skipped.includes(month)) continue
      if (transactions.some((t) => t.recurringId === r.id && t.month === month)) continue
      toAdd.push({
        id: uid(),
        type: r.type,
        month,
        date: dateInMonth(month, r.day),
        description: r.description,
        amount: r.amount,
        categoryId: r.categoryId,
        goalId: r.goalId,
        recurringId: r.id,
        paid: false,
      })
    }
    if (toAdd.length) set({ transactions: [...transactions, ...toAdd] })
  },

  addTransaction: (tx) => {
    const id = uid()
    set((s) => ({ transactions: [...s.transactions, { ...tx, id }] }))
    return id
  },

  updateTransaction: (id, patch) =>
    set((s) => ({
      transactions: s.transactions.map((t) =>
        t.id === id ? { ...t, ...patch, month: (patch.date ?? t.date).slice(0, 7) } : t,
      ),
    })),

  removeTransaction: (id) => {
    const tx = get().transactions.find((t) => t.id === id)
    if (!tx) return undefined
    set((s) => ({
      transactions: s.transactions.filter((t) => t.id !== id),
      // Lembra que este mês foi excluído para a recorrência não recriá-lo.
      recurring: tx.recurringId
        ? s.recurring.map((r) =>
            r.id === tx.recurringId ? { ...r, skipped: [...new Set([...r.skipped, tx.month])] } : r,
          )
        : s.recurring,
    }))
    return tx
  },

  restoreTransaction: (tx) =>
    set((s) => ({
      transactions: [...s.transactions, tx],
      recurring: tx.recurringId
        ? s.recurring.map((r) =>
            r.id === tx.recurringId ? { ...r, skipped: r.skipped.filter((m) => m !== tx.month) } : r,
          )
        : s.recurring,
    })),

  togglePaid: (id) =>
    set((s) => ({ transactions: s.transactions.map((t) => (t.id === id ? { ...t, paid: !t.paid } : t)) })),

  addRecurring: (r) => {
    const id = uid()
    set((s) => ({ recurring: [...s.recurring, { active: true, ...r, id, skipped: [] }] }))
    return id
  },

  updateRecurring: (id, patch) => {
    const from = currentMonth()
    set((s) => {
      const recurring = s.recurring.map((r) => (r.id === id ? { ...r, ...patch } : r))
      const updated = recurring.find((r) => r.id === id)
      if (!updated) return { recurring }
      // Lançamentos ainda não pagos deste mês em diante acompanham a alteração.
      const transactions = s.transactions
        .filter(
          (t) =>
            !(
              t.recurringId === id &&
              !t.paid &&
              t.month >= from &&
              (t.month < updated.startMonth || (updated.endMonth && t.month > updated.endMonth) || !updated.active)
            ),
        )
        .map((t) =>
          t.recurringId === id && !t.paid && t.month >= from
            ? {
                ...t,
                type: updated.type,
                description: updated.description,
                amount: updated.amount,
                categoryId: updated.categoryId,
                goalId: updated.goalId,
                date: dateInMonth(t.month, updated.day),
              }
            : t,
        )
      return { recurring, transactions }
    })
  },

  removeRecurring: (id) => {
    const from = currentMonth()
    set((s) => ({
      recurring: s.recurring.filter((r) => r.id !== id),
      // Remove só os lançamentos futuros ainda não pagos; o histórico fica.
      transactions: s.transactions
        .filter((t) => !(t.recurringId === id && !t.paid && t.month > from))
        .map((t) => (t.recurringId === id ? { ...t, recurringId: undefined } : t)),
    }))
  },

  applyPreset: (id) =>
    set((s) => ({ ...applyPresetTo(s, id), settings: { ...s.settings, presetId: id } })),

  updateBucket: (id, patch) =>
    set((s) => ({
      buckets: s.buckets.map((b) => (b.id === id ? { ...b, ...patch } : b)),
      // Ao mudar o tipo do pote, as categorias dele passam a ter essa natureza.
      categories: patch.kind
        ? s.categories.map((c) => (c.bucketId === id ? { ...c, kind: patch.kind! } : c))
        : s.categories,
      settings: { ...s.settings, presetId: 'custom' },
    })),

  addBucket: () =>
    set((s) =>
      s.buckets.length >= MAX_BUCKETS
        ? s
        : {
            buckets: [...s.buckets, { id: uid(), name: 'Novo pote', percent: 0, kind: 'outros' }],
            settings: { ...s.settings, presetId: 'custom' },
          },
    ),

  removeBucket: (id) =>
    set((s) => {
      if (s.buckets.length <= 1) return s
      const buckets = s.buckets.filter((b) => b.id !== id)
      const fallback = buckets[0]
      return {
        buckets,
        categories: s.categories.map((c) =>
          c.bucketId === id ? { ...c, bucketId: fallback.id, kind: fallback.kind } : c,
        ),
        settings: { ...s.settings, presetId: 'custom' },
      }
    }),

  normalizeBuckets: () =>
    set((s) => ({ buckets: normalizePercents(s.buckets), settings: { ...s.settings, presetId: 'custom' } })),

  addCategory: (c) => {
    const id = uid()
    set((s) => ({ categories: [...s.categories, { ...c, id }] }))
    return id
  },

  updateCategory: (id, patch) =>
    set((s) => ({
      categories: s.categories.map((c) => {
        if (c.id !== id) return c
        const next = { ...c, ...patch }
        // Trocar de pote muda a natureza da categoria para a do novo pote.
        if (patch.bucketId && patch.bucketId !== c.bucketId) {
          next.kind = s.buckets.find((b) => b.id === patch.bucketId)?.kind ?? c.kind
        }
        return next
      }),
    })),

  removeCategory: (id) => {
    const s = get()
    const used = s.transactions.some((t) => t.categoryId === id) || s.recurring.some((r) => r.categoryId === id)
    if (used) {
      set({ categories: s.categories.map((c) => (c.id === id ? { ...c, archived: true } : c)) })
      return 'archived'
    }
    set({ categories: s.categories.filter((c) => c.id !== id) })
    return 'removed'
  },

  addGoal: (g) => {
    const id = uid()
    set((s) => ({ goals: [...s.goals, { ...g, id }] }))
    return id
  },

  updateGoal: (id, patch) => set((s) => ({ goals: s.goals.map((g) => (g.id === id ? { ...g, ...patch } : g)) })),

  removeGoal: (id) =>
    set((s) =>
      id === RESERVE_GOAL_ID
        ? s
        : {
            goals: s.goals.filter((g) => g.id !== id),
            transactions: s.transactions.map((t) => (t.goalId === id ? { ...t, goalId: undefined } : t)),
            recurring: s.recurring.map((r) => (r.goalId === id ? { ...r, goalId: undefined } : r)),
            categories: s.categories.map((c) => (c.goalId === id ? { ...c, goalId: undefined } : c)),
          },
    ),

  updateSettings: (patch) => set((s) => ({ settings: { ...s.settings, ...patch } })),

  completeOnboarding: (p) => {
    const month = currentMonth()
    const actions = get()
    actions.applyPreset(p.presetId)
    if (p.income.amount > 0) {
      actions.addRecurring({
        type: 'receita',
        description: p.income.description || 'Salário',
        amount: p.income.amount,
        day: p.income.day,
        startMonth: month,
      })
    }
    for (const bill of p.bills.filter((b) => b.amount > 0)) {
      actions.addRecurring({ type: 'despesa', ...bill, startMonth: month })
    }
    set((s) => ({
      goals: s.goals.map((g) => (g.id === RESERVE_GOAL_ID ? { ...g, initial: p.reserveBalance } : g)),
      settings: { ...s.settings, userName: p.userName, reserveMonths: p.reserveMonths, onboarded: true },
    }))
    get().ensureMonth(month)
  },

  replaceData: (data) => set({ ...data, profile: data.profile }),

  setProfile: (profile) => set({ profile }),

  resetAll: () => set({ ...createInitialData() }),
}))

/** Lê somente os dados (sem ações) — útil para os cálculos em `lib/finance`. */
export function selectData(s: Store): DataState {
  return {
    buckets: s.buckets,
    categories: s.categories,
    transactions: s.transactions,
    recurring: s.recurring,
    goals: s.goals,
    settings: s.settings,
    profile: s.profile,
  }
}

// ---------- Estado de interface (não persistido) ----------

export interface TxDialogState {
  open: boolean
  editing?: Transaction
  defaults?: Partial<Transaction>
}

interface UIState {
  month: MonthKey
  setMonth: (m: MonthKey) => void
  shiftMonth: (n: number) => void
  txDialog: TxDialogState
  openTx: (opts?: { editing?: Transaction; defaults?: Partial<Transaction> }) => void
  closeTx: () => void
}

export const useUI = create<UIState>()((set) => ({
  month: currentMonth(),
  setMonth: (month) => set({ month }),
  shiftMonth: (n) => set((s) => ({ month: addMonths(s.month, n) })),
  txDialog: { open: false },
  openTx: (opts) => set({ txDialog: { open: true, ...opts } }),
  closeTx: () => set((s) => ({ txDialog: { ...s.txDialog, open: false } })),
}))
