import { useState, type ReactNode } from 'react'
import { useShallow } from 'zustand/react/shallow'
import { Input } from '@/components/ui/input'
import { formatAmount, formatBRL, parseBRL } from '@/lib/format'
import { getIcon } from '@/lib/icons'
import { cn } from '@/lib/utils'
import { selectData, useStore } from '@/store/store'
import type { DataState } from '@/store/types'

/** Dados financeiros atuais (re-renderiza só quando algo muda). */
export function useData(): DataState {
  return useStore(useShallow(selectData))
}

/** Cor de identidade do pote pela posição (paleta validada, ordem fixa). */
export const slotColor = (index: number) => `var(--chart-${(index % 6) + 1})`

// ---------- Campo de valor em reais ----------

interface CurrencyInputProps extends Omit<React.ComponentProps<'input'>, 'value' | 'onChange' | 'type'> {
  value: number | undefined
  onValueChange: (value: number | undefined) => void
}

export function CurrencyInput({ value, onValueChange, className, onFocus, onBlur, ...props }: CurrencyInputProps) {
  const [text, setText] = useState('')
  const [focused, setFocused] = useState(false)
  const display = focused ? text : value ? formatAmount(value) : ''
  return (
    <div className="relative">
      <span
        aria-hidden
        className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-sm text-muted-foreground"
      >
        R$
      </span>
      <Input
        {...props}
        type="text"
        inputMode="decimal"
        autoComplete="off"
        placeholder={props.placeholder ?? '0,00'}
        className={cn('pl-9 tabular', className)}
        value={display}
        onFocus={(e) => {
          setText(value ? formatAmount(value) : '')
          setFocused(true)
          const el = e.currentTarget
          requestAnimationFrame(() => el.select())
          onFocus?.(e)
        }}
        onChange={(e) => {
          const next = e.target.value.replace(/[^\d.,]/g, '')
          setText(next)
          onValueChange(parseBRL(next) ?? undefined)
        }}
        onBlur={(e) => {
          setFocused(false)
          onBlur?.(e)
        }}
      />
    </div>
  )
}

const pctFmt = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 2 })

/** Campo numérico com sufixo (ex.: "%"), aceitando vírgula decimal. */
export function NumberInput({
  value,
  onValueChange,
  suffix = '%',
  className,
  ...props
}: Omit<React.ComponentProps<'input'>, 'value' | 'onChange' | 'type'> & {
  value: number
  onValueChange: (value: number) => void
  suffix?: string
}) {
  const [text, setText] = useState('')
  const [focused, setFocused] = useState(false)
  return (
    <div className="relative">
      <Input
        {...props}
        type="text"
        inputMode="decimal"
        autoComplete="off"
        className={cn('pr-8 tabular', className)}
        value={focused ? text : pctFmt.format(value)}
        onFocus={(e) => {
          setText(pctFmt.format(value))
          setFocused(true)
          const el = e.currentTarget
          requestAnimationFrame(() => el.select())
        }}
        onChange={(e) => {
          const next = e.target.value.replace(/[^\d.,]/g, '')
          setText(next)
          onValueChange(parseBRL(next) ?? 0)
        }}
        onBlur={() => setFocused(false)}
      />
      <span aria-hidden className="pointer-events-none absolute top-1/2 right-3 -translate-y-1/2 text-sm text-muted-foreground">
        {suffix}
      </span>
    </div>
  )
}

// ---------- Ícone de categoria ----------

export function CategoryIcon({
  icon,
  color,
  className,
  size = 'md',
}: {
  icon?: string
  color?: string
  className?: string
  size?: 'sm' | 'md' | 'lg'
}) {
  const Icon = getIcon(icon)
  const box = { sm: 'size-7 rounded-md', md: 'size-9 rounded-lg', lg: 'size-11 rounded-xl' }[size]
  const glyph = { sm: 'size-3.5', md: 'size-4', lg: 'size-5' }[size]
  return (
    <span
      aria-hidden
      className={cn('grid shrink-0 place-items-center bg-muted text-foreground/80', box, className)}
      style={color ? { background: `color-mix(in oklab, ${color} 16%, transparent)`, color: 'var(--foreground)' } : undefined}
    >
      <Icon className={glyph} />
    </span>
  )
}

// ---------- Barra de progresso / medidor ----------

export function Meter({
  value,
  max,
  color = 'var(--chart-1)',
  label,
  className,
}: {
  value: number
  max: number
  color?: string
  label: string
  className?: string
}) {
  const ratio = max > 0 ? Math.min(1, Math.max(0, value / max)) : 0
  return (
    <div
      role="progressbar"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={Math.round(max)}
      aria-valuenow={Math.round(Math.min(value, max))}
      className={cn('h-2 w-full overflow-hidden rounded-full', className)}
      style={{ background: `color-mix(in oklab, ${color} 18%, transparent)` }}
    >
      <div
        className="h-full rounded-full transition-[width] duration-500 ease-out"
        style={{ width: `${ratio * 100}%`, background: color }}
      />
    </div>
  )
}

// ---------- Cabeçalho de página ----------

export function PageHeader({ title, description, actions }: { title: string; description?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
      <div className="min-w-0">
        <h1 className="text-2xl font-semibold tracking-tight sm:text-[1.75rem]">{title}</h1>
        {description && <p className="mt-1 text-sm text-muted-foreground">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  )
}

// ---------- Estado vazio ----------

export function EmptyState({
  icon,
  title,
  description,
  action,
  className,
}: {
  icon?: string
  title: string
  description?: ReactNode
  action?: ReactNode
  className?: string
}) {
  return (
    <div className={cn('flex flex-col items-center justify-center gap-3 px-6 py-10 text-center', className)}>
      {icon && <CategoryIcon icon={icon} size="lg" />}
      <div>
        <p className="font-medium">{title}</p>
        {description && <p className="mt-1 max-w-sm text-sm text-muted-foreground">{description}</p>}
      </div>
      {action}
    </div>
  )
}

// ---------- Tooltip dos gráficos ----------

interface TipItem {
  name?: string | number
  value?: unknown
  color?: string
  dataKey?: unknown
}

export interface ChartTipProps {
  active?: boolean
  payload?: readonly TipItem[]
  label?: unknown
  labelFormatter?: (label: unknown) => ReactNode
  valueFormatter?: (value: number) => string
}

export function ChartTip({ active, payload, label, labelFormatter, valueFormatter = formatBRL }: ChartTipProps) {
  if (!active || !payload?.length) return null
  return (
    <div className="min-w-44 rounded-lg border bg-popover px-3 py-2 text-xs text-popover-foreground shadow-lg">
      <p className="mb-1.5 font-medium">{labelFormatter ? labelFormatter(label) : String(label ?? '')}</p>
      <div className="grid gap-1">
        {payload.map((p) => (
          <div key={String(p.dataKey ?? p.name)} className="flex items-center gap-2">
            <span aria-hidden className="size-2.5 shrink-0 rounded-[3px]" style={{ background: p.color }} />
            <span className="text-muted-foreground">{p.name}</span>
            <span className="ml-auto pl-3 font-medium tabular">{valueFormatter(Number(p.value))}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

/** Legenda HTML (sempre visível, com valores) para gráficos com 2+ séries. */
export function Legend({ items }: { items: { label: string; color: string; value?: string; dashed?: boolean }[] }) {
  return (
    <ul className="flex flex-wrap gap-x-4 gap-y-1.5 text-xs">
      {items.map((i) => (
        <li key={i.label} className="flex items-center gap-1.5">
          <span
            aria-hidden
            className={cn('shrink-0', i.dashed ? 'h-0 w-3 border-t-2 border-dashed' : 'size-2.5 rounded-[3px]')}
            style={i.dashed ? { borderColor: i.color } : { background: i.color }}
          />
          <span className="text-muted-foreground">{i.label}</span>
          {i.value && <span className="font-medium tabular">{i.value}</span>}
        </li>
      ))}
    </ul>
  )
}
