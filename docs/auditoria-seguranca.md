# Auditoria de segurança, UX e visual do app React (Fase 1)

Data: 13/10/2026. Base: commit `4b3ed7a` (estado inicial).
Referências consultadas em 13/10/2026:
- **OWASP Top 10:2025** (repositório `OWASP/Top10`, pasta `2025/docs/en`). As categorias da tabela do servidor foram conferidas pelo CWE de cada ameaça.
- **OWASP ASVS 5.0.0** (maio de 2025, repositório `OWASP/ASVS`, pasta `5.0/en`). Usei os requisitos de nível 1 do capítulo V6 (Authentication).
- **Spring Security** (`BCrypt.java` no ramo `main`), para o limite de 72 bytes.

Os cheat sheets (Authentication, Session Management, Password Storage, XSS, CSRF) ainda não foram lidos. Leio cada um na Fase 2, antes de implementar a parte correspondente.

## Como foi feita

- Leitura completa dos arquivos críticos: `src/lib/crypto.ts`, `src/lib/vault.ts`, `src/store/session.ts`, `src/store/backup.ts`, `src/components/auth.tsx`, `vite.config.ts`, `src/pages/Settings.tsx`, `src/components/TransactionDialog.tsx`, `src/components/common.tsx`, `src/lib/finance.ts`, `src/lib/format.ts` e `src/store/store.ts`.
- Varredura ampla do resto de `src/`, `public/` e `index.html` por um subagente, procurando pontos de XSS, uso de `number` para dinheiro, armazenamento, validação de entrada, logs, chamadas externas e acessibilidade. Conferi à mão cada achado que entrou na tabela.
- `npm audit` (completo e `--omit=dev`) e Lighthouse (ver `docs/lighthouse-antes.md`).

**Modelo de ameaça do app atual:** não existe servidor. Os dados ficam cifrados no `localStorage` do navegador. Quem ataca precisa de acesso ao aparelho, de uma extensão maliciosa ou de um XSS. Na versão Java o modelo muda: o app passa a ser acessível pela rede, e surgem ameaças que hoje não existem (seção "O que passa a existir com o servidor").

Escala de gravidade: **Alta** (dá para explorar e expõe ou altera dados), **Média** (exige uma condição extra, ou causa erro de valor), **Baixa** (vaza pouca informação ou é difícil de explorar), **Info** (boa prática, sem falha direta).

## Falhas encontradas

| # | Arquivo | Falha | Gravidade | Correção na versão Java |
|---|---|---|---|---|
| 1 | `src/lib/finance.ts:5-6`, `src/lib/format.ts:44-54`, `src/store/types.ts:39`, `src/store/backup.ts:10` | Dinheiro em `number` (ponto flutuante). As somas acumulam erro de arredondamento (`0.1 + 0.2 ≠ 0.3`), corrigido só no fim com `Math.round(v*100)/100`. O schema do backup aceita frações de centavo e valores negativos até -1e12. | Média | `BigDecimal` no servidor, coluna `NUMERIC(14,2)`, `@Digits(integer=12, fraction=2)` e `@Positive` nos DTOs, arredondamento explícito com `RoundingMode.HALF_UP` (mesma *regra* do `Math.round`, mas sem o erro do ponto flutuante: `Math.round(1.005*100)/100` dá 1,00, e `new BigDecimal("1.005")` com HALF_UP dá 1,01). Ao portar os testes de `finance.test.ts`, uma diferença de meio centavo nesses casos é correção, não bug. O simulador e o assistente hoje calculam dinheiro em JS com `number` (`finance.ts:277-320`, `advisor.ts:240`). Essas contas passam para o servidor (ver "Decisões"). |
| 2 | `src/lib/vault.ts:105-121` | O limite de tentativas fica no próprio `localStorage` (`throttle` dentro do registro da conta). Quem tem acesso ao navegador zera o contador. E quem copia o cofre pode testar senhas offline, sem limite nenhum (o PBKDF2 com 600 mil iterações só deixa isso mais lento). | Média | Limite no servidor, por usuário + IP, com espera crescente. O hash BCrypt nunca sai do servidor, então não existe ataque offline a partir do navegador. |
| 3 | `src/lib/vault.ts:174-179`, `193-195`, `137` | Revela se um usuário existe. (a) Uma conta bloqueada responde "Muitas tentativas" antes de derivar a chave, e um usuário inexistente nunca fica bloqueado. (b) `resetWithRecovery` com usuário inexistente responde na hora, sem o custo do PBKDF2. (c) O cadastro avisa "Já existe uma conta com esse usuário". | Baixa | Mesma mensagem e mesmo custo (BCrypt de mentira) para usuário inexistente, senha errada e código errado. O limite de tentativas usa o nome digitado + IP, então um nome inexistente também é bloqueado. O cadastro continua dizendo que o usuário já existe: sem e-mail, uma mensagem neutra deixaria a pessoa sem saber o motivo, e o ASVS 5.0 só exige essa proteção no nível 3 (6.3.8). Ver "Decisões". |
| 4 | `src/lib/vault.ts:227-257` | `changePassword`, `regenerateRecovery`, `deleteAccount` e `verifyPassword` conferem a senha atual sem nenhum limite. Quem pega o app desbloqueado pode testar senhas à vontade e descobrir uma senha que talvez seja reutilizada em outros sites. | Baixa | Toda reautenticação (trocar senha, novo código, excluir conta) passa pelo mesmo limitador do login. |
| 5 | `src/store/session.ts:11`, `src/lib/vault.ts:125-130` | Um XSS com o app desbloqueado tem acesso à chave. A chave não é extraível, mas o script pode usá-la para decifrar o cofre, ler o estado inteiro na memória (`useStore`) e regravar dados. Nenhum ponto de XSS alcançável foi encontrado: o React escapa o texto por padrão, e o único `dangerouslySetInnerHTML` (falha 13) recebe só valores fixos. Mas, se um XSS existisse, o impacto seria total. | Média | Thymeleaf com `th:text` (escape automático), nunca `th:utext` com dado do usuário. CSP estrita sem `unsafe-inline`, cookie de sessão `HttpOnly`, e nenhuma chave de dados no navegador. |
| 6 | `vite.config.ts:13`, `src/components/common.tsx:131,162,166` | CSP com `style-src 'unsafe-inline'`, necessário por causa do Recharts e dos `style={...}` inline. Permite injeção de CSS (vazamento por seletores, interface falsa). | Baixa | CSP sem `unsafe-inline`. Larguras e cores dinâmicas viram atributos SVG (`width`, `fill`), `<progress>`/`<meter>` ou classes pré-definidas, nunca o atributo `style`. |
| 7 | `vite.config.ts:53-64` | Os cabeçalhos de segurança (`X-Frame-Options`, `frame-ancestors`, `nosniff`) só existem no `vite dev`/`vite preview`. Em hospedagem estática, sobra só a CSP do `<meta>`, que não aceita `frame-ancestors`. Não há HSTS. | Baixa | Spring Security envia CSP (com `frame-ancestors 'none'`), `X-Content-Type-Options`, `Referrer-Policy`, HSTS e `Permissions-Policy` em toda resposta. |
| 8 | `src/store/backup.ts:119`, `151-165` | O backup define quantas iterações de PBKDF2 usar (aceita até 10 milhões). No navegador, isso só trava a aba de quem importa. **Num servidor vira negação de serviço:** um arquivo forjado faz a CPU do servidor trabalhar ~17× mais que um backup normal a cada envio. | Info hoje, **Média na versão Java** | Importação em Java aceita só iterações entre 100 mil e 1 milhão, limite de tamanho do arquivo (`spring.servlet.multipart.max-file-size`) e limite de tentativas por usuário no endpoint. |
| 9 | `src/store/backup.ts:12-113` | A validação do backup (Zod) confere tipos e tamanhos, mas não confere se `categoryId`/`goalId`/`recurringId` apontam para algo que existe, nem se os IDs são únicos, nem um máximo de itens por lista. | Baixa | A importação em Java valida as referências, ignora os IDs do arquivo (gera IDs novos no banco), limita a quantidade de itens e grava tudo numa transação só (tudo ou nada). |
| 10 | `src/components/auth.tsx:233`, `src/pages/Settings.tsx:235` | A interface mostra `e.message` de qualquer exceção. Um erro interno (Web Crypto, armazenamento) aparece cru para o usuário. | Baixa | `@ControllerAdvice` com mensagem genérica e um código de referência. O detalhe fica só no log, sem dados financeiros nem senha. |
| 11 | `src/lib/vault.ts:82` | O nome de usuário é guardado como SHA-256 sem sal. Com uma lista de nomes comuns dá para descobrir o usuário a partir do cofre. | Info | No servidor o usuário precisa ficar legível para o login. A proteção passa a ser o controle de acesso ao banco, e a coluna fica com índice único sem diferenciar maiúsculas. |
| 12 | `src/components/auth.tsx:398-409` | O código de recuperação pode ser baixado como `.txt` junto com o nome de usuário, e fica na pasta Downloads. A tela já avisa para apagar o arquivo. | Info | Na versão Java o código continua aparecendo uma única vez. No servidor fica só o hash BCrypt (o código tem 39 caracteres ASCII, abaixo do limite de 72 bytes do BCrypt). |
| 13 | `src/components/ui/chart.tsx:93-111` | `dangerouslySetInnerHTML` monta um `<style>` com as cores do `config` do gráfico. Hoje todas as chamadas passam valores fixos (`Dashboard.tsx:30-34`, `History.tsx:14-20`, `Simulator.tsx:18-22`), então nenhum dado do usuário chega ali. Se um dia uma cor escolhida pelo usuário entrar, um valor com `</style>` quebraria a página. | Info | Gráficos em SVG sem HTML montado com texto. As cores vêm dos tokens CSS, por classe. |
| 14 | `package.json` (`shadcn` 4.21.4 → `fast-glob` → `micromatch` → `braces` 3.0.3) | `npm audit`: 7 alertas "high" que vêm todos de um único advisory, GHSA-vfj7-8cjw-p6xm (negação de serviço no `braces` com padrões muito aninhados). Só afeta a CLI do shadcn, que é dependência de desenvolvimento e não entra no build. `npm audit --omit=dev`: 0 vulnerabilidades. | Baixa | Some junto com o `legacy-web/`. Enquanto ele existir, o `npm audit` entra na verificação de fim de fase. Na versão Java quem faz esse papel é o OWASP Dependency-Check. |
| 15 | `src/lib/crypto.ts:157-162` | A lista de senhas proibidas tem cerca de 30 senhas. O ASVS 5.0 6.2.4 (nível 1) pede conferir contra **pelo menos as 3000 senhas mais comuns**. | Média (requisito de nível 1 não atendido) | Arquivo de recurso com pelo menos 3000 senhas comuns, consultado no cadastro, na troca e na redefinição de senha. A fonte e a licença da lista ficam registradas no README. |
| 16 | `src/lib/crypto.ts:180-195` | Regra de composição escondida. Uma senha de 8 a 11 caracteres só passa se misturar 3 tipos de caractere (minúscula, maiúscula, número, símbolo). O ASVS 5.0 6.2.5 (nível 1) proíbe exigir tipos de caractere. | Baixa | Política só por tamanho mínimo + lista de senhas comuns + não conter o usuário. O medidor de força continua como dica, sem bloquear. Mínimo de 12 caracteres (ver "Decisões"). |
| 17 | `src/lib/crypto.ts:60`, `src/components/auth.tsx:58` | A senha passa por `normalize('NFKC')` antes de derivar a chave, e o campo aceita até 256 caracteres. O ASVS 5.0 6.2.8 (nível 1) pede conferir a senha exatamente como digitada, sem truncar nem transformar. | Info | Sem normalização. **Limite de 72 bytes em UTF-8**, validado no servidor: o `BCrypt.hashpw` do Spring Security lança `IllegalArgumentException("password cannot be more than 72 bytes")` para senha nova maior que isso (`BCrypt.java:615-616`). Na hora de conferir ele não lança erro, só considera os primeiros 72 bytes, e isso seria truncamento. Por isso o login também recusa senha acima de 72 bytes (como senha errada). Com isso, 64 caracteres ASCII cabem (6.2.9, nível 2), mas 64 caracteres acentuados ou emoji não. |

O que o app atual já faz bem e precisa ter equivalente na versão Java:

- A senha nunca é guardada. O texto da interface diz o porquê, e a mensagem de erro do login é a mesma para usuário e senha.
- A exigência de senha (`checkPassword`, `src/lib/crypto.ts:171`) recusa senhas comuns, senhas curtas e senhas que contêm o nome de usuário. Vai para Java como `PasswordPolicy` + Bean Validation, corrigida segundo as falhas 15–17.
- Trocar a senha pede a senha atual (ASVS 5.0 6.2.3), e o campo de senha tem botão de mostrar e aceita colar (6.2.6 e 6.2.7).
- Bloqueio por inatividade (`src/store/session.ts:92-102`). Vira expiração da sessão no servidor.
- Excluir a conta exige a senha **e** digitar EXCLUIR (`src/pages/Settings.tsx:455-497`).
- Nenhuma requisição externa: fontes locais, sem analytics e sem CDN.
- `form-action 'none'`, `base-uri 'none'` e `object-src 'none'` na CSP. Com Thymeleaf, `form-action` passa a ser `'self'`, porque os formulários enviam para o próprio servidor.

## O que passa a existir com o servidor

Essas ameaças não se aplicam ao app atual, mas precisam estar cobertas desde o primeiro commit Java:

| Ameaça (categoria no OWASP Top 10:2025, conferida pelo CWE) | Onde aparece | Como a versão Java trata |
|---|---|---|
| A01 Acesso de um usuário aos dados de outro (IDOR, CWE-639) | Todas as rotas com `{id}` | `findByIdAndUserId`, 404 quando o registro é de outra pessoa, e teste MockMvc do usuário A tentando o ID do usuário B em cada rota. |
| A01 CSRF (CWE-352) | Todo `POST`/`PUT`/`DELETE` | CSRF do Spring Security ligado, token nos formulários Thymeleaf e no cabeçalho dos `fetch`. |
| A05 Injeção de SQL (CWE-89) | Repositórios e filtros de busca | Só Spring Data/JPQL com parâmetros. Nada de concatenar texto em consulta, nem na ordenação (lista fixa de campos permitidos). |
| A08 Atribuição em massa (CWE-915) | Formulários de lançamento e configurações | DTOs próprios com os campos permitidos. Nunca ligar o formulário direto à entidade (o `user_id` vem da sessão, não do formulário). |
| A02 Configuração exposta | `application.properties`, H2 Console, Actuator | Console do H2 só no perfil `dev` e só em `localhost`. Actuator desligado ou só com `health`. |
| A10 Erro que revela detalhe interno (CWE-209) | Páginas de erro, respostas de validação | `server.error.include-stacktrace=never`, `@ControllerAdvice` com mensagem genérica (falha 10). |
| A07 Força bruta e fixação de sessão (CWE-307, CWE-384) | Login, recuperação, reautenticação | Limite por usuário+IP com espera crescente, troca do ID de sessão no login, cookie `HttpOnly`/`Secure`/`SameSite=Lax` e expiração. |
| A09 Logs com dado sensível (CWE-532) | Todo log | Nunca logar valores, descrições, senhas, códigos nem o corpo das requisições. Logar só IDs e eventos (ex.: "login falhou para id X"). |
| A07 Segredos no código (CWE-798) | Configuração do banco | `DB_URL`, `DB_USER` e `DB_PASSWORD` em variáveis de ambiente. `.env.example` sem valores. O `.gitignore` já cobre `.env`. |
| A03 Dependência vulnerável | `pom.xml` | OWASP Dependency-Check no `verify`, sem vulnerabilidade alta para fechar uma fase. |
| Negação de serviço (sem categoria própria no Top 10:2025) | Importação de backup, BCrypt no login | Limite de tamanho do arquivo, faixa de iterações (falha 8) e limite de tentativas, que também segura o custo do BCrypt. |

## Inventário de UX

Comparado com as regras de "Experiência de uso" do `CLAUDE.md`.

| Regra | Situação no app atual | O que fazer na Fase 3 |
|---|---|---|
| Registrar gasto em poucos toques, data de hoje | Atende parcialmente. Abre um diálogo (`TransactionDialog.tsx`) com valor em foco, `inputmode="decimal"` e data de hoje quando o mês exibido é o atual. Categoria é obrigatória e fica num `Select` com rolagem. | Formulário curto no topo do mês, sem diálogo. Valor → categoria → salvar. As categorias mais usadas aparecem primeiro. |
| Topo responde gastei / sobra / posso investir | Atende parcialmente. O topo mostra "Saldo livre do mês" em destaque (`Dashboard.tsx:96`) e, ao lado, Receitas, Gastos e Guardado (`Dashboard.tsx:130-142`). "Investimentos" (quanto investir) só aparece mais abaixo, num card (`Dashboard.tsx:181`). | Os três números no topo, nessa ordem, com o aviso "estimativa educativa, não é recomendação" junto. |
| Estado vazio | Existe no painel, lançamentos, contas fixas, histórico e planejamento. **Falta em Metas**: sem metas além da reserva, a lista fica em branco (`Goals.tsx:172-185`). | Estado vazio em toda lista, dizendo o que fazer ("Crie sua primeira meta"). |
| Estado carregando | Só o fallback da rota (`AppShell.tsx:236`). O componente `Skeleton` existe mas não é usado. | Esqueleto com espaço reservado nas partes que vêm do servidor (Fase 5). |
| Estado de erro | `ErrorBoundary` com botão de recarregar, faixa de erro ao salvar e mensagens nos formulários. | Página de erro do servidor com mensagem que diz o que fazer, e erros de validação ao lado do campo. |
| Apagar pede confirmação ou permite desfazer | Lançamentos, troca de regra e categoria arquivada têm "Desfazer". **Apagam na hora, sem confirmação nem desfazer:** conta fixa (`Recurring.tsx:40-43`), meta (`Goals.tsx:180-183`), pote (`Planning.tsx:135`) e categoria sem uso (`Planning.tsx:257-262`). | "Desfazer" com exclusão adiada no servidor em todos. Sem JS, cai numa tela de confirmação. |
| Alvos de toque ≥ 44px | Não atende. Há 33 linhas em `src/pages` e `src/components` com `size-8`/`size-9`/`h-8`/`h-9` (32–36px), boa parte em botões, por exemplo o lixo da categoria (`Planning.tsx:298`, 32px) e o do pote (`Planning.tsx:131-139`, 36px). | Token de tamanho mínimo de 44px para tudo o que é clicável. |
| Labels, `aria-label` em botões só com ícone | Atende. Todos os botões só com ícone têm `aria-label`, e todos os campos têm `<Label>` ou `aria-label`. | Manter, e somar o tooltip no foco planejado. |
| Acessibilidade medida | Lighthouse 95. A falha é `aria-controls` apontando para um painel que não existe nas abas Entrar/Criar conta (`auth.tsx:166-175`). | Na versão Thymeleaf, Entrar e Criar conta viram páginas separadas, sem abas. |
| Gráfico só onde responde uma pergunta | Gráficos em 4 telas: painel (colunas de 6 meses), histórico (colunas e linha), planejamento (pizza) e simulador (área). A pizza do planejamento repete o que os percentuais já dizem, e as colunas do painel repetem o histórico. | Gráfico só no histórico (entradas × saídas) e no simulador (crescimento). |
| Aviso de estimativa educativa | Atende: painel (`Dashboard.tsx:224`), investimentos (`Invest.tsx:251`), simulador (`Simulator.tsx:239`) e assistente (`AdvisorChat.tsx:136`). | Manter o texto e deixá-lo junto do número "posso investir", no topo. |

## Inventário de visual

Comparado com as regras de "Visual" do `CLAUDE.md`.

| Regra | Situação no app atual |
|---|---|
| Evitar o visual padrão de IA | Não atende. É o tema padrão do shadcn/ui: azul `#1d4ed8` como cor principal (`index.css:71`), cantos arredondados, e 37 `<Card>` nas páginas, que formam grades de cards iguais. Os ícones de categoria ficam em quadrados coloridos (`common.tsx:113-136`), e a tela de login tem o painel azul com três itens de "benefício". |
| Fonte com personalidade nos títulos | Não atende. IBM Plex Sans em tudo, e `--font-heading` aponta para a mesma fonte (`index.css:12-13`). |
| `tabular-nums` nos valores | Atende. Existe a classe `tabular`, usada nos campos e valores (`common.tsx:43`). |
| Tokens num arquivo só | Atende parcialmente. Tudo está em `src/index.css`, mas misturado com o tema do shadcn e com cinco paletas de gráfico. |
| Ícones de uma biblioteca só | Atende (Lucide). |
| Hierarquia por tamanho, peso e espaço | Não atende. A hierarquia vem quase toda de caixas (cards dentro de grades). |

Esses pontos entram na Fase 4. A direção proposta no plano ("livro-caixa sóbrio", papel claro, tinta escura e um verde de destaque) resolve a cor e as caixas. A fonte dos títulos ainda precisa ser escolhida.

## Decisões (tomadas pelo Fabrício em 13/10/2026)

- **Dinheiro no JS:** opção (b). O servidor calcula em `BigDecimal` tudo o que envolve dinheiro no simulador e no assistente (pontos da projeção, aporte necessário, divisão da carteira). O JS só desenha e monta o texto. O simulador deixa de funcionar offline.
- **Cadastro com usuário repetido:** a tela diz que o usuário já existe. O login e a recuperação continuam com mensagem neutra e mesmo custo de tempo.
- **Senha:** mínimo de 12 caracteres, máximo de 72 bytes em UTF-8, sem regra de composição, conferida contra pelo menos 3000 senhas comuns e sem conter o nome de usuário.

As opções que foram apresentadas:

1. **Dinheiro no JS que fica no navegador.** O simulador, o assistente e o questionário continuam em JS (decisão já tomada), mas o `CLAUDE.md` proíbe `double`/`float` para dinheiro, e o JS só tem `number`. Opções:
   - (a) o JS trabalha em **centavos inteiros** com arredondamento explícito a cada mês. Um `number` guarda inteiros exatos até 9 quadrilhões, então centavos de qualquer valor realista ficam exatos. Os juros (taxa elevada a 1/12) continuam com fração, mas o resultado de cada mês volta a ser centavo inteiro;
   - (b) o servidor calcula os pontos da projeção em `BigDecimal` e o JS só desenha (o simulador deixa de funcionar offline);
   - (c) exceção documentada: projeções são estimativas e podem usar `number`.
2. **Mensagem do cadastro com usuário repetido.** Dizer "esse usuário já existe" revela quem tem conta. Uma mensagem neutra deixa a pessoa sem saber o que fazer, porque o app não tem e-mail para avisar. O ASVS 5.0 só exige essa proteção no nível 3 (6.3.8). O login e a recuperação ficam neutros de qualquer jeito.
3. **Tamanho mínimo da senha.** Sem regra de composição (falha 16), o tamanho passa a ser o que define a força. O ASVS 5.0 exige no mínimo 8 e "recomenda fortemente" 15 (6.2.1). O app atual, na prática, pede 12 para quem não mistura tipos de caractere.

## Resumo

- Nenhuma falha **Alta** no app atual.
- **Médias:** dinheiro em ponto flutuante (1), limite de tentativas que dá para zerar (2) e lista de senhas comuns curta demais para o ASVS nível 1 (15). A falha 5 (XSS) é Média pelo impacto, mas nenhum ponto de entrada foi encontrado.
- A falha 8 não pesa hoje, mas vira **Média na versão Java** e precisa de cuidado na tela de importação.
- A versão Java resolve 1–7 e 10 pela própria arquitetura (servidor, BCrypt, Spring Security, Thymeleaf), desde que a seção "O que passa a existir com o servidor" seja seguida. As falhas 15–17 precisam de código específico na política de senha.
- Dependências de produção: 0 vulnerabilidades. Dependências de desenvolvimento: 1 advisory (7 alertas), só na CLI do shadcn.
