# App de controle financeiro

App pessoal em que o usuário registra os gastos do mês e vê quanto sobra e quanto faz sentido investir com base nisso. A sugestão de investimento é uma estimativa educativa, nunca recomendação financeira, e a interface precisa deixar isso claro.

## Sobre quem programa

Fabrício, estudante do ensino médio, aprendendo programação e mirando carreira de desenvolvedor Back-End. Explique decisões importantes em poucas linhas e comente o código só onde isso ajuda a entender. Se eu pedir algo que está errado ou frágil, avise antes de fazer.

## Stack

- Backend: Java (LTS atual) com Spring Boot e Maven. Confirme as versões na documentação oficial em vez de usar de memória.
- Camadas: controller, service, repository.
- Banco: H2 em desenvolvimento, PostgreSQL em produção, migrações com Flyway.
- Interface: HTML, CSS e JavaScript (o navegador não roda Java). Thymeleaf ou front separado consumindo API REST, conforme a decisão registrada abaixo.
- Testes: JUnit 5.

Decisão sobre a interface: Thymeleaf (HTML renderizado no servidor) com JavaScript puro só onde precisa. O questionário de perfil roda em JS no navegador. O simulador e o assistente desenham o gráfico e montam o texto em JS, mas as contas de dinheiro (projeção, meses até a meta, aporte, divisão da carteira) são feitas no servidor, em `BigDecimal`. Os dados ficam no servidor (PostgreSQL), não mais só no aparelho. O React antigo fica em `legacy-web/` até a nova versão ter as mesmas funcionalidades. Plano aprovado em `docs/plano.md`.

## Comandos

Pelo Maven Wrapper, sem instalar o Maven. No PowerShell, use `.\mvnw.cmd` e ponha o `-D...` entre aspas.

- Rodar: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` (H2 em `./data`). Sem o perfil `dev`, o app exige `DB_URL`, `DB_USER` e `DB_PASSWORD`.
- Testar: `./mvnw test` e `node --test "src/test/js/**/*.test.mjs"`
- Build: `./mvnw verify` (testes + jar em `target/`)
- Checar vulnerabilidades nas dependências: `./mvnw dependency-check:check`, com `NVD_API_KEY` definida

Rode build e testes ao fim de cada tarefa e só diga que funciona se tiver rodado.

## Git

- Trabalhe na branch `melhorias-java`. Nunca altere a `main`.
- Um commit por mudança coerente, com mensagem que explique o motivo.
- Nunca commite segredos, `.env` ou arquivos de banco local.

## Regras de código

- Dinheiro sempre em `BigDecimal` ou em centavos (`long`). Nunca `double` ou `float`.
- Exibição em R$ com locale pt-BR, datas em dd/mm/aaaa.
- Regra de sugestão de investimento (por exemplo 50/30/20) fica configurável e coberta por testes, não escondida no meio de uma tela.
- Não adicione dependência sem explicar em uma linha por que ela é necessária.
- Não invente número, versão ou resultado de teste. Se não deu para verificar, diga.

## Segurança

Referência: OWASP Top 10 e OWASP ASVS nível 1.

- Segredos em variáveis de ambiente, nunca no código.
- Senhas com BCrypt ou Argon2 via Spring Security. Cookies de sessão HttpOnly, Secure e SameSite.
- Validação de entrada no servidor com Bean Validation, consultas parametrizadas e escape de saída.
- Cada usuário só acessa os próprios lançamentos. Toda rota confere o dono do registro, e há teste para o caso de um usuário tentar usar o ID de outro.
- Proteção CSRF, limite de tentativas de login, cabeçalhos de segurança (CSP, X-Content-Type-Options, Referrer-Policy, HSTS).
- Erros para o usuário não revelam detalhes internos. Logs não guardam dados sensíveis.
- Guarde só os dados financeiros necessários.
- Antes de encerrar uma fase, rode `/security-review`.

## Experiência de uso

- Registrar um gasto em poucos toques, com a data já preenchida com hoje.
- O topo da tela responde: quanto gastei, quanto sobra, quanto posso investir.
- Estados vazio, carregando e erro com mensagem que diga o que fazer.
- Apagar pede confirmação ou permite desfazer.
- Celular primeiro. Acessibilidade WCAG AA: contraste, foco visível, labels nos campos, uso completo pelo teclado, alvos de toque de pelo menos 44px.
- Gráfico só onde responder uma pergunta real do usuário.

## Visual

Use a skill `frontend-design`. O visual não pode parecer gerado por IA.

Evitar: degradê roxo ou azul, grade de cards iguais com sombra suave e ícone em círculo, emoji no lugar de ícone, glassmorphism, tudo centralizado e simétrico, Inter em tudo, textos de efeito sem conteúdo concreto.

Fazer:
- Direção visual sóbria e legível, com os números como protagonistas, descrita em duas linhas antes de implementar.
- Tokens de cor, tipografia e espaçamento em um único arquivo. Paleta curta com uma cor de destaque.
- Fonte com personalidade nos títulos e `font-variant-numeric: tabular-nums` nos valores.
- Ícones de uma biblioteca só.
- Hierarquia por tamanho, peso e espaço, não por caixas.
- Textos curtos, escritos como uma pessoa falaria.

Direção visual escolhida (Fase 4): livro-caixa sóbrio, com papel claro levemente esverdeado, tinta azul-escura, um verde de destaque e vermelho só para valor negativo. Os números são o protagonista: todo valor em Besley com algarismos tabulares, e o saldo leva o traço duplo da contabilidade. Texto em Atkinson Hyperlegible Next, ícones do Lucide sempre ao lado de texto. Tokens em `src/main/resources/static/css/tokens.css`; detalhes em `docs/plano.md` ("Como a Fase 4 ficou").

## Materiais de consulta

Consulte de fato antes de implementar, em vez de responder de memória.

- github.com/anthropics/skills (skill `frontend-design`)
- github.com/OWASP/CheatSheetSeries (Authentication, Session Management, Password Storage, XSS, CSRF)
- github.com/OWASP/ASVS
- github.com/spring-projects/spring-security e a documentação do Spring Boot
- github.com/dependency-check/DependencyCheck

## Idioma

Converse comigo e escreva a interface, as mensagens de commit e o README em português do Brasil. Nomes de classes, métodos e variáveis em inglês.

## Quando faltar informação

Pergunte antes de decidir. Não apague dados do usuário nem arquivos sem me avisar.
