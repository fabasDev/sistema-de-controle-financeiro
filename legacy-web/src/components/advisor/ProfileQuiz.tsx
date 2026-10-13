import { useState } from 'react'
import { ArrowLeft, ArrowRight, CircleCheck } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { MAX_SCORE, PROFILES, QUIZ, scoreProfile } from '@/lib/advisor'
import { cn } from '@/lib/utils'
import { useStore } from '@/store/store'
import type { InvestorProfile } from '@/store/types'

export function ProfileQuiz({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[92dvh] overflow-y-auto sm:max-w-lg">{open && <Quiz onDone={() => onOpenChange(false)} />}</DialogContent>
    </Dialog>
  )
}

function Quiz({ onDone }: { onDone: () => void }) {
  const previous = useStore((s) => s.profile)
  const setProfile = useStore((s) => s.setProfile)
  const [step, setStep] = useState(0)
  const [answers, setAnswers] = useState<Record<string, number>>(() => ({ ...previous?.answers }))
  const [result, setResult] = useState<InvestorProfile | null>(null)
  const q = QUIZ[step]
  const chosen = answers[q?.id]

  function next() {
    if (step < QUIZ.length - 1) return setStep(step + 1)
    const { score, profile } = scoreProfile(answers)
    const saved: InvestorProfile = {
      answers,
      score,
      profile,
      date: new Date().toISOString(),
      // Ajustes manuais só são mantidos se o perfil não mudou.
      custom: previous?.profile === profile ? previous.custom : undefined,
    }
    setProfile(saved)
    setResult(saved)
  }

  if (result) {
    const info = PROFILES[result.profile]
    return (
      <div className="grid gap-4">
        <DialogHeader>
          <span className="mb-1 grid size-11 place-items-center rounded-xl bg-success/12 text-success">
            <CircleCheck className="size-6" aria-hidden />
          </span>
          <DialogTitle className="text-xl">Seu perfil é {info.name}</DialogTitle>
          <DialogDescription>
            {result.score} de {MAX_SCORE} pontos
          </DialogDescription>
        </DialogHeader>
        <div className="grid grid-cols-3 gap-1.5" aria-hidden>
          {(['conservador', 'moderado', 'arrojado'] as const).map((id) => (
            <div key={id} className={cn('rounded-lg border px-2 py-2 text-center text-xs', id === result.profile ? 'border-primary bg-accent font-semibold text-foreground' : 'text-muted-foreground')}>
              {PROFILES[id].name}
            </div>
          ))}
        </div>
        <p className="text-sm">{info.summary}</p>
        <p className="text-sm text-muted-foreground">{info.detail}</p>
        <p className="text-xs text-muted-foreground">Refaça o questionário a cada 2 anos ou quando sua situação mudar (novo emprego, filhos, aposentadoria).</p>
        <DialogFooter>
          <Button size="lg" onClick={onDone}>
            Ver minha carteira sugerida
          </Button>
        </DialogFooter>
      </div>
    )
  }

  return (
    <div className="grid gap-4">
      <DialogHeader>
        <div className="mb-2 flex gap-1" aria-hidden>
          {QUIZ.map((x, i) => (
            <span key={x.id} className={cn('h-1.5 flex-1 rounded-full bg-muted', i <= step && 'bg-primary')} />
          ))}
        </div>
        <p className="text-xs font-medium text-muted-foreground">
          Pergunta {step + 1} de {QUIZ.length}
        </p>
        <DialogTitle className="text-lg leading-snug">{q.question}</DialogTitle>
        <DialogDescription>{q.help ?? 'Escolha a opção que mais combina com você.'}</DialogDescription>
      </DialogHeader>

      <RadioGroup
        key={q.id}
        value={chosen !== undefined ? String(chosen) : ''}
        onValueChange={(v) => setAnswers((a) => ({ ...a, [q.id]: Number(v) }))}
        className="grid gap-2"
        aria-label={q.question}
      >
        {q.options.map((o, i) => (
          <Label
            key={o.label}
            htmlFor={`quiz-${q.id}-${i}`}
            className={cn(
              'flex cursor-pointer items-center gap-3 rounded-xl border p-3.5 text-sm font-normal transition-colors hover:bg-muted/50',
              chosen === i && 'border-primary bg-accent/60',
            )}
          >
            <RadioGroupItem id={`quiz-${q.id}-${i}`} value={String(i)} />
            {o.label}
          </Label>
        ))}
      </RadioGroup>

      <DialogFooter className="flex-row justify-between sm:justify-between">
        <Button type="button" variant="ghost" size="lg" onClick={() => setStep(step - 1)} disabled={step === 0}>
          <ArrowLeft aria-hidden /> Voltar
        </Button>
        <Button type="button" size="lg" onClick={next} disabled={chosen === undefined}>
          {step === QUIZ.length - 1 ? 'Ver resultado' : 'Próxima'} <ArrowRight aria-hidden />
        </Button>
      </DialogFooter>
    </div>
  )
}
