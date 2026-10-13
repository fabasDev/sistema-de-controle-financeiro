/** Mês no formato YYYY-MM (ordenável como string). */
export type MonthKey = string

const pad = (n: number) => String(n).padStart(2, '0')

export function toMonthKey(d: Date): MonthKey {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}`
}

export function currentMonth(): MonthKey {
  return toMonthKey(new Date())
}

export function parseMonth(key: MonthKey) {
  const [year, month] = key.split('-').map(Number)
  return { year, month }
}

export function addMonths(key: MonthKey, n: number): MonthKey {
  const { year, month } = parseMonth(key)
  return toMonthKey(new Date(year, month - 1 + n, 1))
}

/** Quantos meses de `from` até `to` (positivo se `to` for depois). */
export function diffMonths(from: MonthKey, to: MonthKey): number {
  const a = parseMonth(from)
  const b = parseMonth(to)
  return (b.year - a.year) * 12 + (b.month - a.month)
}

/** `count` meses terminando em `end`, do mais antigo para o mais recente. */
export function monthRange(end: MonthKey, count: number): MonthKey[] {
  return Array.from({ length: count }, (_, i) => addMonths(end, i - count + 1))
}

const longFmt = new Intl.DateTimeFormat('pt-BR', { month: 'long', year: 'numeric' })
const shortFmt = new Intl.DateTimeFormat('pt-BR', { month: 'short' })
const monthNameFmt = new Intl.DateTimeFormat('pt-BR', { month: 'long' })

const capitalize = (s: string) => s.charAt(0).toUpperCase() + s.slice(1)

/** "Outubro de 2026" */
export function monthLabel(key: MonthKey): string {
  const { year, month } = parseMonth(key)
  return capitalize(longFmt.format(new Date(year, month - 1, 1)))
}

/** "out/26" */
export function monthShort(key: MonthKey): string {
  const { year, month } = parseMonth(key)
  const m = shortFmt.format(new Date(year, month - 1, 1)).replace('.', '')
  return `${m}/${String(year).slice(2)}`
}

/** "Outubro" */
export function monthName(month: number): string {
  return capitalize(monthNameFmt.format(new Date(2000, month - 1, 1)))
}

export function daysInMonth(key: MonthKey): number {
  const { year, month } = parseMonth(key)
  return new Date(year, month, 0).getDate()
}

/** Data YYYY-MM-DD dentro do mês, limitando o dia ao último dia do mês. */
export function dateInMonth(key: MonthKey, day: number): string {
  return `${key}-${pad(Math.min(Math.max(1, day), daysInMonth(key)))}`
}

export function todayISO(): string {
  const d = new Date()
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

const dayFmt = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: 'short' })
const weekdayFmt = new Intl.DateTimeFormat('pt-BR', { weekday: 'long', day: '2-digit', month: 'long' })

function isoToDate(iso: string) {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(y, m - 1, d)
}

/** "05 de out." */
export function dayLabel(iso: string): string {
  return dayFmt.format(isoToDate(iso))
}

/** "Segunda-feira, 05 de outubro" */
export function longDayLabel(iso: string): string {
  return capitalize(weekdayFmt.format(isoToDate(iso)))
}
