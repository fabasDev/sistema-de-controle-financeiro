import { Suspense, useEffect, useRef, useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router'
import {
  ArrowLeftRight,
  Calculator,
  ChartPie,
  Ellipsis,
  History,
  LayoutDashboard,
  Lock,
  Monitor,
  Moon,
  Plus,
  Repeat,
  Settings,
  Sparkles,
  Sun,
  Target,
  TriangleAlert,
  Wallet,
  type LucideIcon,
} from 'lucide-react'
import { ErrorBoundary } from '@/components/ErrorBoundary'
import { MonthPicker } from '@/components/MonthPicker'
import { TransactionDialog } from '@/components/TransactionDialog'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { useTheme, type ThemePref } from '@/lib/theme'
import { cn } from '@/lib/utils'
import { useSession } from '@/store/session'
import { useStore, useUI } from '@/store/store'

interface NavItem {
  to: string
  label: string
  /** Rótulo curto na barra inferior do celular. */
  short?: string
  icon: LucideIcon
  mobile?: boolean
  month?: boolean
}

const NAV: NavItem[] = [
  { to: '/', label: 'Visão geral', short: 'Início', icon: LayoutDashboard, mobile: true, month: true },
  { to: '/lancamentos', label: 'Lançamentos', icon: ArrowLeftRight, mobile: true, month: true },
  { to: '/planejamento', label: 'Planejamento', icon: ChartPie, month: true },
  { to: '/contas-fixas', label: 'Contas fixas', icon: Repeat },
  { to: '/metas', label: 'Metas', icon: Target, mobile: true, month: true },
  { to: '/investir', label: 'Investimentos', short: 'Investir', icon: Sparkles, mobile: true, month: true },
  { to: '/simulador', label: 'Simulador', icon: Calculator },
  { to: '/historico', label: 'Histórico', icon: History, month: true },
  { to: '/ajustes', label: 'Ajustes', icon: Settings },
]

const THEMES: { value: ThemePref; label: string; icon: LucideIcon }[] = [
  { value: 'system', label: 'Automático', icon: Monitor },
  { value: 'light', label: 'Claro', icon: Sun },
  { value: 'dark', label: 'Escuro', icon: Moon },
]

function Brand() {
  return (
    <div className="flex items-center gap-2.5">
      <span className="grid size-9 place-items-center rounded-xl bg-primary text-primary-foreground">
        <Wallet className="size-5" aria-hidden />
      </span>
      <span className="text-lg font-semibold tracking-tight">Finanças</span>
    </div>
  )
}

function UserMenu() {
  const username = useSession((s) => s.session?.username)
  const displayName = useStore((s) => s.settings.userName)
  const logout = useSession((s) => s.logout)
  const { pref, setPref } = useTheme()
  const ThemeIcon = THEMES.find((t) => t.value === pref)!.icon
  const initial = (displayName || username || '?').charAt(0).toUpperCase()
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" className="size-10 rounded-full p-0" aria-label="Menu da conta">
          <span className="grid size-8 place-items-center rounded-full bg-accent text-sm font-semibold text-accent-foreground">
            {initial}
          </span>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-60">
        <DropdownMenuLabel className="font-normal">
          <p className="truncate font-medium">{displayName || username}</p>
          <p className="truncate text-xs text-muted-foreground">Conta local · dados criptografados</p>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuLabel className="flex items-center gap-2 text-xs text-muted-foreground">
          <ThemeIcon className="size-3.5" aria-hidden /> Tema
        </DropdownMenuLabel>
        <DropdownMenuRadioGroup value={pref} onValueChange={(v) => setPref(v as ThemePref)}>
          {THEMES.map((t) => (
            <DropdownMenuRadioItem key={t.value} value={t.value}>
              {t.label}
            </DropdownMenuRadioItem>
          ))}
        </DropdownMenuRadioGroup>
        <DropdownMenuSeparator />
        <DropdownMenuItem asChild>
          <NavLink to="/ajustes">
            <Settings /> Ajustes e segurança
          </NavLink>
        </DropdownMenuItem>
        <DropdownMenuItem onSelect={() => void logout()}>
          <Lock /> Sair e bloquear
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

function SideNavLink({ item, onClick }: { item: NavItem; onClick?: () => void }) {
  return (
    <NavLink
      to={item.to}
      end={item.to === '/'}
      onClick={onClick}
      className={({ isActive }) =>
        cn(
          'flex h-10 items-center gap-3 rounded-lg px-3 text-sm font-medium text-muted-foreground transition-colors hover:bg-sidebar-accent hover:text-foreground',
          isActive && 'bg-sidebar-accent text-foreground',
        )
      }
    >
      {({ isActive }) => (
        <>
          <item.icon className={cn('size-[18px]', isActive && 'text-primary')} aria-hidden />
          {item.label}
        </>
      )}
    </NavLink>
  )
}

export function AppShell() {
  const location = useLocation()
  const month = useUI((s) => s.month)
  const openTx = useUI((s) => s.openTx)
  const ensureMonth = useStore((s) => s.ensureMonth)
  const recurring = useStore((s) => s.recurring)
  const saveError = useSession((s) => s.saveError)
  const [moreOpen, setMoreOpen] = useState(false)
  const mainRef = useRef<HTMLElement>(null)
  const current = NAV.find((n) => (n.to === '/' ? location.pathname === '/' : location.pathname.startsWith(n.to)))

  // Contas fixas aparecem automaticamente no mês visualizado.
  useEffect(() => {
    ensureMonth(month)
  }, [month, recurring, ensureMonth])

  // Leitores de tela: leva o foco ao conteúdo ao trocar de página.
  useEffect(() => {
    mainRef.current?.focus({ preventScroll: true })
    window.scrollTo({ top: 0 })
  }, [location.pathname])

  return (
    <div className="min-h-dvh">
      <a
        href="#conteudo"
        className="sr-only z-50 rounded-md bg-primary px-3 py-2 text-primary-foreground focus:not-sr-only focus:fixed focus:top-2 focus:left-2"
      >
        Pular para o conteúdo
      </a>

      {/* Barra lateral (desktop) */}
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 flex-col border-r bg-sidebar px-4 py-5 lg:flex">
        <Brand />
        <nav aria-label="Principal" className="mt-8 grid gap-1">
          {NAV.slice(0, -1).map((item) => (
            <SideNavLink key={item.to} item={item} />
          ))}
        </nav>
        <div className="mt-auto grid gap-1">
          <SideNavLink item={NAV.at(-1)!} />
          <p className="mt-3 flex items-center gap-2 px-3 text-xs text-muted-foreground">
            <Lock className="size-3.5" aria-hidden /> Dados criptografados neste aparelho
          </p>
          <NavLink to="/privacidade" className="px-3 text-xs text-muted-foreground underline-offset-4 hover:text-foreground hover:underline">
            Política de privacidade
          </NavLink>
        </div>
      </aside>

      <div className="lg:pl-64">
        <header className="sticky top-0 z-20 border-b bg-background/80 backdrop-blur-md supports-[backdrop-filter]:bg-background/70">
          <div className="mx-auto flex h-16 max-w-6xl items-center gap-2 px-4 sm:px-6">
            <div className="lg:hidden">
              <Brand />
            </div>
            <div className="hidden min-w-0 text-sm text-muted-foreground lg:block">{current?.label}</div>
            <div className="ml-auto flex items-center gap-2">
              {current?.month && <MonthPicker className="hidden sm:flex" />}
              <Button className="hidden h-9 lg:inline-flex" onClick={() => openTx()}>
                <Plus aria-hidden /> Novo lançamento
              </Button>
              <UserMenu />
            </div>
          </div>
          {current?.month && (
            <div className="flex justify-center border-t px-2 py-1.5 sm:hidden">
              <MonthPicker />
            </div>
          )}
        </header>

        {saveError && (
          <div role="alert" className="mx-auto mt-4 flex max-w-6xl items-center gap-2 px-4 text-sm text-danger sm:px-6">
            <TriangleAlert className="size-4 shrink-0" aria-hidden /> {saveError}
          </div>
        )}

        <main
          id="conteudo"
          ref={mainRef}
          tabIndex={-1}
          className="mx-auto max-w-6xl px-4 pt-6 pb-28 outline-none sm:px-6 lg:pb-12"
        >
          <ErrorBoundary resetKey={location.pathname}>
            <Suspense fallback={<div className="h-96 animate-pulse rounded-xl bg-muted/50" aria-busy="true" aria-label="Carregando" />}>
              <Outlet />
            </Suspense>
          </ErrorBoundary>
        </main>
      </div>

      {/* Botão flutuante (celular) */}
      <Button
        size="icon"
        className="fixed right-4 bottom-[calc(5rem+env(safe-area-inset-bottom))] z-30 size-14 rounded-full shadow-lg lg:hidden"
        onClick={() => openTx()}
        aria-label="Novo lançamento"
      >
        <Plus className="size-6" aria-hidden />
      </Button>

      {/* Navegação inferior (celular) */}
      <nav
        aria-label="Principal"
        className="fixed inset-x-0 bottom-0 z-30 border-t bg-background/85 pb-[env(safe-area-inset-bottom)] backdrop-blur-md lg:hidden"
      >
        <ul className="mx-auto grid max-w-lg grid-cols-5">
          {NAV.filter((n) => n.mobile).map((item) => (
            <li key={item.to}>
              <NavLink
                to={item.to}
                end={item.to === '/'}
                className={({ isActive }) =>
                  cn(
                    'flex h-16 flex-col items-center justify-center gap-1 text-[11px] font-medium text-muted-foreground',
                    isActive && 'text-primary',
                  )
                }
              >
                <item.icon className="size-5" aria-hidden />
                {item.short ?? item.label}
              </NavLink>
            </li>
          ))}
          <li>
            <button
              type="button"
              onClick={() => setMoreOpen(true)}
              className={cn(
                'flex h-16 w-full flex-col items-center justify-center gap-1 text-[11px] font-medium text-muted-foreground',
                current && !current.mobile && 'text-primary',
              )}
              aria-haspopup="dialog"
            >
              <Ellipsis className="size-5" aria-hidden />
              Mais
            </button>
          </li>
        </ul>
      </nav>

      <Sheet open={moreOpen} onOpenChange={setMoreOpen}>
        <SheetContent side="bottom" className="rounded-t-2xl pb-[calc(1rem+env(safe-area-inset-bottom))]">
          <SheetHeader>
            <SheetTitle>Mais opções</SheetTitle>
            <SheetDescription className="sr-only">Outras páginas do app</SheetDescription>
          </SheetHeader>
          <nav aria-label="Mais páginas" className="grid gap-1 px-4">
            {NAV.filter((n) => !n.mobile).map((item) => (
              <SideNavLink key={item.to} item={item} onClick={() => setMoreOpen(false)} />
            ))}
            <NavLink to="/privacidade" onClick={() => setMoreOpen(false)} className="px-3 pt-2 text-sm text-muted-foreground underline underline-offset-4">
              Política de privacidade
            </NavLink>
          </nav>
        </SheetContent>
      </Sheet>

      <TransactionDialog />
    </div>
  )
}
