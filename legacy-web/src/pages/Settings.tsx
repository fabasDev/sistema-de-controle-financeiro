import { useRef, useState } from 'react'
import { Link } from 'react-router'
import { Download, KeyRound, Loader2, Lock, LogOut, RotateCcw, ShieldCheck, Sparkles, Trash2, Upload, UserX } from 'lucide-react'
import { toast } from 'sonner'
import { PageHeader, useData } from '@/components/common'
import { PasswordField } from '@/components/auth'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Separator } from '@/components/ui/separator'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import { checkPassword } from '@/lib/crypto'
import { todayISO } from '@/lib/month'
import { useTheme, type ThemePref } from '@/lib/theme'
import * as vault from '@/lib/vault'
import { downloadBlob } from '@/lib/download'
import { exportEncryptedBackup, readEncryptedBackup } from '@/store/backup'
import { createSampleData } from '@/store/sample'
import { useSession } from '@/store/session'
import { selectData, useStore } from '@/store/store'
import type { DataState } from '@/store/types'

type Confirm = 'sample' | 'reset' | null
type DialogKind = 'password' | 'recovery' | 'export' | 'delete' | null

export default function Settings() {
  const { settings } = useData()
  const updateSettings = useStore((s) => s.updateSettings)
  const replaceData = useStore((s) => s.replaceData)
  const resetAll = useStore((s) => s.resetAll)
  const logout = useSession((s) => s.logout)
  const username = useSession((s) => s.session?.username)
  const { pref, setPref } = useTheme()
  const [confirm, setConfirm] = useState<Confirm>(null)
  const [dialog, setDialog] = useState<DialogKind>(null)
  const [importText, setImportText] = useState<string | null>(null)
  const fileRef = useRef<HTMLInputElement>(null)

  async function pickFile(file: File | undefined) {
    if (!file) return
    if (file.size > 20 * 1024 * 1024) return toast.error('Arquivo grande demais para ser um backup.')
    setImportText(await file.text())
    if (fileRef.current) fileRef.current.value = ''
  }

  return (
    <>
      <PageHeader title="Ajustes" description="Perfil, aparência, segurança e backup" />

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Perfil e aparência</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-5">
            <div className="grid gap-1.5">
              <Label htmlFor="set-name">Nome de exibição</Label>
              <Input id="set-name" maxLength={40} value={settings.userName} onChange={(e) => updateSettings({ userName: e.target.value })} className="h-10" />
              <p className="text-xs text-muted-foreground">Usuário de acesso: {username}</p>
            </div>
            <div className="grid gap-1.5">
              <Label id="set-theme">Tema</Label>
              <ToggleGroup type="single" variant="outline" value={pref} onValueChange={(v) => v && setPref(v as ThemePref)} aria-labelledby="set-theme" className="w-full">
                <ToggleGroupItem value="system" className="h-10 flex-1">Automático</ToggleGroupItem>
                <ToggleGroupItem value="light" className="h-10 flex-1">Claro</ToggleGroupItem>
                <ToggleGroupItem value="dark" className="h-10 flex-1">Escuro</ToggleGroupItem>
              </ToggleGroup>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <ShieldCheck className="size-5 text-primary" aria-hidden /> Segurança
            </CardTitle>
            <CardDescription>Como seus dados são protegidos</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-4">
            <ul className="grid gap-1.5 text-sm text-muted-foreground">
              <li>• Criptografia AES-256-GCM com chave derivada da sua senha (PBKDF2, 600 mil iterações).</li>
              <li>• A senha nunca é salva; o nome de usuário fica guardado só como hash.</li>
              <li>• Nenhum dado sai deste aparelho. Sem servidores, sem rastreadores.</li>
              <li>• A chave fica só na memória e some ao sair ou após inatividade.</li>
            </ul>
            <Link to="/privacidade" className="w-fit text-sm text-primary underline underline-offset-4">
              Ler a política de privacidade
            </Link>
            <div className="grid gap-1.5">
              <Label htmlFor="set-lock">Bloquear automaticamente após</Label>
              <Select value={String(settings.autoLockMinutes)} onValueChange={(v) => updateSettings({ autoLockMinutes: Number(v) })}>
                <SelectTrigger id="set-lock" className="h-10 w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="5">5 minutos sem uso</SelectItem>
                  <SelectItem value="15">15 minutos sem uso</SelectItem>
                  <SelectItem value="30">30 minutos sem uso</SelectItem>
                  <SelectItem value="60">1 hora sem uso</SelectItem>
                  <SelectItem value="0">Nunca (não recomendado)</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="flex flex-wrap gap-2">
              <Button variant="outline" onClick={() => setDialog('password')}>
                <Lock aria-hidden /> Alterar senha
              </Button>
              <Button variant="outline" onClick={() => setDialog('recovery')}>
                <KeyRound aria-hidden /> Novo código de recuperação
              </Button>
              <Button variant="outline" onClick={() => void logout()}>
                <LogOut aria-hidden /> Sair
              </Button>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Backup</CardTitle>
            <CardDescription>
              Seus dados ficam só neste navegador. Faça backups para não perdê-los se limpar o navegador ou trocar de computador.
            </CardDescription>
          </CardHeader>
          <CardContent className="grid gap-3">
            <p className="text-sm text-muted-foreground">
              Último backup: {settings.lastExport ? new Date(settings.lastExport).toLocaleString('pt-BR') : 'nunca'}
            </p>
            <div className="flex flex-wrap gap-2">
              <Button onClick={() => setDialog('export')}>
                <Download aria-hidden /> Exportar backup criptografado
              </Button>
              <Button variant="outline" onClick={() => fileRef.current?.click()}>
                <Upload aria-hidden /> Importar backup
              </Button>
              <input ref={fileRef} type="file" accept=".json,application/json" className="hidden" onChange={(e) => void pickFile(e.target.files?.[0])} />
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Dados</CardTitle>
            <CardDescription>Ações que substituem ou apagam informações</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-3">
            <div className="flex flex-wrap gap-2">
              <Button variant="outline" onClick={() => setConfirm('sample')}>
                <Sparkles aria-hidden /> Carregar dados de exemplo
              </Button>
              <Button variant="outline" onClick={() => setConfirm('reset')}>
                <RotateCcw aria-hidden /> Recomeçar do zero
              </Button>
            </div>
            <Separator />
            <div className="grid gap-2">
              <p className="text-sm font-medium text-danger">Zona de perigo</p>
              <Button variant="destructive" className="w-fit" onClick={() => setDialog('delete')}>
                <UserX aria-hidden /> Excluir conta e todos os dados
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>

      <AlertDialog open={!!confirm} onOpenChange={(o) => !o && setConfirm(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{confirm === 'sample' ? 'Substituir pelos dados de exemplo?' : 'Apagar todos os lançamentos e configurações?'}</AlertDialogTitle>
            <AlertDialogDescription>
              Seus dados atuais serão substituídos. Se quiser guardá-los, exporte um backup antes. Sua conta e senha continuam as mesmas.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancelar</AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              onClick={() => {
                if (confirm === 'sample') {
                  const sample = createSampleData()
                  replaceData({ ...sample, settings: { ...sample.settings, userName: settings.userName || sample.settings.userName, autoLockMinutes: settings.autoLockMinutes } })
                  toast.success('Dados de exemplo carregados')
                } else {
                  resetAll()
                  toast.success('Tudo zerado. Vamos configurar de novo.')
                }
              }}
            >
              {confirm === 'sample' ? 'Substituir' : 'Apagar tudo'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <Dialog open={!!dialog} onOpenChange={(o) => !o && setDialog(null)}>
        <DialogContent className="sm:max-w-md">
          {dialog === 'password' && <ChangePassword onDone={() => setDialog(null)} />}
          {dialog === 'recovery' && <NewRecovery onDone={() => setDialog(null)} />}
          {dialog === 'export' && <ExportBackup onDone={() => setDialog(null)} />}
          {dialog === 'delete' && <DeleteAccount onDone={() => setDialog(null)} />}
        </DialogContent>
      </Dialog>

      <Dialog open={importText !== null} onOpenChange={(o) => !o && setImportText(null)}>
        <DialogContent className="sm:max-w-md">
          {importText !== null && <ImportBackup text={importText} onDone={() => setImportText(null)} />}
        </DialogContent>
      </Dialog>
    </>
  )
}

function useAction() {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  async function run(fn: () => Promise<void>) {
    setBusy(true)
    setError('')
    try {
      await fn()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Algo deu errado.')
    } finally {
      setBusy(false)
    }
  }
  return { busy, error, run }
}

function Footer({ busy, onCancel, label, destructive }: { busy: boolean; onCancel: () => void; label: string; destructive?: boolean }) {
  return (
    <DialogFooter>
      <Button type="button" variant="outline" size="lg" onClick={onCancel}>
        Cancelar
      </Button>
      <Button type="submit" size="lg" variant={destructive ? 'destructive' : 'default'} disabled={busy} aria-busy={busy}>
        {busy && <Loader2 className="animate-spin" aria-hidden />}
        {label}
      </Button>
    </DialogFooter>
  )
}

function ErrorText({ message }: { message: string }) {
  return message ? (
    <p role="alert" className="text-sm text-danger">
      {message}
    </p>
  ) : null
}

function ChangePassword({ onDone }: { onDone: () => void }) {
  const session = useSession((s) => s.session)!
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const { busy, error, run } = useAction()
  const strength = checkPassword(next, session.username)

  return (
    <form
      className="grid gap-4"
      noValidate
      onSubmit={(e) => {
        e.preventDefault()
        setSubmitted(true)
        if (!current || !strength.ok || next !== confirm) return
        void run(async () => {
          await vault.changePassword(session.accountId, current, next)
          toast.success('Senha alterada')
          onDone()
        })
      }}
    >
      <DialogHeader>
        <DialogTitle>Alterar senha</DialogTitle>
        <DialogDescription>Seus dados continuam os mesmos; só a proteção da chave muda.</DialogDescription>
      </DialogHeader>
      <PasswordField id="cp-current" label="Senha atual" value={current} onChange={setCurrent} autoComplete="current-password" autoFocus />
      <PasswordField
        id="cp-new"
        label="Nova senha"
        value={next}
        onChange={setNext}
        autoComplete="new-password"
        strengthFor={session.username}
        error={submitted && !strength.ok ? (strength.hint ?? 'Escolha uma senha mais forte.') : undefined}
      />
      <PasswordField
        id="cp-confirm"
        label="Confirme a nova senha"
        value={confirm}
        onChange={setConfirm}
        autoComplete="new-password"
        error={submitted && confirm !== next ? 'As senhas não são iguais.' : undefined}
      />
      <ErrorText message={error} />
      <Footer busy={busy} onCancel={onDone} label="Alterar senha" />
    </form>
  )
}

function NewRecovery({ onDone }: { onDone: () => void }) {
  const session = useSession((s) => s.session)!
  const showRecoveryCode = useSession((s) => s.showRecoveryCode)
  const [password, setPassword] = useState('')
  const { busy, error, run } = useAction()
  return (
    <form
      className="grid gap-4"
      onSubmit={(e) => {
        e.preventDefault()
        if (!password) return
        void run(async () => {
          const code = await vault.regenerateRecovery(session.accountId, password)
          onDone()
          showRecoveryCode(code)
        })
      }}
    >
      <DialogHeader>
        <DialogTitle>Gerar novo código de recuperação</DialogTitle>
        <DialogDescription>O código antigo deixará de funcionar. Use se perdeu o anterior ou acha que alguém o viu.</DialogDescription>
      </DialogHeader>
      <PasswordField id="nr-pass" label="Confirme sua senha" value={password} onChange={setPassword} autoComplete="current-password" autoFocus />
      <ErrorText message={error} />
      <Footer busy={busy} onCancel={onDone} label="Gerar código" />
    </form>
  )
}

function ExportBackup({ onDone }: { onDone: () => void }) {
  const updateSettings = useStore((s) => s.updateSettings)
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const { busy, error, run } = useAction()
  const strength = checkPassword(password)

  return (
    <form
      className="grid gap-4"
      noValidate
      onSubmit={(e) => {
        e.preventDefault()
        setSubmitted(true)
        if (!strength.ok || password !== confirm) return
        void run(async () => {
          const blob = await exportEncryptedBackup(selectData(useStore.getState()), password)
          downloadBlob(blob, `financas-backup-${todayISO()}.json`)
          updateSettings({ lastExport: new Date().toISOString() })
          toast.success('Backup exportado', { description: 'Guarde o arquivo e lembre-se da senha do backup.' })
          onDone()
        })
      }}
    >
      <DialogHeader>
        <DialogTitle>Exportar backup criptografado</DialogTitle>
        <DialogDescription>O arquivo só pode ser aberto com a senha abaixo. Pode ser a mesma senha da conta.</DialogDescription>
      </DialogHeader>
      <PasswordField
        id="ex-pass"
        label="Senha do backup"
        value={password}
        onChange={setPassword}
        autoComplete="new-password"
        strengthFor=""
        autoFocus
        error={submitted && !strength.ok ? (strength.hint ?? 'Escolha uma senha mais forte.') : undefined}
      />
      <PasswordField
        id="ex-confirm"
        label="Confirme a senha"
        value={confirm}
        onChange={setConfirm}
        autoComplete="new-password"
        error={submitted && confirm !== password ? 'As senhas não são iguais.' : undefined}
      />
      <ErrorText message={error} />
      <Footer busy={busy} onCancel={onDone} label="Exportar" />
    </form>
  )
}

function ImportBackup({ text, onDone }: { text: string; onDone: () => void }) {
  const replaceData = useStore((s) => s.replaceData)
  const [password, setPassword] = useState('')
  const [parsed, setParsed] = useState<DataState | null>(null)
  const { busy, error, run } = useAction()

  if (parsed) {
    return (
      <div className="grid gap-4">
        <DialogHeader>
          <DialogTitle>Substituir dados atuais?</DialogTitle>
          <DialogDescription>
            O backup tem {parsed.transactions.length} lançamentos, {parsed.recurring.length} contas fixas e {parsed.goals.length} metas. Os dados atuais desta conta serão
            substituídos.
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" size="lg" onClick={onDone}>
            Cancelar
          </Button>
          <Button
            variant="destructive"
            size="lg"
            onClick={() => {
              replaceData(parsed)
              toast.success('Backup restaurado')
              onDone()
            }}
          >
            Substituir
          </Button>
        </DialogFooter>
      </div>
    )
  }

  return (
    <form
      className="grid gap-4"
      onSubmit={(e) => {
        e.preventDefault()
        if (!password) return
        void run(async () => setParsed(await readEncryptedBackup(text, password)))
      }}
    >
      <DialogHeader>
        <DialogTitle>Importar backup</DialogTitle>
        <DialogDescription>Digite a senha usada ao exportar este backup.</DialogDescription>
      </DialogHeader>
      <PasswordField id="im-pass" label="Senha do backup" value={password} onChange={setPassword} autoComplete="current-password" autoFocus />
      <ErrorText message={error} />
      <Footer busy={busy} onCancel={onDone} label="Abrir backup" />
    </form>
  )
}

function DeleteAccount({ onDone }: { onDone: () => void }) {
  const session = useSession((s) => s.session)!
  const logout = useSession((s) => s.logout)
  const [password, setPassword] = useState('')
  const [typed, setTyped] = useState('')
  const { busy, error, run } = useAction()
  const word = 'EXCLUIR'

  return (
    <form
      className="grid gap-4"
      onSubmit={(e) => {
        e.preventDefault()
        if (!password || typed !== word) return
        void run(async () => {
          await vault.deleteAccount(session.accountId, password)
          onDone()
          await logout('removed')
          toast.success('Conta e dados excluídos deste aparelho')
        })
      }}
    >
      <DialogHeader>
        <DialogTitle className="flex items-center gap-2 text-danger">
          <Trash2 className="size-5" aria-hidden /> Excluir conta
        </DialogTitle>
        <DialogDescription>
          Apaga a conta e todos os dados criptografados deste navegador. <strong className="text-foreground">Não dá para desfazer.</strong> Exporte um backup antes se
          quiser guardar algo.
        </DialogDescription>
      </DialogHeader>
      <PasswordField id="del-pass" label="Sua senha" value={password} onChange={setPassword} autoComplete="current-password" autoFocus />
      <div className="grid gap-1.5">
        <Label htmlFor="del-word">
          Digite <span className="font-mono font-semibold">{word}</span> para confirmar
        </Label>
        <Input id="del-word" autoComplete="off" value={typed} onChange={(e) => setTyped(e.target.value)} className="h-10" />
      </div>
      <ErrorText message={error} />
      <Footer busy={busy || typed !== word} onCancel={onDone} label="Excluir definitivamente" destructive />
    </form>
  )
}
