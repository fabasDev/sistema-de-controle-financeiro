# Finanças — controle financeiro pessoal

App web para organizar o dinheiro do mês: quanto entra, quanto sai, quanto guardar e quanto investir. Os dados ficam **criptografados e somente no seu computador**.

## Como rodar

Requisitos: Node.js 20+. Rode os comandos dentro da pasta `legacy-web/`.

```bash
npm install
npm run dev          # http://localhost:5173 (desenvolvimento)
npm run build        # gera a versão otimizada em dist/
npm run preview      # serve o build em http://localhost:4173 (com headers de segurança)
npm test             # testes de cálculos e criptografia
```

> Abra sempre por `http://localhost` (ou HTTPS). A criptografia do navegador não funciona por IP de rede local.

## Funcionalidades

| Área | O que faz |
|---|---|
| **Login local** | Várias contas no mesmo navegador, senha forte obrigatória, código de recuperação, bloqueio por inatividade |
| **Visão geral** | Saldo livre do mês, receitas, gastos, valor guardado, taxa de poupança, planejado × realizado, gastos por categoria, pendências |
| **Lançamentos** | Receitas e despesas por mês, busca e filtros, marcar como pago, desfazer exclusão |
| **Planejamento** | Regras prontas (50/30/20, 70/20/10, 6 potes, 80/20) ou personalizada; potes com nome, tipo e % editáveis; categorias com ícone e limite mensal |
| **Contas fixas** | Receitas/despesas recorrentes e **parcelamentos**, lançados automaticamente todo mês |
| **Metas** | Reserva de emergência calculada pelo seu custo de vida (3–12 meses), prioridade reserva × investimentos, metas com prazo e valor mensal necessário |
| **Investimentos** | Questionário de perfil (conservador/moderado/arrojado), carteira sugerida por classe de ativo (personalizável), plano do mês em R$ e **assistente offline** que responde dúvidas com os seus números |
| **Simulador** | Juros compostos com aporte mensal, inflação, aumento anual do aporte, meta de patrimônio, gráfico e tabela |
| **Histórico** | 6/12/24 meses: entradas × saídas, taxa de poupança, gastos por categoria por mês |
| **Ajustes** | Tema claro/escuro/automático, troca de senha, novo código de recuperação, backup criptografado, dados de exemplo |
| **Privacidade** | Política de privacidade (LGPD) em `/privacidade`, acessível sem login |
| **404** | Página personalizada para endereços inexistentes, dentro e fora do app |

### Como a recomendação funciona

1. A regra escolhida define quanto da renda vai para cada pote (ex.: 20% para "Futuro").
2. Enquanto a reserva de emergência não está completa, uma parte do "Futuro" vai para ela (padrão 70%, ajustável em **Metas**).
3. A meta da reserva = média dos gastos essenciais dos últimos 3 meses × meses desejados.
4. Com a reserva completa, todo o "Futuro" é sugerido para investimentos.

As sugestões são educativas e não constituem recomendação de investimento.

### Assistente de investimentos

Funciona **sem internet e sem modelo de IA externo**: identifica o assunto da pergunta (perfil, quanto/onde investir, reserva, Tesouro, CDB/LCI/LCA, FIIs, ações, exterior, poupança, cripto, imposto, dívidas, metas, gastos) e monta a resposta com os seus dados. Nenhuma pergunta sai do aparelho e a conversa não é salva. Regras e textos ficam em `src/lib/advisor.ts`.

## Segurança

**O que é protegido e como:**

- **Criptografia AES-256-GCM** de todos os dados financeiros, com a Web Crypto API nativa do navegador (sem bibliotecas de terceiros).
- **Envelope encryption:** uma chave de dados aleatória criptografa tudo; ela é "embrulhada" pela sua senha (PBKDF2-SHA256, 600.000 iterações, sal aleatório) e, separadamente, pelo código de recuperação (160 bits aleatórios).
- **A senha nunca é armazenada.** Senha errada simplesmente não consegue abrir a chave (falha de autenticação do GCM).
- **Nome de usuário salvo só como hash.** Mensagem de erro igual para usuário inexistente e senha errada (mesmo custo de tempo).
- **Limite de tentativas:** após 5 erros, espera crescente (30 s → até 15 min).
- **Dados amarrados à conta** (AAD): blocos criptografados não podem ser trocados entre contas sem detecção; qualquer alteração no arquivo é detectada.
- **Chave só na memória:** some ao sair, ao fechar a aba ou após inatividade (padrão 15 min).
- **Nada sai do computador:** sem servidor, sem analytics, fontes locais. O servidor de desenvolvimento só escuta em `localhost`.
- **Content Security Policy** no build (só scripts do próprio app), `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy: no-referrer`.
- **Backups exportados também são criptografados** com senha; arquivos importados são validados campo a campo.
- Dependências de produção sem vulnerabilidades conhecidas (`npm audit --omit=dev`).

**Limites (importante saber):**

- Protege contra quem acessa os arquivos do navegador ou do computador **com o app bloqueado**. Não protege contra vírus/keyloggers no computador ou extensões maliciosas do navegador **enquanto o app está desbloqueado**.
- Esqueceu a senha **e** perdeu o código de recuperação → os dados não podem ser recuperados (é o preço de ninguém mais conseguir abri-los). Faça backups.
- Limpar os dados do navegador apaga as contas deste app. Use **Ajustes → Exportar backup**.

## Estrutura

```
src/
  lib/          cálculos (finance), criptografia (crypto, vault), regras (presets), formatação
  store/        estado (store), sessão e salvamento criptografado (session), backup, dados de exemplo
  components/   layout, login, onboarding, diálogos e componentes compartilhados (ui/ = shadcn)
  pages/        Visão geral, Lançamentos, Planejamento, Contas fixas, Metas, Simulador, Histórico, Ajustes
```

Stack: React 19, TypeScript, Vite, Tailwind CSS v4, shadcn/ui (Radix), Recharts, Zustand, Zod, Vitest.
