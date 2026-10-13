import { lazy, Suspense } from 'react'
import { BrowserRouter, Route, Routes, useLocation } from 'react-router'
import { AppShell } from '@/components/AppShell'
import { AuthScreen, RecoveryCodeScreen } from '@/components/auth'
import { Onboarding } from '@/components/Onboarding'
import { Toaster } from '@/components/ui/sonner'
import { TooltipProvider } from '@/components/ui/tooltip'
import Dashboard from '@/pages/Dashboard'
import NotFound from '@/pages/NotFound'
import { useSession } from '@/store/session'
import { useStore } from '@/store/store'

const Transactions = lazy(() => import('@/pages/Transactions'))
const Planning = lazy(() => import('@/pages/Planning'))
const Recurring = lazy(() => import('@/pages/Recurring'))
const Goals = lazy(() => import('@/pages/Goals'))
const Invest = lazy(() => import('@/pages/Invest'))
const Simulator = lazy(() => import('@/pages/Simulator'))
const History = lazy(() => import('@/pages/History'))
const Settings = lazy(() => import('@/pages/Settings'))
const Privacy = lazy(() => import('@/pages/Privacy'))

/** Endereços do app (pedem login). Qualquer outro mostra a página 404. */
const APP_PATHS = new Set(['/', '/lancamentos', '/planejamento', '/contas-fixas', '/metas', '/investir', '/simulador', '/historico', '/ajustes'])

function Gate() {
  const { pathname } = useLocation()
  const session = useSession((s) => s.session)
  const recoveryCode = useSession((s) => s.recoveryCode)
  const onboarded = useStore((s) => s.settings.onboarded)

  if (!session) return APP_PATHS.has(pathname.replace(/\/+$/, '') || '/') ? <AuthScreen /> : <NotFound standalone />
  if (recoveryCode) return <RecoveryCodeScreen />
  if (!onboarded) return <Onboarding />

  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<Dashboard />} />
        <Route path="lancamentos" element={<Transactions />} />
        <Route path="planejamento" element={<Planning />} />
        <Route path="contas-fixas" element={<Recurring />} />
        <Route path="metas" element={<Goals />} />
        <Route path="investir" element={<Invest />} />
        <Route path="simulador" element={<Simulator />} />
        <Route path="historico" element={<History />} />
        <Route path="ajustes" element={<Settings />} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  )
}

export default function App() {
  return (
    <TooltipProvider>
      <BrowserRouter>
        <Suspense fallback={<div className="min-h-dvh" aria-busy="true" />}>
          <Routes>
            <Route path="/privacidade" element={<Privacy />} />
            <Route path="/*" element={<Gate />} />
          </Routes>
        </Suspense>
      </BrowserRouter>
      <Toaster position="top-center" closeButton />
    </TooltipProvider>
  )
}
