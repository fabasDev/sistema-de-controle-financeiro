/**
 * Leitura de valores digitados no padrão brasileiro, SEM conta com ponto flutuante:
 * o resultado é um texto decimal ("1234.56") que vai para o servidor, onde vira BigDecimal.
 * Porte de parseBRL (legacy-web/src/lib/format.ts) e espelho de Money.parse no Java.
 */

/**
 * "1.234,56" → "1234.56"; "R$ 2.500" → "2500"; "12.5" → "12.5"; "1.000" → "1000" (milhar).
 * Devolve null quando não há número.
 */
export function parseBRL(input) {
  if (typeof input !== 'string') return null
  let s = input.replace(/[^\d,.-]/g, '')
  if (!s || s === '-') return null
  if (s.includes(',')) {
    s = s.replace(/\./g, '').replace(',', '.')
  } else if ((s.match(/\./g) ?? []).length > 1 || /\.\d{3}$/.test(s)) {
    s = s.replace(/\./g, '')
  }
  return /^-?(\d+\.?\d*|\.\d+)$/.test(s) ? s : null
}

/** Valor válido e maior que zero (só para avisar no formulário; quem decide é o servidor). */
export function isPositiveAmount(input) {
  const s = parseBRL(input)
  return s !== null && !s.startsWith('-') && /[1-9]/.test(s)
}

const brl = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })
const pct = new Intl.NumberFormat('pt-BR', { style: 'percent', maximumFractionDigits: 1 })
const num = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 2 })

/**
 * Formata para a tela um valor que já veio calculado do servidor (JSON). É só exibição:
 * nenhuma conta é feita aqui.
 */
export const formatBRL = (value) => brl.format(Number(value))
export const formatPercent = (ratio) => pct.format(Number(ratio))
export const formatNumber = (value) => num.format(Number(value))

/** "2 anos e 3 meses". Porte de formatDuration. */
export function formatDuration(totalMonths) {
  const years = Math.floor(totalMonths / 12)
  const months = totalMonths % 12
  const parts = []
  if (years) parts.push(`${years} ${years === 1 ? 'ano' : 'anos'}`)
  if (months || !years) parts.push(`${months} ${months === 1 ? 'mês' : 'meses'}`)
  return parts.join(' e ')
}
