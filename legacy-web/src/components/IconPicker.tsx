import { ICON_LABELS, ICON_NAMES, ICONS } from '@/lib/icons'
import { cn } from '@/lib/utils'

/** Grade de ícones selecionáveis (radiogroup acessível por teclado). */
export function IconPicker({ value, onChange, labelledBy }: { value: string; onChange: (icon: string) => void; labelledBy: string }) {
  function move(e: React.KeyboardEvent, index: number) {
    const cols = 8
    const delta = { ArrowRight: 1, ArrowLeft: -1, ArrowDown: cols, ArrowUp: -cols }[e.key]
    if (!delta) return
    e.preventDefault()
    const next = Math.max(0, Math.min(ICON_NAMES.length - 1, index + delta))
    onChange(ICON_NAMES[next])
    const buttons = (e.currentTarget.parentElement as HTMLElement).querySelectorAll<HTMLButtonElement>('button')
    buttons[next]?.focus()
  }

  return (
    <div role="radiogroup" aria-labelledby={labelledBy} className="grid max-h-44 grid-cols-8 gap-1 overflow-y-auto rounded-lg border p-1.5">
      {ICON_NAMES.map((name, i) => {
        const Icon = ICONS[name]
        const selected = name === value
        return (
          <button
            key={name}
            type="button"
            role="radio"
            aria-checked={selected}
            aria-label={ICON_LABELS[name] ?? name}
            title={ICON_LABELS[name] ?? name}
            tabIndex={selected ? 0 : -1}
            onClick={() => onChange(name)}
            onKeyDown={(e) => move(e, i)}
            className={cn(
              'grid aspect-square place-items-center rounded-md text-muted-foreground transition-colors hover:bg-muted hover:text-foreground',
              selected && 'bg-primary text-primary-foreground hover:bg-primary hover:text-primary-foreground',
            )}
          >
            <Icon className="size-4" aria-hidden />
          </button>
        )
      })}
    </div>
  )
}
