import { create } from 'zustand'
import { currentMonth } from '@/lib/month'
import { createInitialData, DEFAULT_SETTINGS } from '@/lib/presets'
import * as vault from '@/lib/vault'
import { selectData, useStore, useUI } from '@/store/store'
import type { DataState } from '@/store/types'

type LockReason = 'manual' | 'inactivity' | 'removed'

interface SessionState {
  session?: vault.Session
  /** Código mostrado uma única vez após cadastro/redefinição. */
  recoveryCode?: string
  lockReason?: LockReason
  saveError?: string
  signup: (username: string, password: string) => Promise<void>
  login: (username: string, password: string) => Promise<void>
  recover: (username: string, code: string, newPassword: string) => Promise<void>
  logout: (reason?: LockReason) => Promise<void>
  showRecoveryCode: (code: string) => void
  dismissRecoveryCode: () => void
}

/** Garante que dados antigos ganhem campos novos com valores padrão. */
export function withDefaults(data: Partial<DataState> | undefined): DataState {
  const base = createInitialData()
  if (!data) return base
  return {
    ...base,
    ...data,
    settings: { ...DEFAULT_SETTINGS, ...data.settings, sim: { ...DEFAULT_SETTINGS.sim, ...data.settings?.sim } },
  }
}

// ---------- Salvamento automático criptografado ----------

let unsubscribe: (() => void) | undefined
let saveTimer: ReturnType<typeof setTimeout> | undefined
let saving: Promise<void> = Promise.resolve()
/** Evita salvar de volta dados que acabaram de chegar de outra aba. */
let applyingRemote = false

function scheduleSave() {
  clearTimeout(saveTimer)
  saveTimer = setTimeout(flush, 300)
}

function flush(): Promise<void> {
  clearTimeout(saveTimer)
  saveTimer = undefined
  const session = useSession.getState().session
  if (!session) return saving
  const data = selectData(useStore.getState())
  saving = saving
    .then(() => vault.saveData(session, data))
    .then(() => useSession.setState({ saveError: undefined }))
    .catch((e: Error) => useSession.setState({ saveError: e.message }))
  return saving
}

function applyData(data: DataState) {
  applyingRemote = true
  try {
    useStore.getState().replaceData(data)
  } finally {
    applyingRemote = false
  }
}

async function startSession(session: vault.Session, initial?: DataState) {
  const data = initial ?? withDefaults(await vault.loadData<DataState>(session))
  applyData(data)
  useUI.setState({ month: currentMonth(), txDialog: { open: false } })
  useSession.setState({ session, lockReason: undefined, saveError: undefined })
  unsubscribe?.()
  unsubscribe = useStore.subscribe(() => {
    if (!applyingRemote) scheduleSave()
  })
  if (initial) await flush()
  startAutoLock()
}

// ---------- Bloqueio por inatividade ----------

let lastActivity = Date.now()
let lockInterval: ReturnType<typeof setInterval> | undefined
const markActivity = () => {
  lastActivity = Date.now()
}
const ACTIVITY_EVENTS = ['pointerdown', 'keydown', 'wheel', 'touchstart'] as const

function startAutoLock() {
  stopAutoLock()
  lastActivity = Date.now()
  ACTIVITY_EVENTS.forEach((e) => window.addEventListener(e, markActivity, { passive: true }))
  lockInterval = setInterval(() => {
    const minutes = useStore.getState().settings.autoLockMinutes
    if (minutes > 0 && Date.now() - lastActivity > minutes * 60_000) {
      void useSession.getState().logout('inactivity')
    }
  }, 15_000)
}

function stopAutoLock() {
  clearInterval(lockInterval)
  ACTIVITY_EVENTS.forEach((e) => window.removeEventListener(e, markActivity))
}

// ---------- Store ----------

export const useSession = create<SessionState>()((set, get) => ({
  signup: async (username, password) => {
    const { session, recoveryCode } = await vault.createAccount(username, password)
    set({ recoveryCode })
    await startSession(session, createInitialData())
  },

  login: async (username, password) => {
    const session = await vault.login(username, password)
    await startSession(session)
  },

  recover: async (username, code, newPassword) => {
    const { session, recoveryCode } = await vault.resetWithRecovery(username, code, newPassword)
    set({ recoveryCode })
    await startSession(session)
  },

  logout: async (reason = 'manual') => {
    if (!get().session) return
    if (reason !== 'removed') await flush()
    unsubscribe?.()
    unsubscribe = undefined
    stopAutoLock()
    // Remove os dados e a chave da memória.
    applyData(createInitialData())
    useUI.setState({ month: currentMonth(), txDialog: { open: false } })
    set({ session: undefined, recoveryCode: undefined, lockReason: reason })
  },

  showRecoveryCode: (code) => set({ recoveryCode: code }),
  dismissRecoveryCode: () => set({ recoveryCode: undefined }),
}))

// Outra aba alterou o cofre: recarrega os dados (ou encerra se a conta sumiu).
window.addEventListener('storage', (e) => {
  const session = useSession.getState().session
  if (e.key !== vault.VAULT_KEY || !session) return
  const account = vault.readAccount(session.accountId)
  if (!account) {
    void useSession.getState().logout('removed')
    return
  }
  vault
    .loadData<DataState>(session)
    .then((data) => data && applyData(withDefaults(data)))
    .catch(() => void useSession.getState().logout('removed'))
})

// Última chance de salvar ao fechar a aba (o salvamento normal já ocorre em ~300 ms).
window.addEventListener('pagehide', () => {
  if (saveTimer) void flush()
})

// Remove dados em texto puro de versões anteriores do app, se existirem.
try {
  localStorage.removeItem('financas:data')
} catch {
  /* armazenamento indisponível */
}
