import { Component, type ReactNode } from 'react'
import { RotateCcw, TriangleAlert } from 'lucide-react'
import { Button } from '@/components/ui/button'

interface State {
  error?: Error
}

/** Evita tela em branco se uma página falhar (ex.: app atualizado com a aba aberta). */
export class ErrorBoundary extends Component<{ children: ReactNode; resetKey?: string }, State> {
  state: State = {}

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  componentDidUpdate(prev: { resetKey?: string }) {
    if (this.state.error && prev.resetKey !== this.props.resetKey) this.setState({ error: undefined })
  }

  render() {
    if (!this.state.error) return this.props.children
    const outdated = /dynamically imported module|Importing a module script failed|error loading dynamically/i.test(this.state.error.message)
    return (
      <div role="alert" className="mx-auto flex max-w-md flex-col items-center gap-3 py-16 text-center">
        <span className="grid size-12 place-items-center rounded-xl bg-danger/10 text-danger">
          <TriangleAlert className="size-6" aria-hidden />
        </span>
        <h1 className="text-lg font-semibold">{outdated ? 'O app foi atualizado' : 'Algo deu errado nesta página'}</h1>
        <p className="text-sm text-muted-foreground">
          {outdated
            ? 'Recarregue para usar a versão nova. Seus dados estão salvos e criptografados; será preciso entrar de novo.'
            : 'Seus dados continuam salvos. Tente recarregar a página.'}
        </p>
        <Button onClick={() => window.location.reload()}>
          <RotateCcw aria-hidden /> Recarregar
        </Button>
      </div>
    )
  }
}
