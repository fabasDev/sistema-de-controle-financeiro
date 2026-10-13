import { useEffect } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { ArrowLeft, ArrowLeftRight, Calculator, House, LogIn, Sparkles, Target } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

const SHORTCUTS = [
  { to: '/lancamentos', label: 'Lançamentos', icon: ArrowLeftRight },
  { to: '/metas', label: 'Metas', icon: Target },
  { to: '/investir', label: 'Investimentos', icon: Sparkles },
  { to: '/simulador', label: 'Simulador', icon: Calculator },
]

/** Página 404. `standalone` = fora do app (usuário não entrou). */
export default function NotFound({ standalone = false }: { standalone?: boolean }) {
  const location = useLocation()
  const navigate = useNavigate()

  useEffect(() => {
    document.title = 'Página não encontrada · Finanças'
    return () => {
      document.title = 'Finanças'
    }
  }, [])

  return (
    <div className={cn('flex flex-col items-center justify-center px-4 text-center', standalone ? 'min-h-dvh py-12' : 'py-10 sm:py-16')}>
      <EmptyWallet />

      <p className="mt-6 text-sm font-semibold tracking-widest text-primary">ERRO 404</p>
      <h1 className="mt-2 text-3xl font-semibold tracking-tight sm:text-4xl">Página não encontrada</h1>
      <p className="mt-3 max-w-md text-muted-foreground">
        Esse endereço sumiu igual dinheiro sem controle. Fique tranquilo: seus dados continuam guardados e criptografados.
      </p>
      <p className="mt-3 max-w-full truncate rounded-md bg-muted px-2.5 py-1 font-mono text-xs text-muted-foreground" title={location.pathname}>
        {location.pathname}
      </p>

      <div className="mt-8 flex flex-wrap justify-center gap-2">
        <Button size="lg" asChild>
          <Link to="/">
            {standalone ? <LogIn aria-hidden /> : <House aria-hidden />}
            {standalone ? 'Ir para o login' : 'Ir para o início'}
          </Link>
        </Button>
        <Button size="lg" variant="outline" onClick={() => navigate(-1)}>
          <ArrowLeft aria-hidden /> Voltar
        </Button>
      </div>

      {!standalone && (
        <nav aria-label="Atalhos" className="mt-10 w-full max-w-lg">
          <p className="mb-3 text-sm text-muted-foreground">Talvez você estivesse procurando:</p>
          <ul className="grid grid-cols-2 gap-2 sm:grid-cols-4">
            {SHORTCUTS.map((s) => (
              <li key={s.to}>
                <Link
                  to={s.to}
                  className="flex h-full flex-col items-center gap-2 rounded-xl border bg-card px-3 py-4 text-sm font-medium transition-colors hover:border-primary hover:bg-accent/50"
                >
                  <s.icon className="size-5 text-primary" aria-hidden />
                  {s.label}
                </Link>
              </li>
            ))}
          </ul>
        </nav>
      )}
    </div>
  )
}

/** Ilustração: carteira vazia com uma moeda "404" rolando para fora. */
function EmptyWallet() {
  return (
    <svg viewBox="0 0 240 150" className="w-60 max-w-full" role="img" aria-label="Ilustração de uma carteira vazia com uma moeda escrito 404">
      <ellipse cx="112" cy="138" rx="86" ry="7" fill="var(--muted)" />
      <g className="motion-safe:animate-[nf-float_4s_ease-in-out_infinite]">
        <rect x="34" y="40" width="140" height="92" rx="16" fill="var(--card)" stroke="var(--border)" strokeWidth="2" />
        <path d="M34 62h140" stroke="var(--border)" strokeWidth="2" />
        <rect x="128" y="74" width="54" height="30" rx="10" fill="var(--accent)" stroke="var(--border)" strokeWidth="2" />
        <circle cx="145" cy="89" r="5" fill="var(--primary)" />
        <path d="M58 98c6-5 12-5 18 0M90 98c6-5 12-5 18 0" stroke="var(--muted-foreground)" strokeWidth="3" strokeLinecap="round" fill="none" />
        <path d="M78 114c4-3 12-3 16 0" stroke="var(--muted-foreground)" strokeWidth="3" strokeLinecap="round" fill="none" />
      </g>
      <g className="motion-safe:animate-[nf-roll_4s_ease-in-out_infinite]" style={{ transformOrigin: '204px 52px' }}>
        <circle cx="204" cy="52" r="26" fill="var(--chart-4)" />
        <circle cx="204" cy="52" r="20" fill="none" stroke="var(--card)" strokeOpacity="0.7" strokeWidth="2" />
        <text x="204" y="58" textAnchor="middle" fontSize="16" fontWeight="700" fill="#10141f" fontFamily="inherit">
          404
        </text>
      </g>
    </svg>
  )
}
