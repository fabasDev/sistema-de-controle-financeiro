# Entrega final (14/10/2026)

O app em Java está completo na branch `melhorias-java`. A `main` continua com o app React original e não foi alterada. O repositório não tem remoto configurado, então nada foi enviado ao GitHub.

O `legacy-web/` **ainda não deve ser apagado**. O plano diz que ele fica até o app novo fazer tudo o que o antigo fazia, e faltam três coisas (ver "O que o app antigo faz e o novo ainda não").

## O que cada fase entregou

**Fase 0: preparação** (`4b3ed7a`, `4adc811`)
- O git foi iniciado com o app React na `main`, e a branch `melhorias-java` foi criada.
- Linha de base do app React no `/cadastro` (celular): desempenho 86, acessibilidade 95, 385 KiB (`docs/lighthouse-antes.md`).

**Fase 1: auditoria de segurança** (`b3e7d6f` a `1fe4836`)
- `docs/auditoria-seguranca.md`: 17 falhas no app React, nenhuma crítica ou alta, conferidas contra o OWASP Top 10:2025 e o ASVS 5.0.
- Decisão principal: as contas de dinheiro do simulador e do assistente passaram para o servidor, em `BigDecimal`.

**Fase 2: backend Java** (`45f3230` a `a2763c5`)
- Java 25 e Spring Boot 4.1.1, com controller, service e repository, Flyway e H2 no desenvolvimento.
- Segurança:
  - senhas com BCrypt;
  - sessão com cookie `__Host-`, HttpOnly, Secure e SameSite;
  - CSRF, CSP sem `unsafe-inline` e limite de tentativas de login;
  - teste do usuário A tentando usar o ID do usuário B em cada rota.
- Importação do backup criptografado do app antigo, testada com um backup gerado por ele.
- Teste que falha se o número de consultas ao banco crescer com o número de lançamentos (N+1).

**Fase 3: experiência de uso** (`a0663d8` a `5b7b2b8`)
- Lançamento rápido no topo, com a data de hoje.
- O topo mostra gastei, sobra e posso investir.
- "Desfazer" em todas as exclusões.
- Gráficos só no histórico e no simulador.
- Questionário de perfil, assistente, assistente inicial e dados de exemplo.
- Tudo funciona sem JavaScript.

**Fase 4: visual** (`3c6e228` a `5b6dee3`)
- Direção "livro-caixa sóbrio": Besley nos valores, Atkinson Hyperlegible Next no texto, ícones do Lucide e tema escuro pelos mesmos tokens.
- Acessibilidade 100 no Lighthouse nas 18 telas testadas. `/security-review` sem achados.

**Fase 5: desempenho** (`7bbb74e` a `604bdca`)
- Cache de 1 ano para CSS, JS e fontes, com a versão no endereço.
- Compressão só de CSS, JS e SVG.
- Espaço reservado para o layout não pular.
- Investimentos subiu de 79 para 99, e o CLS caiu de 0,436 para 0.
- No `/cadastro`, comparado ao app React:
  - desempenho de 86 para 100;
  - peso de 385 para 92 KiB.
- Detalhes em `docs/lighthouse-depois.md`. `/security-review` sem achados.

**Verificação:**
- Os 220 testes JUnit (`./mvnw verify`) e os 35 de JavaScript passaram no commit `604bdca`.
- Depois dele só mudaram documentos e o comentário do `.env.example`, sem mudança de código.
- O roteiro do plano passou no Chrome, no perfil `dev`: cadastrar, lançar, editar, apagar e desfazer, ver o resumo e importar o backup.

## O que o app antigo faz e o novo ainda não

| O quê | App antigo | App novo | Peso |
|---|---|---|---|
| Exportar os dados | "Exportar backup criptografado" (`legacy-web/src/pages/Settings.tsx:144`) | Não existe | **Alto** |
| Lista do que falta pagar | "Pendências do mês" na tela inicial, com caixa para marcar como pago (`Dashboard.tsx:344`) | Só o total "A pagar" (`home.html:64`). Os lançamentos não têm filtro por situação | Médio |
| Escolher o tema | Automático, claro ou escuro (`Settings.tsx:75-80`) | Segue o sistema (`layout.html:6`) | Baixo |

**Exportar os dados** é o que mais pesa. Sem isso, os dados entram no app novo e não têm como sair. A LGPD (art. 18, V) prevê o direito à portabilidade, e a página de Privacidade hoje só fala em acesso, correção e exclusão.

Os três podem ser feitos numa etapa separada, se você autorizar.

## Decisões que você pode rever

- **Questionário fechado:** em Investimentos, o questionário de perfil fica dentro de "Responder as perguntas" (Fase 5), então é um clique a mais. Aberto por padrão, a página volta a pular (CLS 0,436).
- **Senha de 12 caracteres e BCrypt** foram decisões do plano. As referências atuais pedem mais:
  - o Authentication Cheat Sheet da OWASP considera fraca uma senha com menos de 15 caracteres quando não há segundo fator (MFA);
  - o Password Storage Cheat Sheet prefere Argon2id e deixa o BCrypt para sistemas legados.
  - Subir o mínimo para 15 é mudar a constante `MIN_LENGTH` do `PasswordPolicy` e o texto de ajuda de três telas (cadastro, recuperação e Ajustes).
  - O Argon2 do Spring Security precisa de uma dependência a mais (Bouncy Castle). O `DelegatingPasswordEncoder` permite trocar sem invalidar as senhas que já existem.

## O que você precisa fazer à mão

Nesta ordem:

1. **Checar as dependências.**
   - Peça a chave gratuita da NVD em https://nvd.nist.gov/developers/request-an-api-key.
   - Defina `NVD_API_KEY` e rode `.\mvnw.cmd dependency-check:check`.
   - O build falha se houver vulnerabilidade com nota 7 ou mais. **Isso ainda não foi feito.**

2. **Criar o banco PostgreSQL.** No `psql`, como administrador:

   ```sql
   CREATE ROLE financas LOGIN;
   \password financas
   CREATE DATABASE financas OWNER financas;
   ```

   - O `\password` pede a senha sem mostrá-la e sem deixá-la no histórico do terminal.
   - O dono do banco consegue criar as tabelas no esquema `public`. A partir do PostgreSQL 15, esse esquema pertence ao dono do banco.
   - O app nunca rodou num PostgreSQL de verdade, só no H2 imitando o PostgreSQL. Faça a primeira subida num banco de teste (`financas_teste`) e rode o roteiro (cadastrar, lançar, importar).
   - Se o Flyway ou a conferência do esquema (`ddl-auto=validate`) falharem, o app nem sobe, e o erro aparece no log.

3. **Definir as variáveis** `DB_URL` (ex.: `jdbc:postgresql://localhost:5432/financas`), `DB_USER` e `DB_PASSWORD`.
   - O app lê variáveis de ambiente. Ele **não** lê o `.env` sozinho.
   - No servidor, um serviço do systemd (`EnvironmentFile=`) ou o Docker (`--env-file`) carregam um arquivo no formato do `.env.example`. Deixe esse arquivo só com leitura para o usuário do serviço.

4. **Colocar HTTPS na frente do app.** É obrigatório.
   - O cookie de sessão é `Secure` e tem o prefixo `__Host-`, então em HTTP o navegador não guarda o cookie e o login não funciona.
   - No seu computador funciona porque o Chrome trata `localhost` como seguro.
   - O caminho mais simples é um proxy com certificado gratuito (Caddy ou Nginx com Let's Encrypt).
   - Deixe o app escutando só na própria máquina, com `server.address=127.0.0.1` ou a variável `SERVER_ADDRESS=127.0.0.1`, e não abra a porta 8080 no firewall.
     - Por padrão, o Spring Boot escuta em todas as interfaces de rede.
     - Com a porta aberta em HTTP, o formulário de login mandaria a senha sem criptografia, mesmo com o cookie `Secure`.
   - Com o proxy, configure `server.forward-headers-strategy=native` e `server.tomcat.redirect-context-root=false`, como pede a documentação do Spring Boot. Sem isso, acontecem dois problemas:
     - o limite de tentativas enxerga o IP do proxy;
     - o cabeçalho HSTS não é enviado.

5. **Rodar em produção.** Gere o jar com `.\mvnw.cmd verify` e suba com `java -jar target\financas-0.0.1-SNAPSHOT.jar`, sem perfil.

6. **Trazer os dados do app antigo.**
   - Os dados dele ficam no navegador, separados por endereço. Abra o app antigo no mesmo navegador e no mesmo endereço em que você o usava. Por exemplo, `npm run dev` abre em `localhost:5173` e `npm run preview` em `localhost:4173`.
   - Em Ajustes, use "Exportar backup criptografado".
   - No app novo, vá em **Ajustes → Importar o backup do app antigo** e confira os números.
   - A importação **substitui** tudo o que já existe na conta nova. Importe antes de lançar dados de verdade nela.

7. **Apagar `legacy-web/`**, só depois de:
   - decidir sobre os três itens da seção acima;
   - conferir a importação.

   Enquanto ela existir, rode `npm audit` dentro dela de vez em quando.

8. **Git.** Juntar `melhorias-java` na `main` é decisão sua.
   - O caminho comum é criar o repositório no GitHub, enviar a branch e abrir um pull request.
   - Antes, confira com `git status` que nenhum `.env` nem a pasta `data/` aparecem. O `.gitignore` já ignora os dois.

## Pendências

- Dependency-Check não rodou (falta a `NVD_API_KEY`).
- Migrações não testadas num PostgreSQL de verdade.
- O gráfico do histórico é um SVG de largura fixa. No celular, a letra dos eixos continua pequena. A tabela logo abaixo tem os mesmos números.
- `favicon.svg` e `robots.txt` ficam 1 ano no cache sem versão no nome. Se um deles mudar, troque o nome do arquivo.
- SEO 54 nas telas medidas, de propósito: o `robots.txt` bloqueia buscadores. Também falta `meta description`.
- Testado só no Chrome e em `localhost`. Safari e Firefox não foram testados.
