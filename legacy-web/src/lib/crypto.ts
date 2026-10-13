/**
 * Criptografia do cofre local — somente Web Crypto API (nativa do navegador).
 *
 * Esquema (envelope encryption):
 *  - Uma chave de dados (DEK) AES-256-GCM aleatória criptografa os dados financeiros.
 *  - A DEK é "embrulhada" duas vezes: por uma chave derivada da senha (PBKDF2-SHA256)
 *    e por uma chave derivada do código de recuperação.
 *  - A senha nunca é armazenada. Senha errada = falha de autenticação do AES-GCM.
 */

const subtle = () => {
  if (!globalThis.crypto?.subtle) {
    throw new Error(
      'Criptografia indisponível. Abra o app em http://localhost ou em um endereço HTTPS.',
    )
  }
  return globalThis.crypto.subtle
}

export const PASSWORD_ITERATIONS = 600_000
export const RECOVERY_ITERATIONS = 200_000

const enc = new TextEncoder()
const dec = new TextDecoder()

export interface Sealed {
  iv: string
  data: string
}

export interface KdfParams {
  salt: string
  iterations: number
}

// ---------- Base64 ----------

export function toB64(bytes: ArrayBuffer | Uint8Array): string {
  const arr = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes)
  let bin = ''
  for (let i = 0; i < arr.length; i++) bin += String.fromCharCode(arr[i])
  return btoa(bin)
}

export function fromB64(b64: string): Uint8Array<ArrayBuffer> {
  const bin = atob(b64)
  const out = new Uint8Array(bin.length)
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i)
  return out
}

export function randomBytes(n: number): Uint8Array<ArrayBuffer> {
  return globalThis.crypto.getRandomValues(new Uint8Array(n))
}

// ---------- Chaves ----------

/** Deriva uma chave de embrulho (KEK) a partir de um segredo digitado. */
export async function deriveKek(secret: string, kdf: KdfParams): Promise<CryptoKey> {
  const base = await subtle().importKey('raw', enc.encode(secret.normalize('NFKC')), 'PBKDF2', false, ['deriveKey'])
  return subtle().deriveKey(
    { name: 'PBKDF2', hash: 'SHA-256', salt: fromB64(kdf.salt), iterations: kdf.iterations },
    base,
    { name: 'AES-GCM', length: 256 },
    false,
    ['wrapKey', 'unwrapKey', 'encrypt', 'decrypt'],
  )
}

export function newKdf(iterations: number): KdfParams {
  return { salt: toB64(randomBytes(16)), iterations }
}

/** DEK extraível apenas para poder ser embrulhada; a sessão usa uma cópia não extraível. */
export function generateDataKey(): Promise<CryptoKey> {
  return subtle().generateKey({ name: 'AES-GCM', length: 256 }, true, ['encrypt', 'decrypt'])
}

export async function wrapDataKey(dek: CryptoKey, kek: CryptoKey, aad: string): Promise<Sealed> {
  const iv = randomBytes(12)
  const data = await subtle().wrapKey('raw', dek, kek, { name: 'AES-GCM', iv, additionalData: enc.encode(aad) })
  return { iv: toB64(iv), data: toB64(data) }
}

/** Lança erro se a KEK estiver errada (tag de autenticação inválida). */
export function unwrapDataKey(sealed: Sealed, kek: CryptoKey, aad: string, extractable = false): Promise<CryptoKey> {
  return subtle().unwrapKey(
    'raw',
    fromB64(sealed.data),
    kek,
    { name: 'AES-GCM', iv: fromB64(sealed.iv), additionalData: enc.encode(aad) },
    { name: 'AES-GCM', length: 256 },
    extractable,
    ['encrypt', 'decrypt'],
  )
}

// ---------- Dados ----------

export async function encryptJSON(value: unknown, key: CryptoKey, aad: string): Promise<Sealed> {
  const iv = randomBytes(12)
  const data = await subtle().encrypt(
    { name: 'AES-GCM', iv, additionalData: enc.encode(aad) },
    key,
    enc.encode(JSON.stringify(value)),
  )
  return { iv: toB64(iv), data: toB64(data) }
}

export async function decryptJSON<T>(sealed: Sealed, key: CryptoKey, aad: string): Promise<T> {
  const plain = await subtle().decrypt(
    { name: 'AES-GCM', iv: fromB64(sealed.iv), additionalData: enc.encode(aad) },
    key,
    fromB64(sealed.data),
  )
  return JSON.parse(dec.decode(plain)) as T
}

export async function sha256(text: string): Promise<string> {
  return toB64(await subtle().digest('SHA-256', enc.encode(text)))
}

// ---------- Código de recuperação ----------

/** Base32 de Crockford: sem I, L, O, U para evitar confusão na hora de digitar. */
const B32 = '0123456789ABCDEFGHJKMNPQRSTVWXYZ'

/** 160 bits aleatórios → "XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX". */
export function generateRecoveryCode(): string {
  const bytes = randomBytes(20)
  let bits = 0
  let value = 0
  let out = ''
  for (const byte of bytes) {
    value = (value << 8) | byte
    bits += 8
    while (bits >= 5) {
      out += B32[(value >>> (bits - 5)) & 31]
      bits -= 5
    }
  }
  return out.match(/.{4}/g)!.join('-')
}

/** Aceita o código com ou sem hífens/espaços e corrige letras parecidas. */
export function normalizeRecoveryCode(input: string): string {
  const clean = input
    .toUpperCase()
    .replace(/[^0-9A-Z]/g, '')
    .replace(/O/g, '0')
    .replace(/[IL]/g, '1')
  return (clean.match(/.{1,4}/g) ?? []).join('-')
}

// ---------- Força da senha ----------

const COMMON = new Set([
  '123456', '12345678', '123456789', '1234567890', '102030', '112233', '123123', '111111', '000000',
  'password', 'senha', 'senha123', 'senha1234', 'qwerty', 'abc123', 'abcd1234', 'admin', 'iloveyou',
  'brasil', 'flamengo', 'corinthians', 'palmeiras', 'saopaulo', 'vasco', 'gremio', 'cruzeiro',
  'mudar123', 'teste123', 'financas', 'dinheiro',
])

export interface PasswordCheck {
  score: 0 | 1 | 2 | 3 | 4
  label: string
  ok: boolean
  hint?: string
}

export function checkPassword(pw: string, username = ''): PasswordCheck {
  const lower = pw.toLowerCase()
  if (pw.length < 8) return { score: 0, label: 'Muito curta', ok: false, hint: 'Use pelo menos 8 caracteres.' }
  if (COMMON.has(lower) || COMMON.has(lower.replace(/\d+$/, ''))) {
    return { score: 0, label: 'Muito comum', ok: false, hint: 'Essa senha está entre as mais usadas. Escolha outra.' }
  }
  if (username && lower.includes(username.toLowerCase())) {
    return { score: 1, label: 'Fraca', ok: false, hint: 'Não use o nome de usuário na senha.' }
  }
  const classes = [/[a-z]/, /[A-Z]/, /\d/, /[^A-Za-z0-9]/].filter((r) => r.test(pw)).length
  const unique = new Set(pw).size
  let score = 0
  if (pw.length >= 8) score++
  if (pw.length >= 12) score++
  if (classes >= 3) score++
  if (pw.length >= 16 || (classes === 4 && pw.length >= 12)) score++
  if (unique < 5) score = Math.min(score, 1)
  const s = Math.min(4, score) as PasswordCheck['score']
  const labels = ['Muito fraca', 'Fraca', 'Razoável', 'Forte', 'Muito forte']
  return {
    score: s,
    label: labels[s],
    ok: s >= 2,
    hint: s < 2 ? 'Use 12+ caracteres misturando letras, números e símbolos — ou uma frase longa.' : undefined,
  }
}
