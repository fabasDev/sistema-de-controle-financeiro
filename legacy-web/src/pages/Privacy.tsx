import { useEffect } from 'react'
import { Link, useNavigate } from 'react-router'
import { ArrowLeft, Lock, ShieldCheck, Wallet } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useSession } from '@/store/session'

export const PRIVACY = {
  updated: '12 de outubro de 2026',
  controller: 'Fabricio Castilho Gabriel Buelta',
  email: 'fabriciobuelta@gmail.com',
}

const SECTIONS = [
  ['resumo', 'Resumo'],
  ['responsavel', 'Quem é o responsável'],
  ['dados', 'Quais dados o app trata'],
  ['onde', 'Onde os dados ficam e como são protegidos'],
  ['finalidade', 'Para que os dados são usados'],
  ['compartilhamento', 'Compartilhamento'],
  ['assistente', 'Assistente de investimentos'],
  ['retencao', 'Por quanto tempo os dados ficam guardados'],
  ['direitos', 'Seus direitos (LGPD)'],
  ['seguranca', 'Segurança e limites'],
  ['menores', 'Crianças e adolescentes'],
  ['alteracoes', 'Alterações desta política'],
  ['contato', 'Contato'],
] as const

export default function Privacy() {
  const navigate = useNavigate()
  const loggedIn = useSession((s) => !!s.session)

  useEffect(() => {
    document.title = 'Política de privacidade · Finanças'
    window.scrollTo({ top: 0 })
    return () => {
      document.title = 'Finanças'
    }
  }, [])

  return (
    <div className="min-h-dvh">
      <header className="sticky top-0 z-20 border-b bg-background/85 backdrop-blur-md">
        <div className="mx-auto flex h-16 max-w-3xl items-center justify-between gap-3 px-4 sm:px-6">
          <Link to="/" className="flex items-center gap-2.5 rounded-md">
            <span className="grid size-9 place-items-center rounded-xl bg-primary text-primary-foreground">
              <Wallet className="size-5" aria-hidden />
            </span>
            <span className="text-lg font-semibold tracking-tight">Finanças</span>
          </Link>
          <Button variant="outline" onClick={() => (window.history.length > 1 ? navigate(-1) : navigate('/'))}>
            <ArrowLeft aria-hidden /> {loggedIn ? 'Voltar ao app' : 'Voltar'}
          </Button>
        </div>
      </header>

      <main id="conteudo" className="mx-auto max-w-3xl px-4 py-10 sm:px-6">
        <p className="text-sm font-medium text-primary">Privacidade</p>
        <h1 className="mt-1 text-3xl font-semibold tracking-tight sm:text-4xl">Política de privacidade</h1>
        <p className="mt-2 text-sm text-muted-foreground">Última atualização: {PRIVACY.updated}</p>

        <div className="mt-6 grid gap-3 rounded-xl border bg-card p-5 sm:grid-cols-2">
          <Highlight icon={<Lock className="size-5" aria-hidden />} title="Seus dados não saem do aparelho">
            Não há servidor, conta online, cookies de rastreamento nem analytics.
          </Highlight>
          <Highlight icon={<ShieldCheck className="size-5" aria-hidden />} title="Tudo criptografado com a sua senha">
            Nem o responsável pelo app consegue ler suas informações.
          </Highlight>
        </div>

        <nav aria-label="Nesta página" className="mt-8 rounded-xl border p-5">
          <p className="mb-2 text-sm font-semibold">Nesta página</p>
          <ol className="grid list-decimal gap-1 pl-5 text-sm text-muted-foreground sm:grid-cols-2">
            {SECTIONS.map(([id, title]) => (
              <li key={id}>
                <a href={`#${id}`} className="underline-offset-4 hover:text-foreground hover:underline">
                  {title}
                </a>
              </li>
            ))}
          </ol>
        </nav>

        <article className="mt-10 grid gap-10 text-[15px] leading-relaxed [&_h2]:scroll-mt-24 [&_h2]:text-xl [&_h2]:font-semibold [&_h2]:tracking-tight [&_li]:mt-1.5 [&_p]:mt-3 [&_strong]:font-semibold [&_strong]:text-foreground [&_ul]:mt-3 [&_ul]:list-disc [&_ul]:pl-5 text-muted-foreground">
          <section>
            <h2 id="resumo" className="text-foreground">1. Resumo</h2>
            <ul>
              <li>O <strong>Finanças</strong> é um app de controle financeiro pessoal que funciona inteiramente no seu navegador.</li>
              <li>Tudo o que você registra fica <strong>somente neste aparelho</strong>, criptografado com uma chave protegida pela sua senha.</li>
              <li>Nenhuma informação é enviada para servidores, empresas parceiras, anunciantes ou provedores de inteligência artificial.</li>
              <li>Você pode ver, corrigir, exportar e apagar seus dados a qualquer momento pelo próprio app.</li>
            </ul>
          </section>

          <section>
            <h2 id="responsavel" className="text-foreground">2. Quem é o responsável</h2>
            <p>
              Para os fins da Lei Geral de Proteção de Dados (Lei nº 13.709/2018 — LGPD), o responsável pelo app é{' '}
              <strong>{PRIVACY.controller}</strong>, contato: <a className="text-primary underline underline-offset-4" href={`mailto:${PRIVACY.email}`}>{PRIVACY.email}</a>.
            </p>
            <p>
              Como os dados são processados apenas no seu dispositivo e ficam sob seu controle exclusivo, o responsável <strong>não coleta, não recebe e não tem acesso</strong> a eles.
            </p>
          </section>

          <section>
            <h2 id="dados" className="text-foreground">3. Quais dados o app trata</h2>
            <ul>
              <li><strong>Dados de acesso:</strong> nome de usuário (guardado apenas como um código irreversível — hash) e senha (nunca armazenada; é usada só para abrir a chave de criptografia).</li>
              <li><strong>Dados financeiros que você informa:</strong> receitas, despesas, categorias, contas fixas, parcelas, metas, saldo da reserva e parâmetros do simulador.</li>
              <li><strong>Perfil de investidor:</strong> respostas do questionário (objetivo, prazo, tolerância a perdas, experiência, conhecimento, estabilidade da renda, parte do patrimônio disponível e faixa de idade) e o perfil resultante.</li>
              <li><strong>Preferências:</strong> nome de exibição, regra de distribuição, tempo de bloqueio automático e tema (claro/escuro).</li>
            </ul>
            <p>O app não pede CPF, endereço, dados bancários, senhas de banco, localização, contatos, câmera ou microfone, e não trata dados pessoais sensíveis.</p>
          </section>

          <section>
            <h2 id="onde" className="text-foreground">4. Onde os dados ficam e como são protegidos</h2>
            <ul>
              <li>Os dados ficam no armazenamento local do navegador (<em>localStorage</em>) deste aparelho.</li>
              <li>Tudo, exceto a preferência de tema, é criptografado com <strong>AES-256-GCM</strong>. A chave é protegida pela sua senha (PBKDF2-SHA256 com 600 mil iterações) e por um código de recuperação.</li>
              <li>A chave fica apenas na memória enquanto você usa o app e é descartada ao sair, ao fechar a aba ou após o tempo de inatividade configurado.</li>
              <li>O app não usa cookies, não carrega scripts de terceiros e não faz requisições para a internet (as fontes são locais).</li>
              <li>Backups exportados também são criptografados com uma senha escolhida por você.</li>
            </ul>
          </section>

          <section>
            <h2 id="finalidade" className="text-foreground">5. Para que os dados são usados</h2>
            <p>
              Exclusivamente para as funcionalidades que você usa: calcular orçamento, saldo, reserva de emergência, metas, histórico, simulações e as respostas do assistente de investimentos. A base legal é a execução das
              funcionalidades solicitadas por você (art. 7º, V, da LGPD). Não há uso para publicidade, perfilamento comercial ou venda de dados.
            </p>
          </section>

          <section>
            <h2 id="compartilhamento" className="text-foreground">6. Compartilhamento</h2>
            <p>
              <strong>Nenhum.</strong> O app não compartilha dados com terceiros. Os dados só saem do aparelho se <strong>você</strong> exportar um backup, que é criptografado e fica sob sua guarda.
            </p>
          </section>

          <section>
            <h2 id="assistente" className="text-foreground">7. Assistente de investimentos</h2>
            <ul>
              <li>O assistente funciona <strong>offline</strong>, com regras e textos educativos embutidos no app. Suas perguntas e seus números não são enviados para nenhum serviço de inteligência artificial.</li>
              <li>O histórico da conversa não é salvo: ele some ao sair da página. O perfil de investidor é salvo criptografado, junto com seus outros dados.</li>
              <li>As sugestões têm caráter <strong>educativo</strong> e não constituem recomendação, análise ou consultoria de valores mobiliários nos termos da regulamentação da CVM. Rentabilidade passada não garante rentabilidade futura. Antes de decidir, consulte um profissional habilitado.</li>
            </ul>
          </section>

          <section>
            <h2 id="retencao" className="text-foreground">8. Por quanto tempo os dados ficam guardados</h2>
            <p>
              Até você apagá-los. Você pode excluir a conta em <strong>Ajustes → Excluir conta</strong>, apagar só os lançamentos em <strong>Recomeçar do zero</strong> ou limpar os dados do site nas configurações do navegador (o que apaga todas as contas deste app no aparelho).
            </p>
          </section>

          <section>
            <h2 id="direitos" className="text-foreground">9. Seus direitos (LGPD)</h2>
            <p>O art. 18 da LGPD garante, entre outros, os direitos abaixo. Como os dados estão sob seu controle, você os exerce diretamente no app:</p>
            <ul>
              <li><strong>Confirmação e acesso:</strong> todas as informações ficam visíveis nas telas do app.</li>
              <li><strong>Correção:</strong> edite qualquer lançamento, categoria, meta ou o perfil de investidor.</li>
              <li><strong>Portabilidade:</strong> exporte um backup em Ajustes.</li>
              <li><strong>Eliminação:</strong> exclua a conta ou os dados a qualquer momento.</li>
              <li><strong>Informação e revogação:</strong> esta política explica todo o tratamento; para deixar de usar, basta excluir a conta.</li>
            </ul>
            <p>
              Dúvidas podem ser enviadas para o contato abaixo. Você também pode reclamar à Autoridade Nacional de Proteção de Dados (ANPD).
            </p>
          </section>

          <section>
            <h2 id="seguranca" className="text-foreground">10. Segurança e limites</h2>
            <p>A criptografia protege seus dados contra quem tiver acesso aos arquivos do navegador enquanto o app está bloqueado. Ainda assim:</p>
            <ul>
              <li>Ela não protege contra vírus, programas espiões ou extensões maliciosas do navegador enquanto o app está desbloqueado. Mantenha o sistema e o navegador atualizados.</li>
              <li>Se você esquecer a senha <strong>e</strong> perder o código de recuperação, ninguém — nem o responsável pelo app — consegue recuperar os dados.</li>
              <li>Use uma senha forte e exclusiva e faça backups periódicos.</li>
            </ul>
          </section>

          <section>
            <h2 id="menores" className="text-foreground">11. Crianças e adolescentes</h2>
            <p>O app é destinado a maiores de 18 anos. Menores devem usá-lo com acompanhamento dos pais ou responsáveis.</p>
          </section>

          <section>
            <h2 id="alteracoes" className="text-foreground">12. Alterações desta política</h2>
            <p>Esta política pode ser atualizada quando o app mudar. A data no topo indica a versão vigente. Mudanças relevantes, como qualquer envio de dados para fora do aparelho, serão comunicadas no próprio app antes de valer.</p>
          </section>

          <section>
            <h2 id="contato" className="text-foreground">13. Contato</h2>
            <p>
              {PRIVACY.controller} — <a className="text-primary underline underline-offset-4" href={`mailto:${PRIVACY.email}`}>{PRIVACY.email}</a>
            </p>
          </section>
        </article>
      </main>
    </div>
  )
}

function Highlight({ icon, title, children }: { icon: React.ReactNode; title: string; children: React.ReactNode }) {
  return (
    <div className="flex gap-3">
      <span className="mt-0.5 grid size-9 shrink-0 place-items-center rounded-lg bg-accent text-primary">{icon}</span>
      <div>
        <p className="font-medium">{title}</p>
        <p className="mt-0.5 text-sm text-muted-foreground">{children}</p>
      </div>
    </div>
  )
}
