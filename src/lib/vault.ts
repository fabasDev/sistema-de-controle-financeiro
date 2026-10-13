/**
 * Cofre local: contas e dados criptografados guardados no localStorage.
 * Nada aqui é legível sem a senha (ou o código de recuperação) do usuário.
 */
import {
  decryptJSON,
  deriveKek,
  encryptJSON,
  generateDataKey,
  generateRecoveryCode,
  newKdf,
  normalizeRecoveryCode,
  PASSWORD_ITERATIONS,
  RECOVERY_ITERATIONS,
  sha256,
  unwrapDataKey,
  wrapDataKey,
  type KdfParams,
  type Sealed,
} from '@/lib/crypto'

export interface AccountRecord {
  id: string
  /** Hash do nome de usuário — o nome em si não fica salvo em texto puro. */
  userHash: string
  createdAt: string
  updatedAt: string
  password: { kdf: KdfParams; key: Sealed }
  recovery: { kdf: KdfParams; key: Sealed }
  payload?: Sealed
  throttle?: { failures: number; until: number }
}

interface VaultFile {
  version: 1
  accounts: AccountRecord[]
}

type KV = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>

export const VAULT_KEY = 'financas:vault'
let storage: KV | undefined = globalThis.localStorage

/** Permite trocar o armazenamento (usado nos testes). */
export function setVaultStorage(kv: KV) {
  storage = kv
}

function kv(): KV {
  if (!storage) throw new Error('Armazenamento local indisponível neste navegador.')
  return storage
}

function readVault(): VaultFile {
  try {
    const raw = kv().getItem(VAULT_KEY)
    if (raw) {
      const parsed = JSON.parse(raw) as VaultFile
      if (parsed.version === 1 && Array.isArray(parsed.accounts)) return parsed
    }
  } catch {
    /* cofre corrompido ou inacessível: trata como vazio */
  }
  return { version: 1, accounts: [] }
}

function writeVault(v: VaultFile) {
  try {
    kv().setItem(VAULT_KEY, JSON.stringify(v))
  } catch {
    throw new Error('Não foi possível salvar. O armazenamento do navegador pode estar cheio ou bloqueado.')
  }
}

function updateAccount(id: string, patch: (a: AccountRecord) => AccountRecord) {
  const v = readVault()
  v.accounts = v.accounts.map((a) => (a.id === id ? patch(a) : a))
  writeVault(v)
}

const normalizeUser = (u: string) => u.trim().toLowerCase()
const userHash = (u: string) => sha256(`financas:user:${normalizeUser(u)}`)
/** Dados adicionais autenticados: amarram cada blob criptografado à sua conta e finalidade. */
const aad = (id: string, purpose: 'password' | 'recovery' | 'data') => `financas:v1:${id}:${purpose}`

export function hasAccounts() {
  return readVault().accounts.length > 0
}

export function readAccount(id: string) {
  return readVault().accounts.find((a) => a.id === id)
}

export class AuthError extends Error {
  readonly retryAt?: number
  constructor(message: string, retryAt?: number) {
    super(message)
    this.name = 'AuthError'
    this.retryAt = retryAt
  }
}

// ---------- Limite de tentativas ----------

const FREE_ATTEMPTS = 5

function assertNotThrottled(a: AccountRecord) {
  if (a.throttle && a.throttle.until > Date.now()) {
    throw new AuthError('Muitas tentativas. Aguarde para tentar de novo.', a.throttle.until)
  }
}

function registerFailure(a: AccountRecord) {
  const failures = (a.throttle?.failures ?? 0) + 1
  const wait = failures >= FREE_ATTEMPTS ? Math.min(15 * 60_000, 30_000 * 2 ** (failures - FREE_ATTEMPTS)) : 0
  updateAccount(a.id, (acc) => ({ ...acc, throttle: { failures, until: Date.now() + wait } }))
}

function clearFailures(id: string) {
  updateAccount(id, ({ throttle: _t, ...acc }) => acc)
}

// ---------- Sessão ----------

export interface Session {
  accountId: string
  username: string
  /** Chave de dados não extraível — vive só na memória. */
  key: CryptoKey
}

export async function createAccount(username: string, password: string) {
  const name = normalizeUser(username)
  if (name.length < 3) throw new AuthError('O usuário precisa ter pelo menos 3 caracteres.')
  const hash = await userHash(name)
  const vault = readVault()
  if (vault.accounts.some((a) => a.userHash === hash)) throw new AuthError('Já existe uma conta com esse usuário neste navegador.')

  const id = crypto.randomUUID()
  const dek = await generateDataKey()
  const recoveryCode = generateRecoveryCode()
  const pwKdf = newKdf(PASSWORD_ITERATIONS)
  const rcKdf = newKdf(RECOVERY_ITERATIONS)
  const [pwKek, rcKek] = await Promise.all([deriveKek(password, pwKdf), deriveKek(recoveryCode, rcKdf)])
  const [pwSealed, rcSealed] = await Promise.all([
    wrapDataKey(dek, pwKek, aad(id, 'password')),
    wrapDataKey(dek, rcKek, aad(id, 'recovery')),
  ])
  const now = new Date().toISOString()
  vault.accounts.push({
    id,
    userHash: hash,
    createdAt: now,
    updatedAt: now,
    password: { kdf: pwKdf, key: pwSealed },
    recovery: { kdf: rcKdf, key: rcSealed },
  })
  writeVault(vault)

  // Sessão usa uma cópia NÃO extraível da chave.
  const key = await unwrapDataKey(pwSealed, pwKek, aad(id, 'password'))
  return { session: { accountId: id, username: name, key } satisfies Session, recoveryCode }
}

async function findAccount(username: string) {
  const hash = await userHash(username)
  return readVault().accounts.find((a) => a.userHash === hash)
}

const BAD_LOGIN = 'Usuário ou senha incorretos.'

export async function login(username: string, password: string): Promise<Session> {
  const account = await findAccount(username)
  if (!account) {
    // Mesmo custo de uma tentativa real, para não revelar se o usuário existe.
    await deriveKek(password, newKdf(PASSWORD_ITERATIONS))
    throw new AuthError(BAD_LOGIN)
  }
  assertNotThrottled(account)
  try {
    const kek = await deriveKek(password, account.password.kdf)
    const key = await unwrapDataKey(account.password.key, kek, aad(account.id, 'password'))
    clearFailures(account.id)
    return { accountId: account.id, username: normalizeUser(username), key }
  } catch {
    registerFailure(account)
    throw new AuthError(BAD_LOGIN)
  }
}

/** Usa o código de recuperação para definir uma nova senha. Gera um novo código. */
export async function resetWithRecovery(username: string, code: string, newPassword: string) {
  const account = await findAccount(username)
  if (!account) throw new AuthError('Usuário ou código de recuperação incorretos.')
  assertNotThrottled(account)
  let dek: CryptoKey
  try {
    const kek = await deriveKek(normalizeRecoveryCode(code), account.recovery.kdf)
    dek = await unwrapDataKey(account.recovery.key, kek, aad(account.id, 'recovery'), true)
  } catch {
    registerFailure(account)
    throw new AuthError('Usuário ou código de recuperação incorretos.')
  }
  const recoveryCode = await rewrap(account.id, dek, { password: newPassword, recovery: true })
  clearFailures(account.id)
  const session = await login(username, newPassword)
  return { session, recoveryCode: recoveryCode! }
}

/** Reembrulha a chave de dados com nova senha e/ou novo código de recuperação. */
async function rewrap(id: string, dek: CryptoKey, opts: { password?: string; recovery?: boolean }) {
  let recoveryCode: string | undefined
  const patch: Partial<AccountRecord> = {}
  if (opts.password) {
    const kdf = newKdf(PASSWORD_ITERATIONS)
    patch.password = { kdf, key: await wrapDataKey(dek, await deriveKek(opts.password, kdf), aad(id, 'password')) }
  }
  if (opts.recovery) {
    recoveryCode = generateRecoveryCode()
    const kdf = newKdf(RECOVERY_ITERATIONS)
    patch.recovery = { kdf, key: await wrapDataKey(dek, await deriveKek(recoveryCode, kdf), aad(id, 'recovery')) }
  }
  updateAccount(id, (a) => ({ ...a, ...patch, updatedAt: new Date().toISOString() }))
  return recoveryCode
}

async function unlockExtractable(id: string, password: string) {
  const account = readAccount(id)
  if (!account) throw new AuthError('Conta não encontrada.')
  try {
    const kek = await deriveKek(password, account.password.kdf)
    return await unwrapDataKey(account.password.key, kek, aad(id, 'password'), true)
  } catch {
    throw new AuthError('Senha atual incorreta.')
  }
}

export async function changePassword(id: string, current: string, next: string) {
  const dek = await unlockExtractable(id, current)
  await rewrap(id, dek, { password: next })
}

export async function regenerateRecovery(id: string, password: string) {
  const dek = await unlockExtractable(id, password)
  return (await rewrap(id, dek, { recovery: true }))!
}

export async function verifyPassword(id: string, password: string) {
  await unlockExtractable(id, password)
}

export async function deleteAccount(id: string, password: string) {
  await unlockExtractable(id, password)
  const v = readVault()
  v.accounts = v.accounts.filter((a) => a.id !== id)
  writeVault(v)
}

// ---------- Dados ----------

export async function saveData(session: Session, data: unknown) {
  const payload = await encryptJSON(data, session.key, aad(session.accountId, 'data'))
  updateAccount(session.accountId, (a) => ({ ...a, payload, updatedAt: new Date().toISOString() }))
}

export async function loadData<T>(session: Session): Promise<T | undefined> {
  const account = readAccount(session.accountId)
  if (!account?.payload) return undefined
  return decryptJSON<T>(account.payload, session.key, aad(session.accountId, 'data'))
}
