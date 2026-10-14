# Finanças — controle financeiro pessoal

App para registrar os gastos do mês e ver quanto sobra e quanto faz sentido investir. A sugestão de investimento é uma **estimativa educativa**, não recomendação financeira.

Versão em Java (Spring Boot + Thymeleaf), completa na branch `melhorias-java`. Ela já faz tudo o que o app React antigo fazia. O React continua em `legacy-web/` só até a importação dos dados antigos ser conferida; depois pode ser apagado.

Outros documentos:
- `docs/entrega-final.md`: o que cada fase entregou e o passo a passo para colocar o app no ar;
- `docs/plano.md`: o plano aprovado e as decisões de cada fase;
- `docs/auditoria-seguranca.md`: a auditoria de segurança;
- `docs/lighthouse-antes.md` e `docs/lighthouse-depois.md`: as medições de desempenho.

## Como rodar

Requisitos: **Java 25** (Temurin) com `JAVA_HOME` configurado. Não precisa instalar o Maven: o `mvnw` baixa a versão certa e confere o checksum dela. O Node (22 ou mais) só é preciso para os testes do JavaScript.

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev   # http://localhost:8080, banco H2 em ./data
./mvnw test                                             # testes (JUnit 5)
./mvnw verify                                           # testes + empacota o .jar em target/
./mvnw dependency-check:check                           # vulnerabilidades nas dependências (ver abaixo)
node --test "src/test/js/**/*.test.mjs"                 # testes do JavaScript, sem instalar nada
```

No PowerShell, troque `./mvnw` por `.\mvnw.cmd` e ponha entre aspas o que começa com `-D`. Sem aspas, o PowerShell quebra o argumento no primeiro ponto (`-Dspring-boot` e `.run.profiles=dev`):

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
.\mvnw.cmd verify
```

### Primeiro acesso

1. Com o app rodando, abra http://localhost:8080 e clique em **Criar conta**. A senha precisa ter pelo menos 15 caracteres.
2. O app mostra o **código de recuperação** uma vez só. Anote: é com ele que se cria uma senha nova se a antiga for esquecida.
3. Depois de entrar, a visão geral oferece **Configurar em 1 minuto**: o assistente inicial pergunta a renda, a regra de divisão (50/30/20, por exemplo), as contas fixas e a reserva de emergência. Dá para pular, ou escolher **Explorar com dados de exemplo** (6 meses fictícios) para ver as telas cheias.

Para voltar ao começo sem perder a conta, use **Ajustes → Recomeçar do zero**.

### Perfis

| Perfil | Banco | Para quê |
|---|---|---|
| `dev` | H2 em arquivo (`./data`, fora do git) | Desenvolver no próprio computador |
| `test` | H2 em memória | Usado pelos testes |
| nenhum (produção) | PostgreSQL pelas variáveis de ambiente | Servidor de verdade |

### Configuração de produção

Os segredos vêm de variáveis de ambiente, nunca do código. A lista está em `.env.example`. O app não lê o `.env` sozinho: defina as variáveis no sistema ou no serviço que roda o app.

- `DB_URL`, `DB_USER`, `DB_PASSWORD`: banco PostgreSQL. Sem elas o app não sobe, de propósito.
- `NVD_API_KEY`: só para o Dependency-Check.

Antes de pôr no ar (o passo a passo completo está em `docs/entrega-final.md`, em "O que você precisa fazer à mão"):

- **Banco criado com `ICU_LOCALE 'pt-BR'`:**
  ```sql
  CREATE DATABASE financas OWNER financas TEMPLATE template0 LOCALE_PROVIDER icu ICU_LOCALE 'pt-BR';
  ```
  Num banco com o locale "C", a busca de Lançamentos não acha "CAFÉ" quando se digita "café". Testado no PostgreSQL 18.6.
- **HTTPS obrigatório.** O cookie de sessão é `Secure` e tem o prefixo `__Host-`, então em HTTP o navegador não guarda o cookie e o login não funciona. O caminho mais simples é um proxy com certificado gratuito (Caddy, ou Nginx com Let's Encrypt).
- **App escutando só na própria máquina:** `SERVER_ADDRESS=127.0.0.1`, sem abrir a porta 8080 no firewall. Por padrão, o Spring Boot escuta em todas as interfaces, e o formulário de login poderia mandar a senha sem criptografia.
- **Atrás do proxy:** `server.forward-headers-strategy=native` e `server.tomcat.redirect-context-root=false`. Sem isso, o limite de tentativas enxerga o IP do proxy em vez do IP de quem acessa, e o cabeçalho HSTS não é enviado.
- **Subir:** `./mvnw verify` gera o jar, e `java -jar target/financas-0.0.1-SNAPSHOT.jar` roda sem perfil. Na primeira subida, o Flyway cria as tabelas.

### Trazer os dados do app antigo

1. Abra o app antigo (`legacy-web`) no mesmo navegador e no mesmo endereço em que ele era usado. Os dados dele ficam no navegador, separados por endereço: `npm run dev` abre em `localhost:5173` e `npm run preview` em `localhost:4173`.
2. Em Ajustes, use "Exportar backup criptografado".
3. No app novo, entre na conta e vá em **Ajustes → Importar um backup**. Escolha o arquivo e digite a senha do backup.
4. A importação **substitui** tudo o que a conta tem. Importe antes de lançar dados de verdade nela.

### Backup dos dados

Em **Ajustes → Exportar meus dados**, o app baixa tudo o que a conta tem num arquivo `.json` criptografado com uma senha escolhida na hora (AES-256-GCM, chave derivada da senha com PBKDF2-SHA256 de 600 mil iterações). O formato é o mesmo do backup do app antigo, então o arquivo volta pela mesma tela de importação, nesta conta, em outra ou em outro servidor. A senha e o código de recuperação da conta não vão no arquivo.

### Verificar vulnerabilidades nas dependências

O OWASP Dependency-Check precisa de uma chave gratuita da NVD. Sem ela, a primeira atualização do banco de vulnerabilidades é muito lenta. Peça em https://nvd.nist.gov/developers/request-an-api-key e defina a variável `NVD_API_KEY` antes de rodar `./mvnw dependency-check:check`. O build falha se houver vulnerabilidade com nota CVSS 7 ou mais, e o relatório fica em `target/dependency-check-report.html`.

Na prática, a chave é obrigatória. Testado em 14/10/2026:
- a versão 13.0.0 do plugin (a do `pom.xml`) não roda sem ela: manda a chave vazia, e a NVD responde "Invalid apiKey";
- a 12.2.2 começa a baixar sem chave, mas a NVD passou a responder 429 ("muitas consultas") depois de 27% dos cerca de 400 mil registros, e nada fica salvo para a próxima tentativa.

Com a chave, defina também `-DossIndexAnalyzerEnabled=false` se não tiver conta na Sonatype: o analisador OSS Index agora exige login. No PowerShell: `.\mvnw.cmd dependency-check:check "-DossIndexAnalyzerEnabled=false"`.

As versões do Tomcat e do Jackson estão fixadas no `pom.xml` (`tomcat.version` e `jackson-bom.version`) por causa de vulnerabilidades nas que vêm com o Spring Boot 4.1.1. Apague essas duas propriedades quando atualizar o Spring Boot para uma versão que traga versões iguais ou maiores.

Enquanto `legacy-web/` existir, rode também `npm audit` dentro dela.

## O que dá para fazer

- **Visão geral:** o topo responde quanto gastei, quanto sobra e quanto posso investir (estimativa educativa). Logo abaixo, o lançamento rápido: valor, categoria e salvar, com a data de hoje e as categorias mais usadas primeiro. Mais abaixo, as pendências do mês (o que falta pagar ou receber), cada uma com o botão de marcar como pago.
- **Lançamentos:** lista do mês com busca e filtros por tipo, categoria e situação (pendentes ou pagas).
- **Contas fixas e parcelas:** cada mês já começa com elas lançadas como pendentes (a pagar ou a receber). Mudar uma conta muda também os lançamentos ainda não pagos dali em diante; apagar tira os futuros e deixa o histórico.
- **Planejamento:** a regra que divide a renda em potes. Há quatro prontas (50/30/20, 70/20/10, 6 potes e "pague-se primeiro" 80/20), e os potes podem ser editados, o que vira uma regra personalizada. Cada categoria fica num pote e pode ter limite mensal. As regras prontas estão na migração `V2__seed_presets.sql`.
- **Metas:** reserva de emergência (quantos meses de custo de vida guardar e quanto falta) e metas com valor e prazo, com quanto guardar por mês para chegar lá.
- **Investimentos:** questionário de perfil (as respostas ficam no navegador), carteira sugerida e um assistente que responde dúvidas com os seus números, sem IA externa.
- **Simulador:** atualiza enquanto você digita, com gráfico e tabela ano a ano. As contas são feitas no servidor, em `BigDecimal`.
- **Histórico:** entradas × saídas por mês (gráfico e tabela) e gastos por categoria.
- **Ajustes:** nome, tema (claro, escuro ou o do aparelho), trocar a senha, gerar outro código de recuperação, exportar e importar backup, dados de exemplo, recomeçar do zero e excluir a conta.

Apagar qualquer registro (lançamento, conta fixa, meta, pote ou categoria) mostra "Desfazer" por 10 minutos.

Sem JavaScript, tudo continua funcionando: formulários vão para o servidor e apagar passa por uma página de confirmação. O JavaScript (módulos em `src/main/resources/static/js`, sem dependência nem build) só melhora a experiência.

## Desempenho

- **Cache de 1 ano** para CSS, fontes e JavaScript. O endereço muda quando o arquivo muda, então ninguém fica com versão velha:
  - CSS e fontes levam o hash do conteúdo no nome (`app-1f7c….css`);
  - o JavaScript leva a versão no caminho (`/js/v4220…/app.js`), porque os módulos se importam por caminho relativo. A versão é um resumo de todos os `.js` (`StaticResourcesConfig`).
  - `favicon.svg` e `robots.txt` também ficam 1 ano no cache, mas sem versão. Se mudar um deles, troque o nome do arquivo.
- **Compressão (gzip)** só de CSS, JavaScript e SVG. HTML e JSON ficam de fora de propósito: eles trazem o token CSRF e dados da conta ao lado de texto digitado, o cenário do ataque BREACH.
- As páginas HTML continuam com `Cache-Control: no-store`, para os dados financeiros não ficarem guardados no navegador.
- No perfil `dev`, o navegador sempre confere se o CSS ou o JS mudou, então uma edição aparece ao recarregar.
- As medições do Lighthouse, antes e depois, estão em `docs/lighthouse-depois.md`.

## Visual

Direção "livro-caixa sóbrio": papel claro, tinta escura, um verde de destaque e os valores em fonte de números impressos. Segue o tema claro ou escuro do aparelho, ou o escolhido em Ajustes (fica na conta e vale em qualquer aparelho). Cor, tipografia e espaçamento ficam em `src/main/resources/static/css/tokens.css`; o resto do CSS só usa esses nomes. O plano de design e a verificação estão em `docs/plano.md` ("Como a Fase 4 ficou").

### Fontes e ícones de terceiros

Tudo é servido pelo próprio app, sem chamar outro site:

| O quê | Onde | Licença |
|---|---|---|
| Fonte Besley (títulos e valores) | `static/fonts/besley-latin-wght.woff2` | SIL Open Font License 1.1, em `static/fonts/BESLEY-OFL.txt` |
| Fonte Atkinson Hyperlegible Next (texto) | `static/fonts/atkinson-hyperlegible-next-latin-wght.woff2` | SIL Open Font License 1.1, em `static/fonts/ATKINSON-HYPERLEGIBLE-NEXT-OFL.txt` |
| Ícones Lucide (lucide-static 1.56.0) | `templates/fragments/icons.html` | ISC, em `templates/fragments/LUCIDE-LICENSE.txt` |

As fontes vieram do Fontsource 5.3.0 (subconjunto latin, que cobre o português).

## Dependências e por que cada uma está aqui

| Dependência | Para quê |
|---|---|
| `spring-boot-starter-webmvc` | Rotas HTTP e servidor Tomcat embutido |
| `spring-boot-starter-thymeleaf` | HTML gerado no servidor, com escape automático de saída |
| `spring-boot-starter-security` | Login, hash de senha, CSRF e cabeçalhos de segurança |
| `bcprov-jdk18on` (Bouncy Castle) | O Argon2id do Spring Security calcula o hash com ele |
| `spring-boot-starter-data-jpa` | Repositórios, consultas parametrizadas e paginação |
| `spring-boot-starter-validation` | Bean Validation nas entradas |
| `spring-boot-starter-flyway` + `flyway-database-postgresql` | Migrações versionadas do banco |
| `h2` | Banco de desenvolvimento e testes |
| `postgresql` | Driver do banco de produção |
| `spring-boot-starter-webmvc-test` | JUnit 5 e MockMvc (só nos testes) |
| `spring-boot-starter-security-test` | Usuário de teste e token CSRF nos testes |
| `spring-boot-starter-data-jpa-test` | Testes de repositório e contagem de queries |
| plugin `dependency-check-maven` | Procura vulnerabilidades conhecidas nas dependências |

Versões: Spring Boot 4.1.1 (que traz Spring Security 7.1.1, Hibernate 7.4.5, Thymeleaf 3.1.5 e Flyway 12.4.0), Java 25 e Maven 3.10.0 pelo wrapper. O Tomcat (11.0.27) e o Jackson (3.1.7) estão fixados no `pom.xml` por segurança, como explicado em "Verificar vulnerabilidades nas dependências".

## Segurança

Referência: OWASP Top 10 e ASVS nível 1. Os detalhes de cada decisão estão nos commits e em `docs/auditoria-seguranca.md`.

- **Senhas:** Argon2id via `DelegatingPasswordEncoder`, com os mínimos do Password Storage Cheat Sheet (19 MiB de memória, 2 iterações, paralelismo 1). Senhas criadas antes, em BCrypt (custo 12), continuam valendo e são refeitas em Argon2id no primeiro login. Mínimo de 15 caracteres, porque o Authentication Cheat Sheet considera fraca uma senha menor sem segundo fator. Quem já tinha senha de 12 a 14 caracteres continua entrando; a regra vale ao criar ou trocar a senha. Máximo de 72 bytes, o limite do BCrypt, mantido enquanto existirem senhas antigas. A senha mais longa é recusada, nunca cortada. Não há regra de "maiúscula, número e símbolo". A senha não pode conter o nome de usuário nem estar na lista de senhas comuns (abaixo).
- **Limite de tentativas** no servidor: por usuário+IP (5), por usuário (20) e por IP (50). A espera começa em 30 s e dobra até 15 min. Vale também para a recuperação de senha, a confirmação de senha em Ajustes e a importação de backup.
- **Respostas iguais** no login e na recuperação para usuário inexistente e para senha errada, inclusive no tempo de resposta. O cadastro avisa quando o usuário já existe (decisão da Fase 1).
- **Recuperação de senha** pelo código de recuperação (160 bits), guardado só como hash (Argon2id). Cada código vale uma vez, e trocar a senha encerra as outras sessões.
- **Sessão:** cookie `__Host-id` (HttpOnly, Secure, SameSite=Lax), id novo no login, expira com 30 min sem uso e 8 h depois de entrar. Sair apaga a sessão no servidor.
- **CSRF** ligado em todo formulário. **Cabeçalhos:** CSP sem `unsafe-inline`, HSTS, `nosniff`, `Referrer-Policy: no-referrer`, `X-Frame-Options: DENY`, `Cross-Origin-Opener-Policy` e `Permissions-Policy`.
- **Cada usuário só acessa os próprios dados:** toda consulta filtra pelo dono, e um id de outra pessoa dá 404. O `AuthorizationTest` testa isso rota por rota.
- **Erros** mostram uma mensagem genérica, sem detalhes internos. **Logs** de segurança registram o usuário como um apelido (hash), nunca senha, código ou valores.
- **Dinheiro** sempre em `BigDecimal` com 2 casas e arredondamento HALF_UP (`NUMERIC(14,2)` no banco).

### Lista de senhas comuns

`src/main/resources/security/common-passwords.txt` tem as 10.000 senhas mais usadas com 15 caracteres ou mais (o mínimo do app), todas em minúsculas. Elas foram tiradas da lista `xato-net-10-million-passwords-1000000.txt` do [SecLists](https://github.com/danielmiessler/SecLists) (Daniel Miessler, **licença MIT**, cópia em `src/main/resources/security/SECLISTS-LICENSE.txt`). O ASVS 5.0 (6.2.4) pede pelo menos 3000 senhas que obedeçam à política de tamanho.

`common-password-bases.txt` junta as 1.000 senhas mais usadas da mesma lista, a lista em português `Portugese_Pwdb_common-password-list-top-150.txt` (também do SecLists) e as palavras que o app antigo bloqueava. Ela pega senhas como "flamengo2024!", que são uma palavra comum seguida de números.

## O que os testes conferem

Os testes Java usam o perfil `test` (H2 em memória) e não precisam de nada instalado além do Java. Os principais:

| Teste | O que confere |
|---|---|
| `CalculationUnitTest` e `FinanceCalculationsTest` | As contas de dinheiro (resumo do mês, regras, metas, simulador) com os mesmos casos e valores esperados do app antigo. O segundo roda contra o banco para testar também as somas feitas em SQL. |
| `AuthorizationTest` | Um usuário tenta usar o id de um registro de outro em todas as rotas com id, em GET e POST, e recebe 404. |
| `WebSecurityTest` e `SecurityRulesUnitTest` | Login, cadastro, recuperação de senha, CSRF, cabeçalhos, troca do id da sessão, política de senha e limite de tentativas. |
| `QueryCountTest` | Abre cada tela com 25 e com 75 registros. Se o número de consultas ao banco crescer, há N+1 e o teste falha. |
| `BackupImportTest` e `BackupExportTest` | Importar um backup gerado pelo app antigo, e exportar e importar de volta em outra conta. |
| `UndoTest` | "Desfazer" de cada tipo de exclusão, só uma vez e só por quem apagou. |
| `PagesRenderTest` | Abre cada tela logado e com dados, para pegar erro de template ou de consulta. |
| `src/test/js/*.test.mjs` | Leitura dos valores em R$ digitados, pontuação do questionário de perfil, respostas do assistente e a escala e a linha do gráfico. |

## Estrutura

```
src/main/java/io/github/fabasdev/financas/
  domain/       entidades JPA, Money (BigDecimal) e Months
  repository/   consultas (sempre filtrando pelo dono)
  service/      regras: resumo do mês, sugestão de investimento, simulador, contas fixas, importação...
  controller/   telas (Thymeleaf), as APIs /api/simulacao e /api/assistente e o gráfico do histórico (SVG)
  security/     política de senha, limite de tentativas, filtros de sessão
  config/       Spring Security, relógio (fuso America/Sao_Paulo) e cache do JavaScript
src/main/resources/
  db/migration/ V1 (tabelas), V2 (regras 50/30/20, 70/20/10, 6 potes e 80/20) e V3 (tema na conta)
  security/     listas de senhas comuns, com a licença
  templates/    HTML das telas (fragments/icons.html: ícones Lucide)
  static/css/   tokens.css (cor, tipografia, espaçamento) e app.css
  static/fonts/ fontes Besley e Atkinson Hyperlegible Next, com as licenças
  static/js/    melhorias em JavaScript (simulador, questionário, assistente, validação de formulário, menu)
src/test/java/  testes JUnit 5 (perfil test, H2 em memória)
src/test/js/    testes do JavaScript (node --test)
legacy-web/     app React antigo (até a importação dos dados ser conferida)
docs/           plano, entrega final, auditoria e medições do Lighthouse
```
