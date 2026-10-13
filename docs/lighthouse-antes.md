# Linha de base de desempenho (app React, antes da migração)

Medido em 13/10/2026 para comparar com a versão Java na Fase 5.

## Como foi medido

- App: `npm run build` + `npm run preview -- --port 4173`, servido em `http://localhost:4173/`.
- Ferramenta: Lighthouse 13.5.0 via `npx` (não é dependência do projeto), Chrome 155 headless.
- Preset: celular (padrão do Lighthouse), throttling simulado.
- 3 execuções. A tabela mostra a mediana (execução 2), cujo relatório completo está em `docs/lighthouse/antes-react-mobile.json`.

**Limite da medição:** sem conta criada, o app abre na tela "Criar conta". A nota vale só para essa primeira tela, não para o painel com dados. Na Fase 5 a comparação justa é com a tela de login/cadastro do app novo. Se quisermos medir o painel, as duas versões precisam ser medidas do mesmo jeito.

## Resultado (mediana)

| Categoria | Nota |
|---|---|
| Desempenho | 86 |
| Acessibilidade | 95 |
| Boas práticas | 100 |
| SEO | 91 |

| Métrica | Valor |
|---|---|
| First Contentful Paint | 2,9 s |
| Largest Contentful Paint | 3,4 s |
| Total Blocking Time | 90 ms |
| Cumulative Layout Shift | 0,001 |
| Speed Index | 2,9 s |
| Peso total transferido | 385 KiB |

Nota de desempenho nas 3 execuções: 85, 86 e 87. FCP, LCP e CLS foram iguais nas três. O TBT variou entre 90 e 150 ms.

## O que puxou as notas para baixo

- **Desempenho:** FCP e LCP altos. O JS principal (`index-*.js`, 526 kB sem compressão) precisa carregar antes de qualquer coisa aparecer, e o próprio build avisa que esse pedaço passa de 500 kB.
- **Acessibilidade:** `aria-valid-attr-value`. A aba "Criar conta" (Radix Tabs) aponta `aria-controls` para um painel que não existe no DOM naquele momento.
- **SEO:** falta `robots.txt` (o preview responde com o `index.html` no lugar).

## Outros dados do mesmo dia

- `npm test`: 3 arquivos, 51 testes, todos passando (Vitest 5.0.3).
- `npm run build`: concluído, com o aviso de chunk maior que 500 kB citado acima.
