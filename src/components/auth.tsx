import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { Copy, Download, Eye, EyeOff, KeyRound, Loader2, Lock, ShieldCheck, TriangleAlert, Wallet } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { checkPassword } from '@/lib/crypto'
import { cn } from '@/lib/utils'
import { AuthError, hasAccounts } from '@/lib/vault'
import { downloadBlob } from '@/lib/download'
import { useSession } from '@/store/session'

// ---------- Campo de senha ----------

export function PasswordField({
  id,
  label,
  value,
  onChange,
  autoComplete,
  strengthFor,
  error,
  autoFocus,
}: {
  id: string
  label: string
  value: string
  onChange: (v: string) => void
  autoComplete: 'current-password' | 'new-password'
  /** Quando definido, mostra o medidor de força (valor = nome de usuário). */
  strengthFor?: string
  error?: string
  autoFocus?: boolean
}) {
  const [visible, setVisible] = useState(false)
  const check = strengthFor !== undefined && value ? checkPassword(value, strengthFor) : null
  const describedBy = [error && `${id}-error`, check && `${id}-strength`].filter(Boolean).join(' ') || undefined
  return (
    <div className="grid gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      <div className="relative">
        <Input
          id={id}
          type={visible ? 'text' : 'password'}
          autoComplete={autoComplete}
          autoCapitalize="none"
          spellCheck={false}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          className="h-11 pr-11"
          aria-invalid={!!error}
          aria-describedby={describedBy}
          autoFocus={autoFocus}
          maxLength={256}
        />
        <Button
          type="button"
          variant="ghost"
          size="icon"
          className="absolute top-1/2 right-1 size-9 -translate-y-1/2"
          onClick={() => setVisible((v) => !v)}
          aria-label={visible ? 'Ocultar senha' : 'Mostrar senha'}
          aria-pressed={visible}
        >
          {visible ? <EyeOff aria-hidden /> : <Eye aria-hidden />}
        </Button>
      </div>
      {check && (
        <div id={`${id}-strength`} className="grid gap-1" aria-live="polite">
          <div className="flex gap-1" aria-hidden>
            {[0, 1, 2, 3].map((i) => (
              <span
                key={i}
                className={cn(
                  'h-1 flex-1 rounded-full bg-muted',
                  i < check.score && (check.score <= 1 ? 'bg-danger' : check.score === 2 ? 'bg-warning' : 'bg-success'),
                )}
              />
            ))}
          </div>
          <p className="text-xs text-muted-foreground">
            Força: <span className="font-medium text-foreground">{check.label}</span>
            {check.hint ? ` — ${check.hint}` : ''}
          </p>
        </div>
      )}
      {error && (
        <p id={`${id}-error`} role="alert" className="text-xs text-danger">
          {error}
        </p>
      )}
    </div>
  )
}

// ---------- Tela de entrada ----------

type Mode = 'login' | 'signup' | 'recover'

function useCountdown(until?: number) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (!until) return
    const t = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(t)
  }, [until])
  return until ? Math.max(0, Math.ceil((until - now) / 1000)) : 0
}

export function AuthScreen() {
  const lockReason = useSession((s) => s.lockReason)
  const [mode, setMode] = useState<Mode>(() => (hasAccounts() ? 'login' : 'signup'))

  return (
    <div className="grid min-h-dvh lg:grid-cols-2">
      <aside className="relative hidden flex-col justify-between overflow-hidden bg-primary p-10 text-primary-foreground lg:flex">
        <div className="flex items-center gap-2.5">
          <span className="grid size-10 place-items-center rounded-xl bg-primary-foreground/15">
            <Wallet className="size-5" aria-hidden />
          </span>
          <span className="text-xl font-semibold">Finanças</span>
        </div>
        <div className="max-w-md">
          <h2 className="text-3xl leading-tight font-semibold">Saiba para onde vai cada real — e quanto pode guardar.</h2>
          <ul className="mt-8 grid gap-4 text-primary-foreground/90">
            <Feature icon={<ShieldCheck className="size-5" aria-hidden />} text="Dados criptografados com AES-256 usando a sua senha" />
            <Feature icon={<Lock className="size-5" aria-hidden />} text="Tudo fica só neste aparelho: nada é enviado para servidores" />
            <Feature icon={<KeyRound className="size-5" aria-hidden />} text="Código de recuperação caso esqueça a senha" />
          </ul>
        </div>
        <p className="text-sm text-primary-foreground/70">
          Orçamento, metas, reserva e investimentos em um só lugar ·{' '}
          <Link to="/privacidade" className="underline underline-offset-4">
            Política de privacidade
          </Link>
        </p>
      </aside>

      <main className="flex items-center justify-center px-4 py-10">
        <div className="w-full max-w-sm">
          <div className="mb-8 flex items-center gap-2.5 lg:hidden">
            <span className="grid size-10 place-items-center rounded-xl bg-primary text-primary-foreground">
              <Wallet className="size-5" aria-hidden />
            </span>
            <span className="text-xl font-semibold">Finanças</span>
          </div>

          {lockReason === 'inactivity' && (
            <p role="status" className="mb-4 flex items-center gap-2 rounded-lg bg-accent px-3 py-2 text-sm">
              <Lock className="size-4 shrink-0" aria-hidden /> Sessão bloqueada por inatividade. Entre de novo para continuar.
            </p>
          )}

          {mode === 'recover' ? (
            <RecoverForm onBack={() => setMode('login')} />
          ) : (
            <>
              <h1 className="text-2xl font-semibold tracking-tight">{mode === 'login' ? 'Entrar' : 'Criar conta'}</h1>
              <p className="mt-1 mb-6 text-sm text-muted-foreground">
                {mode === 'login' ? 'Use o usuário e a senha que você criou neste aparelho.' : 'Sua conta e seus dados ficam apenas neste navegador.'}
              </p>
              <Tabs value={mode} onValueChange={(v) => setMode(v as Mode)} className="mb-6">
                <TabsList className="w-full">
                  <TabsTrigger value="login" className="flex-1">
                    Entrar
                  </TabsTrigger>
                  <TabsTrigger value="signup" className="flex-1">
                    Criar conta
                  </TabsTrigger>
                </TabsList>
              </Tabs>
              {mode === 'login' ? <LoginForm onForgot={() => setMode('recover')} /> : <SignupForm />}
            </>
          )}
          <p className="mt-8 text-center text-xs text-muted-foreground">
            Ao usar o app você concorda com a{' '}
            <Link to="/privacidade" className="underline underline-offset-4 hover:text-foreground">
              Política de privacidade
            </Link>
            . Seus dados não saem deste aparelho.
          </p>
        </div>
      </main>
    </div>
  )
}

function Feature({ icon, text }: { icon: React.ReactNode; text: string }) {
  return (
    <li className="flex items-start gap-3">
      <span className="mt-0.5 shrink-0">{icon}</span>
      {text}
    </li>
  )
}

function SubmitButton({ busy, children, disabled }: { busy: boolean; children: React.ReactNode; disabled?: boolean }) {
  return (
    <Button type="submit" size="lg" className="h-11 w-full" disabled={busy || disabled} aria-busy={busy}>
      {busy && <Loader2 className="animate-spin" aria-hidden />}
      {busy ? 'Protegendo seus dados…' : children}
    </Button>
  )
}

function FormError({ message, retryAt }: { message?: string; retryAt?: number }) {
  const seconds = useCountdown(retryAt)
  if (!message) return null
  return (
    <p role="alert" className="flex items-start gap-2 rounded-lg bg-danger/10 px-3 py-2 text-sm text-danger">
      <TriangleAlert className="mt-0.5 size-4 shrink-0" aria-hidden />
      <span>
        {message}
        {seconds > 0 && ` Tente novamente em ${seconds}s.`}
      </span>
    </p>
  )
}

function useSubmit() {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<{ message: string; retryAt?: number }>()
  async function run(fn: () => Promise<void>) {
    setBusy(true)
    setError(undefined)
    try {
      await fn()
    } catch (e) {
      setError({ message: e instanceof Error ? e.message : 'Algo deu errado.', retryAt: e instanceof AuthError ? e.retryAt : undefined })
    } finally {
      setBusy(false)
    }
  }
  return { busy, error, run }
}

function UsernameField({ value, onChange, autoFocus }: { value: string; onChange: (v: string) => void; autoFocus?: boolean }) {
  return (
    <div className="grid gap-1.5">
      <Label htmlFor="auth-user">Usuário</Label>
      <Input
        id="auth-user"
        name="username"
        autoComplete="username"
        autoCapitalize="none"
        spellCheck={false}
        maxLength={60}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="h-11"
        autoFocus={autoFocus}
        required
      />
    </div>
  )
}

function LoginForm({ onForgot }: { onForgot: () => void }) {
  const login = useSession((s) => s.login)
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const { busy, error, run } = useSubmit()
  return (
    <form
      className="grid gap-4"
      onSubmit={(e) => {
        e.preventDefault()
        if (username.trim() && password) void run(() => login(username, password))
      }}
    >
      <UsernameField value={username} onChange={setUsername} autoFocus />
      <PasswordField id="auth-pass" label="Senha" value={password} onChange={setPassword} autoComplete="current-password" />
      <FormError message={error?.message} retryAt={error?.retryAt} />
      <SubmitButton busy={busy} disabled={!username.trim() || !password}>
        Entrar
      </SubmitButton>
      <Button type="button" variant="link" onClick={onForgot} className="justify-self-center">
        Esqueci minha senha
      </Button>
    </form>
  )
}

function SignupForm() {
  const signup = useSession((s) => s.signup)
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const { busy, error, run } = useSubmit()
  const strength = checkPassword(password, username)
  const userError = submitted && username.trim().length < 3 ? 'Use pelo menos 3 caracteres.' : undefined
  const passError = submitted && !strength.ok ? (strength.hint ?? 'Escolha uma senha mais forte.') : undefined
  const confirmError = submitted && confirm !== password ? 'As senhas não são iguais.' : undefined

  return (
    <form
      className="grid gap-4"
      noValidate
      onSubmit={(e) => {
        e.preventDefault()
        setSubmitted(true)
        if (username.trim().length >= 3 && strength.ok && confirm === password) void run(() => signup(username, password))
      }}
    >
      <div className="grid gap-1.5">
        <UsernameField value={username} onChange={setUsername} autoFocus />
        {userError && (
          <p role="alert" className="text-xs text-danger">
            {userError}
          </p>
        )}
      </div>
      <PasswordField id="auth-new" label="Senha" value={password} onChange={setPassword} autoComplete="new-password" strengthFor={username} error={passError} />
      <PasswordField id="auth-confirm" label="Confirme a senha" value={confirm} onChange={setConfirm} autoComplete="new-password" error={confirmError} />
      <p className="text-xs text-muted-foreground">
        A senha protege a chave que criptografa seus dados. Ela nunca é salva — por isso, guarde o código de recuperação que aparecerá a seguir.
      </p>
      <FormError message={error?.message} />
      <SubmitButton busy={busy}>Criar conta</SubmitButton>
    </form>
  )
}

function RecoverForm({ onBack }: { onBack: () => void }) {
  const recover = useSession((s) => s.recover)
  const [username, setUsername] = useState('')
  const [code, setCode] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const { busy, error, run } = useSubmit()
  const strength = checkPassword(password, username)
  const passError = submitted && !strength.ok ? (strength.hint ?? 'Escolha uma senha mais forte.') : undefined
  const confirmError = submitted && confirm !== password ? 'As senhas não são iguais.' : undefined

  return (
    <>
      <h1 className="text-2xl font-semibold tracking-tight">Recuperar acesso</h1>
      <p className="mt-1 mb-6 text-sm text-muted-foreground">Use o código de recuperação que você guardou ao criar a conta para definir uma nova senha.</p>
      <form
        className="grid gap-4"
        noValidate
        onSubmit={(e) => {
          e.preventDefault()
          setSubmitted(true)
          if (username.trim() && code.trim() && strength.ok && confirm === password) void run(() => recover(username, code, password))
        }}
      >
        <UsernameField value={username} onChange={setUsername} autoFocus />
        <div className="grid gap-1.5">
          <Label htmlFor="auth-code">Código de recuperação</Label>
          <Input
            id="auth-code"
            autoComplete="off"
            autoCapitalize="characters"
            spellCheck={false}
            placeholder="XXXX-XXXX-XXXX-…"
            value={code}
            maxLength={64}
            onChange={(e) => setCode(e.target.value)}
            className="h-11 font-mono uppercase"
          />
        </div>
        <PasswordField id="auth-reset" label="Nova senha" value={password} onChange={setPassword} autoComplete="new-password" strengthFor={username} error={passError} />
        <PasswordField id="auth-reset-confirm" label="Confirme a nova senha" value={confirm} onChange={setConfirm} autoComplete="new-password" error={confirmError} />
        <FormError message={error?.message} retryAt={error?.retryAt} />
        <SubmitButton busy={busy}>Redefinir senha</SubmitButton>
        <Button type="button" variant="link" onClick={onBack} className="justify-self-center">
          Voltar para o login
        </Button>
      </form>
    </>
  )
}

// ---------- Código de recuperação ----------

export function RecoveryCodeScreen() {
  const code = useSession((s) => s.recoveryCode)!
  const username = useSession((s) => s.session?.username)
  const dismiss = useSession((s) => s.dismissRecoveryCode)
  const [saved, setSaved] = useState(false)

  async function copy() {
    try {
      await navigator.clipboard.writeText(code)
      toast.success('Código copiado', { description: 'Cole em um gerenciador de senhas ou anote em papel.' })
    } catch {
      toast.error('Não foi possível copiar. Selecione o código e copie manualmente.')
    }
  }

  function download() {
    const text = [
      'Finanças — código de recuperação',
      '',
      `Usuário: ${username}`,
      `Código: ${code}`,
      '',
      'Guarde este arquivo em local seguro (de preferência offline) e apague-o da pasta de downloads.',
      'Com este código é possível redefinir a senha da sua conta neste navegador.',
    ].join('\n')
    downloadBlob(new Blob([text], { type: 'text/plain;charset=utf-8' }), 'financas-codigo-de-recuperacao.txt')
  }

  return (
    <main className="flex min-h-dvh items-center justify-center px-4 py-10">
      <Card className="w-full max-w-lg">
        <CardHeader>
          <span className="mb-2 grid size-11 place-items-center rounded-xl bg-accent text-primary">
            <KeyRound className="size-5" aria-hidden />
          </span>
          <CardTitle className="text-xl">Guarde seu código de recuperação</CardTitle>
          <CardDescription>
            Se esquecer a senha, este código é a <strong className="text-foreground">única forma</strong> de recuperar seus dados. Ele não será mostrado de novo.
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4">
          <p
            className="rounded-xl border bg-muted/60 px-4 py-4 text-center font-mono text-lg font-semibold tracking-wider break-all select-all sm:text-xl"
            aria-label={`Código de recuperação: ${code.split('').join(' ')}`}
          >
            {code}
          </p>
          <div className="grid grid-cols-2 gap-2">
            <Button variant="outline" size="lg" onClick={copy}>
              <Copy aria-hidden /> Copiar
            </Button>
            <Button variant="outline" size="lg" onClick={download}>
              <Download aria-hidden /> Baixar .txt
            </Button>
          </div>
          <ul className="grid gap-1 text-sm text-muted-foreground">
            <li>• Guarde em um gerenciador de senhas ou anote em papel.</li>
            <li>• Não envie por e-mail nem mensagens.</li>
            <li>• Quem tiver o código e o seu usuário pode redefinir a senha.</li>
          </ul>
          <label className="flex cursor-pointer items-start gap-3 rounded-lg border p-3 text-sm">
            <Checkbox checked={saved} onCheckedChange={(v) => setSaved(v === true)} className="mt-0.5" />
            Guardei meu código de recuperação em um local seguro.
          </label>
          <Button size="lg" className="h-11" disabled={!saved} onClick={dismiss}>
            Continuar
          </Button>
        </CardContent>
      </Card>
    </main>
  )
}
