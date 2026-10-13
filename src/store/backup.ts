import { z } from 'zod'
import { decryptJSON, deriveKek, encryptJSON, newKdf, PASSWORD_ITERATIONS } from '@/lib/crypto'
import { DEFAULT_SETTINGS } from '@/lib/presets'
import type { DataState } from '@/store/types'

const month = z.string().regex(/^\d{4}-\d{2}$/)
const kind = z.enum(['necessidades', 'desejos', 'futuro', 'outros'])
const txType = z.enum(['receita', 'despesa'])
const text = (max = 200) => z.string().max(max)
const money = z.number().finite().min(-1e12).max(1e12)

const dataSchema = z.object({
  buckets: z
    .array(z.object({ id: text(), name: text(), percent: z.number().min(0).max(100), kind, description: text(500).optional() }))
    .min(1)
    .max(12),
  categories: z.array(
    z.object({
      id: text(),
      name: text(),
      icon: text(),
      bucketId: text(),
      kind,
      budget: money.optional(),
      goalId: text().optional(),
      archived: z.boolean().optional(),
    }),
  ),
  transactions: z.array(
    z.object({
      id: text(),
      type: txType,
      month,
      date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
      description: text(),
      amount: money,
      categoryId: text().optional(),
      goalId: text().optional(),
      recurringId: text().optional(),
      paid: z.boolean(),
    }),
  ),
  recurring: z.array(
    z.object({
      id: text(),
      type: txType,
      description: text(),
      amount: money,
      categoryId: text().optional(),
      goalId: text().optional(),
      day: z.number().int().min(1).max(31),
      startMonth: month,
      endMonth: month.optional(),
      active: z.boolean(),
      skipped: z.array(month),
    }),
  ),
  goals: z.array(
    z.object({
      id: text(),
      name: text(),
      icon: text(),
      kind: z.enum(['reserva', 'investimento', 'meta']),
      target: money,
      deadline: month.optional(),
      initial: money,
    }),
  ),
  profile: z
    .object({
      answers: z.record(z.string().max(40), z.number().int().min(0).max(10)),
      score: z.number().min(0).max(100),
      profile: z.enum(['conservador', 'moderado', 'arrojado']),
      date: z.string().max(40),
      custom: z
        .object({
          posfixado: z.number().min(0).max(100),
          inflacao: z.number().min(0).max(100),
          prefixado: z.number().min(0).max(100),
          isentos: z.number().min(0).max(100),
          fiis: z.number().min(0).max(100),
          acoes: z.number().min(0).max(100),
          exterior: z.number().min(0).max(100),
        })
        .partial()
        .optional(),
    })
    .optional(),
  settings: z
    .object({
      userName: text(60),
      presetId: z.enum(['50-30-20', '70-20-10', '6-potes', '80-20', 'custom']),
      reserveMonths: z.number().int().min(1).max(60),
      reserveBase: z.enum(['necessidades', 'gastos', 'manual']),
      reserveManualCost: money.min(0),
      reservePriority: z.number().min(0).max(100),
      sim: z
        .object({
          initial: money.min(0),
          monthly: money.min(0),
          annualRate: z.number().min(0).max(100),
          years: z.number().int().min(1).max(50),
          inflation: z.number().min(0).max(100),
          yearlyIncrease: z.number().min(0).max(100),
          target: money.min(0),
        })
        .partial(),
      onboarded: z.boolean(),
      lastExport: z.string().max(40),
      autoLockMinutes: z.number().int().min(0).max(1440),
    })
    .partial(),
})

const encryptedSchema = z.object({
  app: z.literal('financas'),
  format: z.literal('encrypted'),
  version: z.literal(1),
  kdf: z.object({ salt: z.string(), iterations: z.number().int().min(100_000).max(10_000_000) }),
  iv: z.string(),
  data: z.string(),
})

const BACKUP_AAD = 'financas:backup:v1'

/** Gera um arquivo de backup criptografado com a senha informada (AES-256-GCM + PBKDF2). */
export async function exportEncryptedBackup(data: DataState, password: string): Promise<Blob> {
  const kdf = newKdf(PASSWORD_ITERATIONS)
  const key = await deriveKek(password, kdf)
  const sealed = await encryptJSON({ exportedAt: new Date().toISOString(), data }, key, BACKUP_AAD)
  const file = { app: 'financas', format: 'encrypted', version: 1, kdf, ...sealed }
  return new Blob([JSON.stringify(file)], { type: 'application/json' })
}

export function isEncryptedBackup(textContent: string) {
  try {
    return encryptedSchema.safeParse(JSON.parse(textContent)).success
  } catch {
    return false
  }
}

function validate(json: unknown): DataState {
  const result = dataSchema.safeParse(json)
  if (!result.success) throw new Error('O conteúdo do backup está incompleto ou corrompido.')
  const { settings, ...rest } = result.data
  return { ...rest, settings: { ...DEFAULT_SETTINGS, ...settings, sim: { ...DEFAULT_SETTINGS.sim, ...settings.sim } } }
}

/** Lê um backup criptografado. Lança erro amigável se a senha ou o arquivo estiverem errados. */
export async function readEncryptedBackup(textContent: string, password: string): Promise<DataState> {
  let file: z.infer<typeof encryptedSchema>
  try {
    file = encryptedSchema.parse(JSON.parse(textContent))
  } catch {
    throw new Error('Este arquivo não é um backup do app Finanças.')
  }
  let payload: { data: unknown }
  try {
    const key = await deriveKek(password, file.kdf)
    payload = await decryptJSON(file, key, BACKUP_AAD)
  } catch {
    throw new Error('Senha do backup incorreta ou arquivo alterado.')
  }
  return validate(payload.data)
}
