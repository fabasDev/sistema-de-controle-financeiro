import { beforeEach, describe, expect, it } from 'vitest'
import { checkPassword, generateRecoveryCode, normalizeRecoveryCode } from '@/lib/crypto'
import * as vault from '@/lib/vault'
import { createInitialData } from '@/lib/presets'
import { exportEncryptedBackup, readEncryptedBackup } from '@/store/backup'

const mem = new Map<string, string>()
vault.setVaultStorage({
  getItem: (k) => mem.get(k) ?? null,
  setItem: (k, v) => void mem.set(k, v),
  removeItem: (k) => void mem.delete(k),
})

const raw = () => mem.get(vault.VAULT_KEY) ?? ''
const PW = 'Cofre-Seguro-2026!'

beforeEach(() => mem.clear())

describe('cofre local', () => {
  it('cria conta, salva e lê dados sem nada legível no armazenamento', async () => {
    const { session, recoveryCode } = await vault.createAccount('Maria', PW)
    expect(recoveryCode).toMatch(/^([0-9A-HJKMNP-TV-Z]{4}-){7}[0-9A-HJKMNP-TV-Z]{4}$/)
    await vault.saveData(session, { segredo: 'Salário 12345,67' })

    expect(raw()).not.toContain('maria')
    expect(raw()).not.toContain('Salário')
    expect(raw()).not.toContain(PW)
    expect(raw()).not.toContain(recoveryCode)

    const again = await vault.login('  MARIA ', PW)
    expect(await vault.loadData(again)).toEqual({ segredo: 'Salário 12345,67' })
  })

  it('senha errada e usuário inexistente dão a mesma mensagem', async () => {
    await vault.createAccount('joao', PW)
    await expect(vault.login('joao', 'errada-123')).rejects.toThrow('Usuário ou senha incorretos.')
    await expect(vault.login('ninguem', PW)).rejects.toThrow('Usuário ou senha incorretos.')
  })

  it('bloqueia temporariamente após 5 tentativas erradas', async () => {
    await vault.createAccount('ana', PW)
    for (let i = 0; i < 5; i++) await expect(vault.login('ana', `x${i}xxxxxx`)).rejects.toThrow()
    await expect(vault.login('ana', PW)).rejects.toThrow('Muitas tentativas')
  })

  it('detecta adulteração dos dados', async () => {
    const { session } = await vault.createAccount('bia', PW)
    await vault.saveData(session, { a: 1 })
    const file = JSON.parse(raw())
    const bytes = atob(file.accounts[0].payload.data)
    file.accounts[0].payload.data = btoa(String.fromCharCode(bytes.charCodeAt(0) ^ 1) + bytes.slice(1))
    mem.set(vault.VAULT_KEY, JSON.stringify(file))
    await expect(vault.loadData(session)).rejects.toThrow()
  })

  it('recupera o acesso com o código e invalida o código antigo', async () => {
    const { session, recoveryCode } = await vault.createAccount('caio', PW)
    await vault.saveData(session, { ok: true })
    const typed = recoveryCode.toLowerCase().replace(/-/g, ' ')
    const result = await vault.resetWithRecovery('caio', typed, 'Nova-Senha-Forte-99')
    expect(await vault.loadData(result.session)).toEqual({ ok: true })
    expect(result.recoveryCode).not.toBe(recoveryCode)
    await expect(vault.login('caio', PW)).rejects.toThrow()
    await expect(vault.resetWithRecovery('caio', recoveryCode, 'Outra-Senha-123!')).rejects.toThrow()
  })

  it('troca de senha mantém os dados', async () => {
    const { session } = await vault.createAccount('dani', PW)
    await vault.saveData(session, [1, 2, 3])
    await expect(vault.changePassword(session.accountId, 'errada', 'X')).rejects.toThrow('Senha atual incorreta.')
    await vault.changePassword(session.accountId, PW, 'Trocada-Senha-77!')
    const s2 = await vault.login('dani', 'Trocada-Senha-77!')
    expect(await vault.loadData(s2)).toEqual([1, 2, 3])
  })

  it('não permite usuário duplicado', async () => {
    await vault.createAccount('edu', PW)
    await expect(vault.createAccount('EDU', PW)).rejects.toThrow('Já existe')
  })
})

describe('backup criptografado', () => {
  it('ida e volta com a senha certa; falha com a errada', async () => {
    const data = createInitialData()
    const text = await (await exportEncryptedBackup(data, 'senha-do-backup-1')).text()
    expect(text).not.toContain('Moradia')
    expect(await readEncryptedBackup(text, 'senha-do-backup-1')).toEqual(data)
    await expect(readEncryptedBackup(text, 'errada')).rejects.toThrow('Senha do backup incorreta')
  })
})

describe('utilitários', () => {
  it('normaliza o código de recuperação digitado', () => {
    const code = generateRecoveryCode()
    expect(normalizeRecoveryCode(code.toLowerCase().replace(/-/g, ''))).toBe(code)
    expect(normalizeRecoveryCode('abco-il00')).toBe('ABC0-1100')
  })

  it('avalia a força da senha', () => {
    expect(checkPassword('123').ok).toBe(false)
    expect(checkPassword('senha123').ok).toBe(false)
    expect(checkPassword('aaaaaaaaaaaa').ok).toBe(false)
    expect(checkPassword('maria-2026-x', 'maria').ok).toBe(false)
    expect(checkPassword('Girassol-Azul-42').ok).toBe(true)
  })
})
