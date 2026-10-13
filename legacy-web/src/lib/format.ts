const brl = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })
const brlInt = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
  maximumFractionDigits: 0,
})
const brlCompact = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
  notation: 'compact',
  maximumFractionDigits: 1,
})
const amount = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const pct = new Intl.NumberFormat('pt-BR', { style: 'percent', maximumFractionDigits: 1 })
const num = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 })

/** R$ 1.234,56 */
export const formatBRL = (v: number) => brl.format(v)

/** R$ 1.235 */
export const formatBRLInt = (v: number) => brlInt.format(v)

/** R$ 1,2 mil — para eixos de gráficos. */
export const formatBRLCompact = (v: number) => (Math.abs(v) < 1000 ? brlInt.format(v) : brlCompact.format(v))

/** 1.234,56 — para campos de entrada. */
export const formatAmount = (v: number) => amount.format(v)

/** Recebe uma razão (0.25) e devolve "25%". */
export const formatPercent = (ratio: number) => pct.format(Number.isFinite(ratio) ? ratio : 0)

export const formatNumber = (v: number) => num.format(v)

/** Valor com sinal explícito: "+ R$ 10,00" / "− R$ 10,00". */
export function formatSigned(v: number) {
  if (v === 0) return brl.format(0)
  return `${v > 0 ? '+' : '−'} ${brl.format(Math.abs(v))}`
}

/**
 * Converte texto digitado no padrão brasileiro em número.
 * Aceita "1.234,56", "1234,56", "1234.56", "R$ 1.234" e "1.234" (milhar).
 */
export function parseBRL(input: string): number | null {
  let s = input.replace(/[^\d,.-]/g, '')
  if (!s || s === '-') return null
  if (s.includes(',')) {
    s = s.replace(/\./g, '').replace(',', '.')
  } else if ((s.match(/\./g) ?? []).length > 1 || /\.\d{3}$/.test(s)) {
    s = s.replace(/\./g, '')
  }
  const n = Number(s)
  return Number.isFinite(n) ? Math.round(n * 100) / 100 : null
}

export function pluralize(n: number, singular: string, plural: string) {
  return `${formatNumber(n)} ${Math.abs(n) === 1 ? singular : plural}`
}

/** "2 anos e 3 meses" */
export function formatDuration(totalMonths: number) {
  const years = Math.floor(totalMonths / 12)
  const months = totalMonths % 12
  const parts: string[] = []
  if (years) parts.push(pluralize(years, 'ano', 'anos'))
  if (months || !years) parts.push(pluralize(months, 'mês', 'meses'))
  return parts.join(' e ')
}
