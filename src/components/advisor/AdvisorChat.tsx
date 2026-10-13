import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { Bot, Info, SendHorizontal, Sparkles } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { answer, greeting, SUGGESTIONS, type AdvisorContext, type Block, type ChatAction, type Rich } from '@/lib/advisor'
import { cn } from '@/lib/utils'
import { useStore } from '@/store/store'

interface Message {
  id: number
  role: 'user' | 'assistant'
  blocks: Block[]
}

const reducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches

export function AdvisorChat({ ctx, onQuiz, className }: { ctx: AdvisorContext; onQuiz: () => void; className?: string }) {
  const navigate = useNavigate()
  const updateSettings = useStore((s) => s.updateSettings)
  const [messages, setMessages] = useState<Message[]>(() => [{ id: 0, role: 'assistant', blocks: greeting(ctx) }])
  const [text, setText] = useState('')
  const [typing, setTyping] = useState(false)
  const listRef = useRef<HTMLDivElement>(null)
  const ctxRef = useRef(ctx)
  const nextId = useRef(1)

  useEffect(() => {
    ctxRef.current = ctx
  }, [ctx])

  useEffect(() => {
    const el = listRef.current
    if (el) el.scrollTo({ top: el.scrollHeight, behavior: reducedMotion() ? 'auto' : 'smooth' })
  }, [messages, typing])

  function send(question: string) {
    const q = question.trim().slice(0, 300)
    if (!q || typing) return
    setText('')
    setMessages((m) => [...m, { id: nextId.current++, role: 'user', blocks: [{ t: 'p', text: [q] }] }])
    setTyping(true)
    setTimeout(
      () => {
        setMessages((m) => [...m, { id: nextId.current++, role: 'assistant', blocks: answer(q, ctxRef.current) }])
        setTyping(false)
      },
      reducedMotion() ? 0 : 450,
    )
  }

  function run(action: ChatAction) {
    if (action.type === 'quiz') return onQuiz()
    if (action.type === 'ask') return send(action.text)
    if (action.type === 'simulate') {
      updateSettings({ sim: { ...useStore.getState().settings.sim, monthly: action.monthly } })
      return navigate('/simulador')
    }
    const [path, hash] = action.to.split('#')
    if (path === window.location.pathname && hash) {
      document.getElementById(hash)?.scrollIntoView({ behavior: reducedMotion() ? 'auto' : 'smooth', block: 'start' })
    } else navigate(action.to)
  }

  return (
    <div className={cn('flex min-h-0 flex-col', className)}>
      <div ref={listRef} role="log" aria-live="polite" aria-label="Conversa com o assistente" className="min-h-0 flex-1 overflow-y-auto px-4 py-4 sm:px-5">
        <ul className="grid gap-4">
          {messages.map((m) => (
            <li key={m.id} className={cn('flex gap-2.5', m.role === 'user' && 'justify-end')}>
              {m.role === 'assistant' && (
                <span aria-hidden className="mt-0.5 grid size-8 shrink-0 place-items-center rounded-full bg-primary text-primary-foreground">
                  <Bot className="size-4" />
                </span>
              )}
              <div
                className={cn(
                  'grid max-w-[85%] gap-2 rounded-2xl px-3.5 py-2.5 text-sm leading-relaxed',
                  m.role === 'user' ? 'rounded-br-md bg-primary text-primary-foreground' : 'rounded-tl-md bg-muted/70',
                )}
              >
                <span className="sr-only">{m.role === 'user' ? 'Você:' : 'Assistente:'}</span>
                {m.blocks.map((b, i) => (
                  <BlockView key={i} block={b} onAction={run} />
                ))}
              </div>
            </li>
          ))}
          {typing && (
            <li className="flex gap-2.5" aria-label="Assistente digitando">
              <span aria-hidden className="grid size-8 shrink-0 place-items-center rounded-full bg-primary text-primary-foreground">
                <Bot className="size-4" />
              </span>
              <span className="flex items-center gap-1 rounded-2xl rounded-tl-md bg-muted/70 px-4 py-3" aria-hidden>
                {[0, 1, 2].map((i) => (
                  <span key={i} className="size-1.5 animate-bounce rounded-full bg-muted-foreground" style={{ animationDelay: `${i * 120}ms` }} />
                ))}
              </span>
            </li>
          )}
        </ul>
      </div>

      <div className="border-t px-4 pt-3 pb-4 sm:px-5">
        {messages.length === 1 && (
          <div className="mb-3 flex flex-wrap gap-1.5" role="group" aria-label="Exemplos de perguntas">
            {SUGGESTIONS.map((s) => (
              <Button key={s} type="button" variant="outline" size="sm" className="rounded-full" onClick={() => send(s)} disabled={typing}>
                {s}
              </Button>
            ))}
          </div>
        )}
        <form
          className="flex gap-2"
          onSubmit={(e) => {
            e.preventDefault()
            send(text)
          }}
        >
          <Input
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder="Escreva sua pergunta…"
            aria-label="Sua pergunta"
            maxLength={300}
            autoComplete="off"
            enterKeyHint="send"
            className="h-11"
          />
          <Button type="submit" size="icon" className="size-11 shrink-0" disabled={!text.trim() || typing} aria-label="Enviar pergunta">
            <SendHorizontal aria-hidden />
          </Button>
        </form>
        <p className="mt-2 flex items-center gap-1.5 text-[11px] text-muted-foreground">
          <Sparkles className="size-3" aria-hidden /> Funciona sem internet. Conteúdo educativo, não é recomendação de investimento.
        </p>
      </div>
    </div>
  )
}

function RichText({ parts }: { parts: Rich[] }) {
  return (
    <>
      {parts.map((part, i) => (typeof part === 'string' ? <span key={i}>{part}</span> : <strong key={i} className="font-semibold">{part.b}</strong>))}
    </>
  )
}

function BlockView({ block, onAction }: { block: Block; onAction: (a: ChatAction) => void }) {
  switch (block.t) {
    case 'p':
      return (
        <p>
          <RichText parts={block.text} />
        </p>
      )
    case 'ul':
      return (
        <ul className="grid list-disc gap-1 pl-4">
          {block.items.map((item, i) => (
            <li key={i}>
              <RichText parts={item} />
            </li>
          ))}
        </ul>
      )
    case 'kv':
      return (
        <dl className="grid gap-1 rounded-lg bg-card px-3 py-2 text-[13px]">
          {block.rows.map(([k, v]) => (
            <div key={k} className="flex items-baseline justify-between gap-3">
              <dt className="text-muted-foreground">{k}</dt>
              <dd className="font-medium whitespace-nowrap tabular">{v}</dd>
            </div>
          ))}
        </dl>
      )
    case 'actions':
      return (
        <div className="flex flex-wrap gap-1.5 pt-0.5">
          {block.actions.map((a) => (
            <Button
              key={a.label}
              type="button"
              size="sm"
              variant={a.action.type === 'quiz' ? 'default' : 'outline'}
              className={cn('h-auto min-h-8 whitespace-normal py-1.5 text-left', a.action.type !== 'quiz' && 'bg-card')}
              onClick={() => onAction(a.action)}
            >
              {a.label}
            </Button>
          ))}
        </div>
      )
    case 'note':
      return (
        <p className="flex items-start gap-1.5 text-xs text-muted-foreground">
          <Info className="mt-0.5 size-3.5 shrink-0" aria-hidden /> {block.text}
        </p>
      )
  }
}
