# Plano: migração do app Finanças para Java + segurança, UX, visual e desempenho

## Contexto
Hoje o app é só React/TypeScript, sem servidor (~11 mil linhas, 51 testes Vitest passando). Os dados ficam no `localStorage`, criptografados (AES-GCM + PBKDF2). Dinheiro usa `number` (ponto flutuante).
Decisões já tomadas por você:
- Os dados passam a ficar no servidor (PostgreSQL), e a página de Privacidade e o README serão reescritos.
- Interface em Thymeleaf + JS puro.
- Simulador, assistente offline e questionário de perfil continuam como JS no navegador (as contas de dinheiro foram para o servidor, ver "Decisões da Fase 1").
- Você tira a pasta do OneDrive e eu inicio o git.

Versões confirmadas na doc oficial em 13/10/2026: **Java 25 (LTS)** e **Spring Boot 4.1.1**, que exige Java 17 ou mais e funciona até o Java 26.

### Decisões da Fase 1 (13/10/2026)
Detalhes em `docs/auditoria-seguranca.md`, seção "Decisões".
- **O dinheiro do simulador e do assistente é calculado no servidor** (`BigDecimal`). O JS só desenha o gráfico e monta o texto, e o questionário de perfil continua todo em JS (não envolve dinheiro). Isso substitui "simulador continua como JS" no que diz respeito às contas. Na Fase 2 entram `SimulationService` (projeção, meses até a meta, aporte necessário) e o cálculo da divisão da carteira, com testes portados de `finance.test.ts` e `advisor.test.ts`. Na Fase 3, o simulador chama o servidor com `debounce` ao mudar os campos.
- **Cadastro** diz quando o usuário já existe. Login e recuperação seguem neutros.
- **Senha:** mínimo de 12 caracteres, máximo de 72 bytes UTF-8 (limite do BCrypt, conferido em login e cadastro), sem regra de composição e conferida contra pelo menos 3000 senhas comuns (ASVS 5.0 6.2.1, 6.2.4, 6.2.5 e 6.2.8).

## Fase 0: preparação (o que você faz + linha de base)
**O que você faz, à mão:**
1. Mover a pasta para fora do OneDrive (ex.: `C:\dev\Sistema-controle-financeiro`) e abrir o Claude Code lá.
2. Instalar o JDK 25 (Eclipse Temurin) e configurar `JAVA_HOME`. O Maven não precisa instalar, porque vou usar o Maven Wrapper (`mvnw`).

**O que eu faço:**
- Completar o `.gitignore` com `.env`, `target/`, `*.mv.db`, `*.trace.db` e `legacy-web/node_modules`.
- `git init`, depois um commit do estado atual na `main`. Depois disso a `main` não é mais alterada.
- Criar a branch `melhorias-java`.
- Medir o Lighthouse do app React atual (`npm run build && npm run preview`) para ter o "antes" real da Fase 5. Se não conseguir rodar, aviso.

Não existe histórico git anterior, então não há segredos antigos a procurar. Vou varrer só os arquivos atuais.

## Fase 1: auditoria de segurança (sem reescrever)
- Um subagente Explore (haiku) faz a varredura ampla. Depois eu leio de fato os arquivos críticos: `src/lib/crypto.ts`, `vault.ts`, `store/session.ts`, `store/backup.ts`, `components/auth.tsx`, `vite.config.ts` e as páginas com entrada de usuário.
- O resultado é o arquivo `docs/auditoria-seguranca.md`, com uma tabela de arquivo, falha, gravidade e correção na versão Java. Também cobre um inventário curto de UX e visual.
- Falhas que já estão confirmadas:
  - dinheiro em `number` (`src/lib/finance.ts:6`, `src/lib/format.ts:44`);
  - limite de tentativas guardado no `localStorage` (`vault.ts:114`), que dá para zerar;
  - CSP com `style-src 'unsafe-inline'`;
  - um XSS com o app desbloqueado exporia a chave.
- Commit, e aviso para você rodar `/security-review`.

## Fase 2: backend Java (o maior bloco, com vários commits)
**Estrutura:**
- O React vai para `legacy-web/`. Fica lá até as funcionalidades estarem equivalentes, e só apago com o seu ok.
- Java em `src/main/java/br/.../financas/`, com os pacotes `controller`, `service`, `repository`, `domain`, `dto`, `config` e `security`.

**Dependências** (uma linha de motivo para cada, também no README). Os nomes foram conferidos no Spring Initializr com o Boot 4.1.1 em 13/10/2026. O Boot 4 dividiu os starters, então alguns nomes mudaram em relação à versão anterior deste plano:
- `spring-boot-starter-webmvc` (antes `-web`): rotas HTTP.
- `-thymeleaf`: HTML no servidor, com escape automático.
- `-security`: login, BCrypt, CSRF e cabeçalhos.
- `-data-jpa`: repositórios e paginação.
- `-validation`: Bean Validation.
- `spring-boot-starter-flyway` (antes `flyway-core`) + `flyway-database-postgresql`: migrações.
- `h2` e `postgresql` (runtime).
- Testes: `spring-boot-starter-webmvc-test` (JUnit 5 e MockMvc), `-security-test` e `-data-jpa-test`.
- Plugin `dependency-check-maven` 13.0.0.

O que ficou de fora:
- `thymeleaf-extras-springsecurity6`. A documentação do Spring Security confirma que o Thymeleaf já põe o token CSRF nos formulários `th:action`, e o usuário logado vai para o template pelo controller.
- `spring-boot-h2console`, que o Initializr adiciona junto com o H2. Um console do banco exposto é uma falha A02.
- `-thymeleaf-test`, `-validation-test` e `-flyway-test`. Os POMs mostram que eles só trazem o `spring-boot-starter-test`, que já vem pelos outros, mais o starter principal.

O limite de login será código próprio, sem biblioteca extra.

**Domínio:** `User`, `Category`, `Bucket` (regra configurável), `Transaction`, `Recurring` (fixas e parcelamentos), `Goal`, `Settings`.
- Dinheiro em `BigDecimal` com `NUMERIC(14,2)`. Percentuais também em `BigDecimal`.
- Todas as associações `LAZY`. Toda entidade financeira tem `user_id`.

**Flyway:**
- `V1__schema.sql` cria as tabelas, os índices `(user_id, date DESC, id DESC)` e `(user_id, month)`, e as FKs.
- `V2__seed_presets.sql` insere as regras 50/30/20, 70/20/10, 6 potes e 80/20, vindas de `src/lib/presets.ts`.

**Migração dos dados existentes:**
- Não dá para fazer por Flyway, porque os dados estão cifrados com a senha de cada pessoa.
- No app novo haverá a tela "Importar backup do app antigo". Ela aceita o arquivo `.json` (cifrado ou não), lê o formato de `src/store/backup.ts` e decifra em Java com PBKDF2WithHmacSHA256 + AES/GCM e AAD `financas:backup:v1`.
- O arquivo é validado campo a campo e os valores são convertidos para BigDecimal com 2 casas.
- Tem teste com um backup gerado pelo app antigo.

**Services e regras:**
- `MonthSummaryService` usa consultas `SUM ... GROUP BY` no repositório para responder quanto gastei, quanto entrou e quanto sobra.
- `InvestmentSuggestionService` aplica a regra configurável (potes + prioridade da reserva), portada de `src/lib/finance.ts`. Os casos de `src/lib/finance.test.ts` viram testes JUnit com os mesmos valores esperados.
- Contas fixas e parcelamentos são gerados por mês, com a mesma regra de `skipped`.

**Segurança:**
- Senhas com BCrypt via `DelegatingPasswordEncoder`.
- Sessão com expiração, cookie `HttpOnly`, `Secure` e `SameSite=Lax`, e troca do id de sessão no login.
- CSRF ligado.
- Limite de tentativas por usuário+IP no servidor, com espera crescente.
- Mesma mensagem para usuário inexistente e senha errada.
- Cabeçalhos: CSP sem `unsafe-inline`, nosniff, `Referrer-Policy`, HSTS, `frame-ancestors 'none'`.
- Erros tratados por `@ControllerAdvice` com mensagem genérica. Logs nunca registram valores nem senha.
- Segredos (URL, usuário e senha do banco) vêm de variáveis de ambiente, com `.env.example` sem valores.
- A recuperação de senha muda: o código de recuperação passa a ser um hash BCrypt no servidor (a explicação vai no README).

**Autorização:**
- Todo repositório consulta por `findByIdAndUserId` e responde 404 quando o registro não é do usuário.
- Testes MockMvc cobrem o usuário A tentando ler, editar ou apagar o ID do usuário B em cada rota.

**Paginação:**
- `Pageable` com `spring.data.web.pageable.default-page-size=20` e `max-page-size=100`, que reduz automaticamente pedidos maiores.
- Ordenação fixa `date DESC, id DESC`.
- Navegação com "Carregar mais"/numerada, com página e filtros na URL, para o estado voltar depois de editar.

**N+1:**
- `spring.jpa.open-in-view=false`.
- A listagem usa projeção ou DTO com JOIN na categoria (associação to-one).
- No dev, `hibernate.generate_statistics` + log de SQL.
- Um teste com `Statistics` do Hibernate compara o número de queries com 5 e com 50 lançamentos, e falha se crescer.

**Fim da fase:**
- `./mvnw verify`, OWASP Dependency-Check e `npm audit` (enquanto `legacy-web` existir).
- README em pt-BR com instruções de rodar, testar e configurar.
- Aviso para você rodar `/security-review`.

### O que mudou na execução da Fase 2 (13/10/2026)
- **Pacote:** `io.github.fabasdev.financas` (escolha do Fabrício).
- **Ids:** os ids do app antigo ("cat-moradia", "goal-reserva") eram iguais para todo mundo. No banco, cada registro tem id gerado, e as categorias e metas padrão ganharam uma `system_key` por usuário.
- **Índice por mês:** o lançamento não tem mais coluna `month`, porque o mês vem da data. O índice `(user_id, entry_date DESC, id DESC)` atende tanto a listagem quanto o resumo do mês. As contas fixas usam `recurring_month` com UNIQUE, para não gerar o mesmo mês duas vezes.
- **Importação:** o app antigo só exporta backup cifrado (`Settings.tsx`), então só esse formato é aceito.
- **Perfil de investidor:** só o resultado fica no banco. As respostas do questionário ficam no navegador.
- **Limite de login:** além de usuário+IP, há contador por usuário (20) e por IP (50), como recomenda o Authentication Cheat Sheet.
- **Pendente da Fase 2:** rodar o Dependency-Check quando a chave da NVD (`NVD_API_KEY`) existir; testar as migrações num PostgreSQL de verdade (até agora só no H2 em modo PostgreSQL).

## Fase 3: experiência de uso
- Novo gasto: formulário curto no topo do mês, com data de hoje, categoria e valor com teclado numérico (`inputmode="decimal"`).
- O topo da tela mostra três números: gastei, sobra, posso investir. Junto vem um aviso fixo de que é uma "estimativa educativa, não recomendação".
- Validação no servidor e no formulário, mensagens ao lado do campo, e estados vazio/carregando/erro com instrução do que fazer.
- Apagar tem "Desfazer" por alguns segundos (exclusão adiada no servidor). Sem JS, cai numa confirmação.
  - *Como ficou (Fase 3):* em vez de adiar a exclusão, o registro é apagado de verdade e uma cópia dele e das ligações fica na sessão por 10 minutos. O "Desfazer" recria tudo. Com exclusão adiada, toda soma, saldo e listagem precisaria ignorar registros "meio apagados", e um esquecimento distorceria os números. Vale para lançamento, conta fixa, meta, categoria, pote e troca de regra. Com JS, o "Apagar" vai direto e o "Desfazer" aparece na mensagem. Sem JS, a página de confirmação continua.
- Botões com texto. Os que têm só ícone ganham `aria-label` e um tooltip no hover e no foco, ligado por `aria-describedby`.
- Alvos de toque de 44px, foco visível, labels, uso completo pelo teclado e contraste AA, conferidos com a skill de auditoria de acessibilidade.
- Gráficos só em duas telas:
  - Histórico: entradas × saídas por mês;
  - Simulador: crescimento ao longo do tempo.
- Gráficos em SVG gerado no servidor ou no JS puro. Não entra biblioteca nova sem eu perguntar.

### Como a Fase 3 ficou (13/10/2026)
- Lançamento rápido no topo (visão geral e lançamentos), com as categorias mais usadas nos últimos 90 dias primeiro.
- "Desfazer" em todas as exclusões, inclusive troca de regra (ver a nota acima sobre restauração em vez de exclusão adiada).
- Gráficos: histórico em SVG gerado no servidor e simulador em JS chamando `/api/simulacao` com debounce. Seguem a skill dataviz, com paleta provisória validada pelo script dela.
- Questionário de perfil e assistente em JS, portados de `advisor.ts`, sem conta de dinheiro no navegador.
- Assistente inicial e dados de exemplo (paridade com o app antigo).
- Validação no formulário (mensagem ao lado do campo) além da do servidor.
- Acessibilidade: alvos de 44px, foco visível, rótulos e Lighthouse 100 nas 14 telas testadas.
- Os botões só com ícone (aria-label + tooltip) ficam para a Fase 4, porque hoje todo botão tem texto e os ícones chegam com o visual.

## Fase 4: visual
- Carregar a skill `frontend-design` e consultar o repo anthropics/skills.
- Direção em duas linhas para você aprovar antes de implementar: algo na linha de um livro-caixa sóbrio (papel claro, tinta escura, um verde de destaque), com números grandes em fonte tabular.
- `static/css/tokens.css` com cor, tipografia e espaçamento, e um tema escuro pelos mesmos tokens.
- Fonte de títulos com personalidade + fonte de números com `tabular-nums`, hospedadas localmente. Ícones só do Lucide (SVG estático, mesma biblioteca que já usamos).
- Registrar a direção escolhida no CLAUDE.md.

### Como a Fase 4 ficou (14/10/2026)
> As cores abaixo foram trocadas pela "Noite azul", a seu pedido, no fim do mesmo dia (seção no fim deste arquivo). Tipos, princípios e o layout dos números continuam.

A skill `frontend-design` não estava instalada; o `SKILL.md` foi lido direto de github.com/anthropics/skills. A direção aprovada virou este plano, revisado contra a lista de "padrões de IA" da própria skill antes de escrever o CSS.

**Direção (duas linhas):** livro-caixa sóbrio, com papel claro levemente esverdeado, tinta azul-escura, um verde de destaque e vermelho só para valor negativo. Os números são o protagonista: todo valor em Besley com algarismos tabulares, e o saldo do mês leva o traço duplo da contabilidade.

**Cores** (`static/css/tokens.css`, com o tema escuro pelos mesmos nomes):

| Nome | Claro | Escuro | Uso |
|---|---|---|---|
| papel | `#f3f5f1` | `#172124` | fundo |
| tinta | `#1b2a33` | `#e2e8e3` | texto e números |
| lápis | `#4b5b61` | `#a7b4ae` | rótulos e texto de apoio |
| pauta | `#c5cfc8` | `#34443f` | linha entre registros |
| verde | `#17603f` | `#7ccb9f` | ação principal, links, "posso investir" |
| tinta vermelha | `#a3322b` | `#f08a7e` | saldo negativo e erro, sempre com texto |

**Tipos:** Besley nos títulos e em todo valor; Atkinson Hyperlegible Next no texto.
- A Besley é uma serifa inspirada nos desenhos de Robert Besley, o criador da Clarendon, e é mais pesada que as fontes digitais comuns. Os números dela lembram os impressos antigos, que é a ideia do livro-caixa.
- A Atkinson Hyperlegible Next é a versão melhorada da Atkinson Hyperlegible, pensada para ser fácil de ler, como diz o nome. O nome homenageia o fundador do Braille Institute.
- As duas descrições foram conferidas nos READMEs oficiais (indestructible-type/Besley e googlefonts/atkinson-hyperlegible-next).
- Testei a Atkinson nos números, mas o zero dela é cortado (Ø) e deixava as tabelas com cara de código. A fonte não tem zero alternativo.

**Layout:** alinhado à esquerda, sem caixas. No celular:

```
Finanças                 [≡ Menu] [Sair]
Outubro de 2026
‹ Setembro de 2026   Novembro de 2026 ›
Gastei                       R$ 3.828,98
────────────────────────────────────────
Sobra                       R$ 1.671,02
                            ═══════════
Posso investir                R$ 390,00
Estimativa educativa, não é recomendação.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Registrar um gasto
[Valor      ] [Categoria  ▾]
```

Na tela larga, números e tabelas ficam à esquerda e o lançamento rápido, fixo numa coluna à direita.

**Princípios:**
- O único elemento ousado é o topo com os números grandes; o resto fica quieto.
- Linhas só onde um livro-caixa teria: entre registros, sob o cabeçalho da tabela e o traço duplo sob totais.
- Hierarquia por tamanho, peso e espaço. Sem cartões, sombras ou degradês.

**Revisão contra os padrões genéricos da skill (o que mudou):**
- O topo era uma grade de três "cartões" iguais (`auto-fit`), o padrão "número grande com rótulo pequeno". Virou uma coluna de livro-caixa com pesos diferentes: "Sobra" maior e com traço duplo, "Posso investir" em verde.
- O papel creme quente (`#F4F1EA`) é o primeiro item da lista de clichês. Por isso o papel é frio e esverdeado, como o papel pautado de contabilidade.
- As setas "←" e "→" coladas no texto dos links de mês viraram ícones. Os separadores "—" e "·" no meio de frases viraram um formato só.
- As linhas finas aparecem só onde carregam informação, não por decoração.

**Outras decisões:**
- **Ícones:** só do Lucide e sempre ao lado de um texto. Nenhum botão depende só do ícone, então o tooltip previsto na Fase 3 não foi necessário. Um tooltip acessível (WCAG 1.4.13) precisaria de polyfill ou de JS próprio.
- **Navegação no celular:** dentro de "Menu" (`<details>`, funciona sem JS; com JS, Esc fecha). A página atual tem `aria-current`.
- **Tabelas no celular:** as listas com ações viram um bloco por registro. As tabelas largas de números rolam dentro de uma região que recebe foco.
- **Gráficos:** as cores vêm dos tokens e foram validadas com o script da skill dataviz no papel claro e no escuro.

**Verificação:**
- Lighthouse 13.5.0 deu 100 em acessibilidade nas 18 telas testadas (só tema claro).
- No tema escuro, o contraste foi medido por um script próprio (texto, bordas de campo e de botão): tudo passa.
- Fotos em 390 e 1280 px, nos dois temas: sem rolagem horizontal, sem erro de console e sem violação de CSP.
- Fluxos no navegador com cliques de mouse nos ícones: cadastrar, lançar, marcar como pago, editar, apagar e desfazer, menu do celular e importar o backup antigo (com senha errada e com a certa). Também rodaram de novo os roteiros da Fase 3 (simulador ao vivo, gráfico, questionário e o teste de XSS pelo nome).
- Tudo foi testado só no Chrome. Safari e Firefox não foram testados.

**Pendência:** o gráfico do histórico é um SVG de largura fixa e encolhe no celular. A letra dos eixos aumenta nessa largura, mas continua pequena. A tabela logo abaixo tem os mesmos números.

## Fase 5: desempenho da renderização
- Lighthouse no app novo, comparado com a linha de base da Fase 0. Relato só valores medidos.
- Scripts com `defer`, CSS mínimo e cache dos assets estáticos (`spring.web.resources.cache`).
- Fontes com `font-display: swap` e preload só da fonte da primeira tela.
- Esqueletos de carregamento com espaço reservado.
- Atualização pontual do DOM com `DocumentFragment`, `debounce` na busca e `throttle` em scroll/resize.
- Animações só com `transform`/`opacity` e respeito a `prefers-reduced-motion`.

### Como a Fase 5 ficou (14/10/2026)
A medição veio primeiro: as 7 telas principais, antes e depois, com o mesmo Lighthouse da Fase 0. Os números estão em `docs/lighthouse-depois.md`. O que estava pior era Investimentos (nota 79, CLS 0,436), e boa parte da fase foi para isso.

**O que mudou:**
- **Cache dos arquivos estáticos:** CSS e fontes levam o hash do conteúdo no nome e ficam 1 ano no cache. O JavaScript não pode usar o hash, porque os módulos se importam por caminho relativo (`./lib/http.js`); a própria documentação do Spring indica a versão no caminho para esse caso. Por isso a URL é `/js/v4220…/app.js`, e a versão é um resumo de todos os `.js` (`StaticResourcesConfig`). O caminho continua em `/js/`, então a regra pública do `SecurityConfig` não mudou. No perfil `dev`, o navegador sempre confere se o arquivo mudou.
- **Compressão** só de CSS, JS e SVG. HTML e JSON ficam de fora por causa do BREACH (token CSRF ao lado de texto digitado).
- **Espaço reservado:** o questionário é montado dentro de um `<details>` fechado ("Responder as perguntas"); o gráfico do simulador já vem com o tamanho certo e a pauta vazia enquanto carrega; a linha "Calculando…" tem altura fixa. Quem decide o que aparece sem JavaScript é o CSS (`@media (scripting: none)`), não o script, para a página já nascer com o layout final.
- **Preload só da Besley**, a fonte dos títulos, que aparece no topo de toda tela. Medi com e sem: com ele o CLS ficou em 0 em 6 das 7 telas (no Histórico, 0,002) e o FCP caiu de 0,1 a 0,2 s. A Atkinson (texto) não ganhou preload.
- **Tooltip do simulador:** o `pointermove` desenha no máximo uma vez por quadro (`requestAnimationFrame`) e não remonta o tooltip se o ano não mudou.
- **`prefers-reduced-motion`:** o app não tem animação; ficou uma regra no CSS para uma animação futura respeitar a preferência.

**Itens do plano que não precisaram de mudança:**
- **`defer` nos scripts:** os scripts já são `type="module"`, que o navegador carrega sem bloquear a página, como o `defer`.
- **`font-display: swap`:** já estava nas duas fontes desde a Fase 4.
- **CSS mínimo:** procurei classes sem uso no `app.css` e não achei nenhuma.
- **`DocumentFragment`:** o JavaScript já insere tudo de uma vez com `replaceChildren(...)`, que tem o mesmo efeito.
- **`debounce` na busca:** a busca de Lançamentos é um formulário com botão "Filtrar", sem nada rodando enquanto se digita. O simulador já esperava 400 ms.
- **`throttle` em scroll/resize:** não há nenhum desses eventos no app. O único evento de alta frequência era o `pointermove` do gráfico (acima).

**Verificação:**
- `./mvnw verify` com 220 testes JUnit, 6 deles novos no `StaticResourcesTest` (URLs com versão, cache de 1 ano sem login, imports relativos do JS, fontes reescritas dentro do CSS, CSS comprimido e HTML não). Os 35 testes de JavaScript também passaram.
- Fluxos no Chrome (390 e 1280 px): questionário completo, assistente, simulador digitando, tooltip com mouse e teclado, e as duas telas com JavaScript desligado. Sem erro de console, de CSP ou de rede.
- Roteiro padrão do plano no perfil `dev` (banco em memória), clicando como uma pessoa: cadastrar, entrar, lançar, editar, apagar e desfazer (pelo `app.js` com versão no caminho), marcar como pago, ver o resumo e importar o backup antigo. Tudo passou.
- `/security-review` sem achados. Ela apontou que o teste "HTML não é comprimido" passaria à toa se a página tivesse menos de 2 KB; o teste ganhou essa trava.
- Cabeçalhos conferidos com `curl`, no perfil de produção e no `dev`.

**Pendências:**
- `favicon.svg` e `robots.txt` ficam 1 ano no cache sem versão no nome. Se um deles mudar, é preciso trocar o nome do arquivo.
- O SEO do `/cadastro` e das telas logadas fica em 54 porque o `robots.txt` bloqueia a indexação, de propósito. Também falta `meta description`.
- Só medido no Chrome e em `localhost`.

## Verificação (ao fim de cada fase)
- `./mvnw verify`, que roda os testes JUnit, incluindo autorização entre usuários, contagem de queries e cálculos portados.
- `./mvnw spring-boot:run` com perfil `dev` (H2) e o fluxo testado no navegador: cadastrar, lançar, editar, apagar e desfazer, ver o resumo e importar o backup antigo.
- Dependency-Check sem vulnerabilidade alta. Commit por fase, mensagem em pt-BR e aviso para `/security-review` e `/compact`.

## Entrega final
Relatório curto por fase, pendências e o que você precisa fazer à mão:
- variáveis `DB_URL`, `DB_USER`, `DB_PASSWORD`;
- criar o banco PostgreSQL de produção;
- HTTPS/certificado;
- exportar o backup no app antigo e importar no novo;
- apagar `legacy-web/` quando estiver satisfeito.

### Como a Entrega final ficou (14/10/2026)
O relatório por fase e o passo a passo à mão estão em `docs/entrega-final.md`. A conferência com o app antigo achou três funções que faltavam: exportar os dados, a lista de pendências na tela inicial e a escolha de tema. Com a sua autorização, as três entraram no mesmo dia (`905ba89`, `b65cf87`, `24a5675`). Os detalhes estão no mesmo arquivo, em "O que faltava do app antigo".

### Pendências resolvidas depois da Entrega (14/10/2026)
Detalhes em `docs/entrega-final.md`, seção "Pendências resolvidas depois do relatório".
- **Senha:** mínimo de 15 caracteres e Argon2id (`66fe6aa`). Isso substitui a decisão da Fase 1 (12 caracteres) e o BCrypt da Fase 2. Senhas antigas viram Argon2id no primeiro login.
- **Gráfico do histórico no celular:** desenho próprio para a tela estreita (`88dd19a`, `fe0faba`).
- **PostgreSQL de verdade:** migrações e roteiros testados no PostgreSQL 18.6. O banco de produção deve ser criado com `ICU_LOCALE 'pt-BR'`, para a busca igualar maiúsculas acentuadas.
- **Dependências:** o Dependency-Check não roda sem a chave da NVD (a 13.0.0 recusa, e a 12.2.2 levou erro 429 em 27%). A conferência foi feita na base OSV e achou falhas no Tomcat 11.0.24 e no Jackson 3.1.5, corrigidas fixando Tomcat 11.0.27 e Jackson 3.1.7 (`48dd63a`). A checagem do plano continua pendente da chave.

## Noite azul: cores novas e ajustes de uso (14/10/2026)
Você achou que as cores do livro-caixa esverdeado não ficaram boas, preferiu tons de azul mais escuro e pediu para usar as skills `ui-ux-pro-max` e `ui-styling`.
- A `ui-styling` é feita para React com shadcn e Tailwind. Usar ela aqui traria um build em Node para um app Thymeleaf, então só os princípios dela foram usados (tokens, contraste, tema escuro coerente).
- A sugestão de sistema visual da `ui-ux-pro-max` para finanças era glassmorphism com uma fonte manuscrita. O CLAUDE.md proíbe glassmorphism, então essa parte ficou de fora. As paletas de azul-marinho e as regras de experiência dela entraram.
- Três paletas foram aplicadas nas telas de verdade (trocando só o `tokens.css`) e comparadas lado a lado: "Tinta azul", "Faixa marinho" e "Noite azul". Você escolheu a **Noite azul**, manteve a Besley nos valores e a regra de hierarquia sem caixas.

**Direção (duas linhas):** noite azul. Fundo azul-marinho escuro por padrão, texto claro, um azul-céu de destaque e vermelho só para valor negativo; o claro (papel frio, tinta azul-marinho, destaque azul-cobalto) é escolha em Ajustes. Os números continuam o protagonista, em Besley com algarismos tabulares e o traço duplo sob o saldo.

**Cores** (`static/css/tokens.css`):

| Nome | Escuro (padrão) | Claro | Uso |
|---|---|---|---|
| `--paper` | `#0c1b33` | `#f4f6f9` | fundo |
| `--paper-raised` | `#132744` | `#ffffff` | campos e balões |
| `--ink` | `#edf2fa` | `#13213a` | texto e números |
| `--ink-muted` | `#a8b6cd` | `#4a5770` | rótulos e texto de apoio |
| `--rule` | `#233a5c` | `#d3d9e3` | linha entre registros |
| `--rule-strong` | `#7a8aa8` | `#6a768c` | borda de campo e botão |
| `--accent` | `#7cc0ff` | `#1f4e9c` | ação principal, links, "posso investir" |
| `--negative` | `#ff8f87` | `#b02a2a` | saldo negativo e erro, sempre com texto |
| séries | `#5aa9f5` `#e0a548` `#9be3b0` | `#2a64b8` `#b7791f` `#1c6b45` | gráficos |

- Contraste (WCAG AA) medido por script nos dois temas: texto 15,3:1 e 14,8:1, texto de apoio 8,4:1 e 6,7:1, destaque 8,9:1 e 7,4:1, vermelho 7,8:1 e 6,0:1, borda de campo 4,9:1 e 4,2:1.
- As skills `frontend-design` e `dataviz` não estavam instaladas. As séries foram conferidas por um script próprio: 3:1 ou mais sobre o fundo e diferença CIEDE2000 de 10 ou mais entre elas também com os três tipos de daltonismo simulados (Machado et al., 2009). O verde da primeira tentativa se confundia com o azul na tritanopia e foi trocado.

**Tema escuro como padrão:**
- O enum `Theme` passou a ter `DARK` como padrão (`Theme.DEFAULT`). As telas sem login usam o escuro.
- "Automático" virou `data-theme="system"`: o `tokens.css` usa o claro só quando o aparelho está no claro.
- A migração V4 muda o padrão da coluna para `DARK` e passa para `DARK` as contas em `SYSTEM`. Elas só tinham `SYSTEM` porque era o padrão da V3: a escolha de tema é do mesmo dia e o app ainda não estava no ar.