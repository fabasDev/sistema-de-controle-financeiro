/**
 * Gera o arquivo de backup usado nos testes da importação em Java
 * (src/test/resources/backup/backup-antigo.json), com o código de exportação real deste app.
 * Assim o teste em Java prova que lê o que o app antigo grava.
 *
 * Só roda quando FIXTURE_OUT aponta para o arquivo de saída. No `npm test` normal, fica pulado:
 *   FIXTURE_OUT=../src/test/resources/backup/backup-antigo.json npx vitest run scripts/backup-fixture.test.ts
 *
 * Dados fictícios e senha descartável (está no teste em Java de propósito).
 */
import { writeFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { createInitialData, RESERVE_GOAL_ID } from '@/lib/presets'
import { exportEncryptedBackup, readEncryptedBackup } from '@/store/backup'
import type { DataState } from '@/store/types'

const out = process.env.FIXTURE_OUT
/** Com acento e "ç" para conferir que Java e navegador codificam a senha igual (UTF-8 + NFKC). */
export const FIXTURE_PASSWORD = 'backup de teste com ção e café'

function fixtureData(): DataState {
  const data = createInitialData()
  data.settings = {
    ...data.settings,
    userName: 'Pessoa de Teste',
    reserveMonths: 8,
    reservePriority: 60,
    sim: { initial: 1500, monthly: 300.5, annualRate: 11.25, years: 15, inflation: 4.5, yearlyIncrease: 3, target: 200000 },
    onboarded: true,
  }
  data.categories.push({ id: 'b3f1c2d4-pets', name: 'Pets', icon: 'PawPrint', bucketId: 'bucket-50-30-20-1', kind: 'desejos', budget: 150 })
  data.goals = data.goals.map((g) => (g.id === RESERVE_GOAL_ID ? { ...g, initial: 1000 } : g))
  data.goals.push({ id: 'goal-viagem-1', name: 'Viagem', icon: 'Plane', kind: 'meta', target: 6000, deadline: '2027-03', initial: 1200.5 })
  data.recurring = [
    { id: 'rec-salario', type: 'receita', description: 'Salário', amount: 4000, day: 5, startMonth: '2026-01', active: true, skipped: [] },
    { id: 'rec-internet', type: 'despesa', description: 'Internet', amount: 99.9, categoryId: 'cat-contas', day: 20, startMonth: '2026-01', active: true, skipped: ['2026-03'] },
    { id: 'rec-notebook', type: 'despesa', description: 'Notebook', amount: 350.33, categoryId: 'cat-compras', day: 31, startMonth: '2026-08', endMonth: '2026-12', active: true, skipped: [] },
  ]
  data.transactions = [
    { id: 't1', type: 'receita', month: '2026-09', date: '2026-09-05', description: 'Salário', amount: 4000, recurringId: 'rec-salario', paid: true },
    { id: 't2', type: 'despesa', month: '2026-09', date: '2026-09-20', description: 'Internet', amount: 99.9, categoryId: 'cat-contas', recurringId: 'rec-internet', paid: true },
    { id: 't3', type: 'despesa', month: '2026-09', date: '2026-09-12', description: 'Ração', amount: 89.99, categoryId: 'b3f1c2d4-pets', paid: true },
    { id: 't4', type: 'despesa', month: '2026-09', date: '2026-09-06', description: 'Aporte reserva', amount: 500, categoryId: 'cat-reserva', goalId: RESERVE_GOAL_ID, paid: true },
    { id: 't5', type: 'despesa', month: '2026-09', date: '2026-09-30', description: 'Notebook', amount: 350.33, categoryId: 'cat-compras', recurringId: 'rec-notebook', paid: false },
    { id: 't6', type: 'despesa', month: '2026-10', date: '2026-10-02', description: 'Mercado', amount: 1.005, categoryId: 'cat-mercado', paid: true },
    { id: 't7', type: 'despesa', month: '2026-10', date: '2026-10-03', description: 'Passagem', amount: 300, categoryId: 'cat-viagens', goalId: 'goal-viagem-1', paid: true },
  ]
  data.profile = {
    answers: { objetivo: 2, prazo: 3, queda: 2, experiencia: 1, conhecimento: 1, renda: 1, patrimonio: 1, idade: 1 },
    score: 12,
    profile: 'moderado',
    date: '2026-06-01T12:00:00.000Z',
    custom: { acoes: 20, exterior: 5 },
  }
  return data
}

describe.skipIf(!out)('fixture do backup antigo', () => {
  it('grava um backup criptografado que o próprio app consegue ler de volta', async () => {
    const data = fixtureData()
    const blob = await exportEncryptedBackup(data, FIXTURE_PASSWORD)
    const text = await blob.text()
    expect((await readEncryptedBackup(text, FIXTURE_PASSWORD)).transactions).toHaveLength(7)
    writeFileSync(out!, text, 'utf-8')
  })
})
