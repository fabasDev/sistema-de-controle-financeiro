# Finanças — controle financeiro pessoal

App para registrar os gastos do mês e ver quanto sobra e quanto faz sentido investir. A sugestão de investimento é uma **estimativa educativa**, não recomendação financeira.

Versão em Java (Spring Boot + Thymeleaf), em migração na branch `melhorias-java`. O app React antigo continua em `legacy-web/` até a versão nova ter tudo o que ele tinha. O plano está em `docs/plano.md` e a auditoria de segurança em `docs/auditoria-seguranca.md`.

## Como rodar

Requisitos: **Java 25** (Temurin) com `JAVA_HOME` configurado. Não precisa instalar o Maven: o `mvnw` baixa a versão certa e confere o checksum dela.

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev   # http://localhost:8080, banco H2 em ./data
./mvnw test                                             # testes (JUnit 5)
./mvnw verify                                           # testes + empacota o .jar em target/
./mvnw dependency-check:check                           # vulnerabilidades nas dependências (ver abaixo)
```

No Windows, use `mvnw.cmd` no lugar de `./mvnw` se não estiver no Git Bash.

### Perfis

| Perfil | Banco | Para quê |
|---|---|---|
| `dev` | H2 em arquivo (`./data`, fora do git) | Desenvolver no próprio computador |
| `test` | H2 em memória | Usado pelos testes |
| nenhum (produção) | PostgreSQL pelas variáveis de ambiente | Servidor de verdade |

### Configuração de produção

Os segredos vêm de variáveis de ambiente, nunca do código. A lista está em `.env.example`:

- `DB_URL`, `DB_USER`, `DB_PASSWORD`: banco PostgreSQL. Sem elas o app não sobe, de propósito.
- `NVD_API_KEY`: só para o Dependency-Check.

O cookie de sessão é `Secure`, então em produção o app precisa estar atrás de HTTPS. Se houver um proxy (Nginx, por exemplo), configure `server.forward-headers-strategy`. Sem isso, o limite de tentativas enxerga o IP do proxy em vez do IP de quem acessa.

### Trazer os dados do app antigo

1. No app antigo (`legacy-web`), em Ajustes, use "Exportar backup criptografado".
2. No app novo, entre na sua conta e vá em **Ajustes → Importar o backup do app antigo**.
3. Escolha o arquivo e digite a senha do backup. Os dados atuais da conta são substituídos.

### Verificar vulnerabilidades nas dependências

O OWASP Dependency-Check precisa de uma chave gratuita da NVD. Sem ela, a primeira atualização do banco de vulnerabilidades é muito lenta. Peça em https://nvd.nist.gov/developers/request-an-api-key e defina a variável `NVD_API_KEY` antes de rodar `./mvnw dependency-check:check`. O build falha se houver vulnerabilidade com nota CVSS 7 ou mais, e o relatório fica em `target/dependency-check-report.html`.

Enquanto `legacy-web/` existir, rode também `npm audit` dentro dela.

## Dependências e por que cada uma está aqui

| Dependência | Para quê |
|---|---|
| `spring-boot-starter-webmvc` | Rotas HTTP e servidor Tomcat embutido |
| `spring-boot-starter-thymeleaf` | HTML gerado no servidor, com escape automático de saída |
| `spring-boot-starter-security` | Login, BCrypt, CSRF e cabeçalhos de segurança |
| `spring-boot-starter-data-jpa` | Repositórios, consultas parametrizadas e paginação |
| `spring-boot-starter-validation` | Bean Validation nas entradas |
| `spring-boot-starter-flyway` + `flyway-database-postgresql` | Migrações versionadas do banco |
| `h2` | Banco de desenvolvimento e testes |
| `postgresql` | Driver do banco de produção |
| `spring-boot-starter-webmvc-test` | JUnit 5 e MockMvc (só nos testes) |
| `spring-boot-starter-security-test` | Usuário de teste e token CSRF nos testes |
| `spring-boot-starter-data-jpa-test` | Testes de repositório e contagem de queries |
| plugin `dependency-check-maven` | Procura vulnerabilidades conhecidas nas dependências |

Versões: Spring Boot 4.1.1 (que traz Spring Security 7.1.1, Hibernate 7.4.5, Thymeleaf 3.1.5 e Flyway 12.4.0), Java 25 e Maven 3.10.0 pelo wrapper.

## Segurança

Referência: OWASP Top 10 e ASVS nível 1. Os detalhes de cada decisão estão nos commits e em `docs/auditoria-seguranca.md`.

- **Senhas:** BCrypt (custo 12) via `DelegatingPasswordEncoder`. Mínimo de 12 caracteres e máximo de 72 bytes, o limite do BCrypt. A senha mais longa é recusada, nunca cortada. Não há regra de "maiúscula, número e símbolo". A senha não pode conter o nome de usuário nem estar na lista de senhas comuns (abaixo).
- **Limite de tentativas** no servidor: por usuário+IP (5), por usuário (20) e por IP (50). A espera começa em 30 s e dobra até 15 min. Vale também para a recuperação de senha, a confirmação de senha em Ajustes e a importação de backup.
- **Respostas iguais** no login e na recuperação para usuário inexistente e para senha errada, inclusive no tempo de resposta. O cadastro avisa quando o usuário já existe (decisão da Fase 1).
- **Recuperação de senha** pelo código de recuperação (160 bits), guardado só como hash BCrypt. Cada código vale uma vez, e trocar a senha encerra as outras sessões.
- **Sessão:** cookie `__Host-id` (HttpOnly, Secure, SameSite=Lax), id novo no login, expira com 30 min sem uso e 8 h depois de entrar. Sair apaga a sessão no servidor.
- **CSRF** ligado em todo formulário. **Cabeçalhos:** CSP sem `unsafe-inline`, HSTS, `nosniff`, `Referrer-Policy: no-referrer`, `X-Frame-Options: DENY`, `Cross-Origin-Opener-Policy` e `Permissions-Policy`.
- **Cada usuário só acessa os próprios dados:** toda consulta filtra pelo dono, e um id de outra pessoa dá 404. O `AuthorizationTest` testa isso rota por rota.
- **Erros** mostram uma mensagem genérica, sem detalhes internos. **Logs** de segurança registram o usuário como um apelido (hash), nunca senha, código ou valores.
- **Dinheiro** sempre em `BigDecimal` com 2 casas e arredondamento HALF_UP (`NUMERIC(14,2)` no banco).

### Lista de senhas comuns

`src/main/resources/security/common-passwords.txt` tem as 10.000 senhas mais usadas com 12 caracteres ou mais. Elas foram tiradas da lista `xato-net-10-million-passwords-1000000.txt` do [SecLists](https://github.com/danielmiessler/SecLists) (Daniel Miessler, **licença MIT**, cópia em `src/main/resources/security/SECLISTS-LICENSE.txt`). O ASVS 5.0 (6.2.4) pede pelo menos 3000 senhas que obedeçam à política de tamanho.

`common-password-bases.txt` junta as 1.000 senhas mais usadas da mesma lista, a lista em português `Portugese_Pwdb_common-password-list-top-150.txt` (também do SecLists) e as palavras que o app antigo bloqueava. Ela pega senhas como "flamengo2024!", que são uma palavra comum seguida de números.

## Estrutura

```
src/main/java/io/github/fabasdev/financas/
  domain/       entidades JPA, Money (BigDecimal) e Months
  repository/   consultas (sempre filtrando pelo dono)
  service/      regras: resumo do mês, sugestão de investimento, simulador, contas fixas, importação...
  controller/   telas (Thymeleaf) e as APIs /api/simulacao e /api/assistente
  security/     política de senha, limite de tentativas, filtros de sessão
  config/       Spring Security e relógio (fuso America/Sao_Paulo)
src/main/resources/
  db/migration/ V1 (tabelas) e V2 (regras 50/30/20, 70/20/10, 6 potes e 80/20)
  templates/    HTML das telas
legacy-web/     app React antigo (até a migração terminar)
docs/           plano, auditoria e medições
```
