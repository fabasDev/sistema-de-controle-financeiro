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

**Dependências** (uma linha de motivo para cada, também no README):
- `spring-boot-starter-web`: rotas HTTP.
- `-thymeleaf`: HTML no servidor, com escape automático.
- `-security`: login, BCrypt, CSRF e cabeçalhos.
- `thymeleaf-extras-springsecurity`: token CSRF e usuário logado nos templates.
- `-data-jpa`: repositórios e paginação.
- `-validation`: Bean Validation.
- `flyway-core` + `flyway-database-postgresql`: migrações.
- `h2` (só dev/test) e `postgresql` (runtime).
- `spring-boot-starter-test`: JUnit 5 e MockMvc.
- Plugin `dependency-check-maven`.

Todos os nomes e versões serão conferidos na doc do Boot 4.1 antes de entrar no `pom.xml`. O limite de login será código próprio, sem biblioteca extra.

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

## Fase 3: experiência de uso
- Novo gasto: formulário curto no topo do mês, com data de hoje, categoria e valor com teclado numérico (`inputmode="decimal"`).
- O topo da tela mostra três números: gastei, sobra, posso investir. Junto vem um aviso fixo de que é uma "estimativa educativa, não recomendação".
- Validação no servidor e no formulário, mensagens ao lado do campo, e estados vazio/carregando/erro com instrução do que fazer.
- Apagar tem "Desfazer" por alguns segundos (exclusão adiada no servidor). Sem JS, cai numa confirmação.
- Botões com texto. Os que têm só ícone ganham `aria-label` e um tooltip no hover e no foco, ligado por `aria-describedby`.
- Alvos de toque de 44px, foco visível, labels, uso completo pelo teclado e contraste AA, conferidos com a skill de auditoria de acessibilidade.
- Gráficos só em duas telas:
  - Histórico: entradas × saídas por mês;
  - Simulador: crescimento ao longo do tempo.
- Gráficos em SVG gerado no servidor ou no JS puro. Não entra biblioteca nova sem eu perguntar.

## Fase 4: visual
- Carregar a skill `frontend-design` e consultar o repo anthropics/skills.
- Direção em duas linhas para você aprovar antes de implementar: algo na linha de um livro-caixa sóbrio (papel claro, tinta escura, um verde de destaque), com números grandes em fonte tabular.
- `static/css/tokens.css` com cor, tipografia e espaçamento, e um tema escuro pelos mesmos tokens.
- Fonte de títulos com personalidade + fonte de números com `tabular-nums`, hospedadas localmente. Ícones só do Lucide (SVG estático, mesma biblioteca que já usamos).
- Registrar a direção escolhida no CLAUDE.md.

## Fase 5: desempenho da renderização
- Lighthouse no app novo, comparado com a linha de base da Fase 0. Relato só valores medidos.
- Scripts com `defer`, CSS mínimo e cache dos assets estáticos (`spring.web.resources.cache`).
- Fontes com `font-display: swap` e preload só da fonte da primeira tela.
- Esqueletos de carregamento com espaço reservado.
- Atualização pontual do DOM com `DocumentFragment`, `debounce` na busca e `throttle` em scroll/resize.
- Animações só com `transform`/`opacity` e respeito a `prefers-reduced-motion`.

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
