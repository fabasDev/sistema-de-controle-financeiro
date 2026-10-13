import { useState } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { currentMonth, monthLabel, monthName, parseMonth } from '@/lib/month'
import { cn } from '@/lib/utils'
import { useUI } from '@/store/store'

export function MonthPicker({ className }: { className?: string }) {
  const month = useUI((s) => s.month)
  const setMonth = useUI((s) => s.setMonth)
  const shiftMonth = useUI((s) => s.shiftMonth)
  const [open, setOpen] = useState(false)
  const [year, setYear] = useState(() => parseMonth(month).year)
  const today = currentMonth()
  const selected = parseMonth(month)

  return (
    <div className={cn('flex items-center gap-1', className)}>
      <Button variant="ghost" size="icon" className="size-9" onClick={() => shiftMonth(-1)} aria-label="Mês anterior">
        <ChevronLeft />
      </Button>
      <Popover
        open={open}
        onOpenChange={(o) => {
          setOpen(o)
          if (o) setYear(selected.year)
        }}
      >
        <PopoverTrigger asChild>
          <Button variant="outline" className="h-9 min-w-40 justify-center px-3 font-medium tabular" aria-label={`Mês selecionado: ${monthLabel(month)}. Escolher outro mês`}>
            {monthLabel(month)}
          </Button>
        </PopoverTrigger>
        <PopoverContent className="w-72 p-3" align="center">
          <div className="mb-2 flex items-center justify-between">
            <Button variant="ghost" size="icon" onClick={() => setYear((y) => y - 1)} aria-label="Ano anterior">
              <ChevronLeft />
            </Button>
            <span className="font-medium tabular">{year}</span>
            <Button variant="ghost" size="icon" onClick={() => setYear((y) => y + 1)} aria-label="Próximo ano">
              <ChevronRight />
            </Button>
          </div>
          <div className="grid grid-cols-3 gap-1.5">
            {Array.from({ length: 12 }, (_, i) => {
              const key = `${year}-${String(i + 1).padStart(2, '0')}`
              const active = key === month
              return (
                <Button
                  key={key}
                  variant={active ? 'default' : 'ghost'}
                  className={cn('h-9', key === today && !active && 'ring-1 ring-primary/40')}
                  aria-pressed={active}
                  onClick={() => {
                    setMonth(key)
                    setOpen(false)
                  }}
                >
                  {monthName(i + 1).slice(0, 3)}
                </Button>
              )
            })}
          </div>
          {month !== today && (
            <Button
              variant="link"
              className="mt-2 w-full"
              onClick={() => {
                setMonth(today)
                setOpen(false)
              }}
            >
              Voltar para o mês atual
            </Button>
          )}
        </PopoverContent>
      </Popover>
      <Button variant="ghost" size="icon" className="size-9" onClick={() => shiftMonth(1)} aria-label="Próximo mês">
        <ChevronRight />
      </Button>
    </div>
  )
}
