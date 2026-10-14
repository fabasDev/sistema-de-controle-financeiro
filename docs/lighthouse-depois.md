# Desempenho depois da Fase 5 (app Java)

Medido em 14/10/2026, para comparar com `docs/lighthouse-antes.md`.

## Como foi medido

- Do mesmo jeito da linha de base: Lighthouse 13.5.0 via `npx`, Chrome 155 headless, preset celular, throttling simulado, 3 execuções por tela. As tabelas mostram a execução mediana pela nota de desempenho.
- App: o `.jar` empacotado, com a configuração de produção (cache do Thymeleaf ligado, sem log de SQL), H2 em memória e uma conta de teste com os dados de exemplo. As telas logadas foram medidas com o cookie dessa conta, que foi tirado dos relatórios salvos.
- **Antes** é o commit `5b6dee3` (fim da Fase 4). **Depois** é o fim desta fase.
- Tudo rodou em `localhost`. O Lighthouse simula a rede de celular, mas não há latência real.
- O Lighthouse sempre começa com o cache vazio. Por isso o cache de 1 ano não aparece nas notas; ele foi conferido pelos cabeçalhos e pelo `StaticResourcesTest`.

Relatórios completos (execução mediana) em `docs/lighthouse/`: `depois-java-cadastro-mobile.json`, `antes-java-investimentos-mobile.json` e `depois-java-investimentos-mobile.json`.

## Comparação com o app React (só a tela de cadastro)

A linha de base do React só mediu a primeira tela, "Criar conta". A tela equivalente aqui é `/cadastro`.

| | React (Fase 0) | Java depois da Fase 5 |
|---|---|---|
| Desempenho | 86 | 100 |
| Acessibilidade | 95 | 100 |
| Boas práticas | 100 | 100 |
| SEO | 91 | 54 |
| First Contentful Paint | 2,9 s | 1,4 s |
| Largest Contentful Paint | 3,4 s | 1,5 s |
| Total Blocking Time | 90 ms | 0 ms |
| Cumulative Layout Shift | 0,001 | 0 |
| Speed Index | 2,9 s | 1,4 s |
| Peso total transferido | 385 KiB | 92 KiB |

- **A maior parte do ganho veio da Fase 2**, não desta: o HTML passou a vir pronto do servidor, em vez de esperar 526 kB de JavaScript. Antes desta fase, o `/cadastro` do Java já tinha nota 99 e FCP de 1,6 s.
- **SEO 54 é de propósito.** O `robots.txt` só libera `/login` e `/privacidade` para buscadores, então o Lighthouse marca o `/cadastro` como "bloqueado para indexação". Também falta a `meta description`. O `/login` tem nota 91, porque só falta a descrição. No React não havia `robots.txt`.

## Java antes × depois da Fase 5

| Tela | Desempenho | FCP | LCP | CLS | Peso |
|---|---|---|---|---|---|
| Cadastro | 99 → 100 | 1,6 → 1,4 s | 1,7 → 1,5 s | 0 → 0 | 112 → 92 KiB |
| Login | 99 → 100 | 1,6 → 1,4 s | 1,7 → 1,5 s | 0 → 0 | 111 → 92 KiB |
| Visão geral | 100 → 100 | 1,5 → 1,4 s | 1,5 → 1,5 s | 0,007 → 0 | 122 → 102 KiB |
| Lançamentos | 98 → 99 | 1,8 → 1,7 s | 2,0 → 1,8 s | 0,002 → 0 | 155 → 136 KiB |
| Simulador | 99 → 99 | 1,6 → 1,4 s | 1,7 → 1,7 s | 0,002 → 0 | 146 → 120 KiB |
| Investimentos | 79 → 99 | 1,7 → 1,4 s | 1,8 → 1,7 s | 0,436 → 0 | 152 → 114 KiB |
| Histórico | 99 → 99 | 1,7 → 1,6 s | 1,8 → 1,7 s | 0,009 → 0,002 | 126 → 106 KiB |

- Total Blocking Time ficou em 0 ms em todas as telas, antes e depois.
- Acessibilidade e boas práticas ficaram em 100 em todas as telas, antes e depois.
- Nota de desempenho de Investimentos nas 3 execuções: antes 79, 79 e 89; depois 99, 99 e 99. Nas outras telas, as 3 execuções variaram no máximo 1 ponto.

## De onde veio cada ganho

- **Investimentos (CLS de 0,436 para 0):** o JavaScript montava as 8 perguntas do questionário no meio da página logo depois de ela aparecer, e o resto da página era empurrado para baixo. Agora as perguntas são montadas dentro de "Responder as perguntas" (`<details>` fechado), que não ocupa espaço até a pessoa abrir.
- **Simulador na tela larga:** o Lighthouse só mede o celular, onde o gráfico fica abaixo da dobra. Medi com um script próprio (`PerformanceObserver` no Chrome, janela de 1350 × 940): a soma dos deslocamentos caiu de 0,101 para 0,001. O gráfico aparecia depois e empurrava a tabela. Agora o espaço dele já vem reservado, com a pauta vazia até o desenho chegar.
- **Peso (uns 20 KiB a menos por tela, às vezes mais):** compressão gzip do CSS e do JavaScript. O `app.css` passou de 23,6 KB para 7,1 KB transferidos.
- **FCP e o CLS que sobrava:** o preload da fonte Besley. Os títulos "pulavam" alguns pixels quando ela trocava a fonte provisória. Medi com e sem o preload, nas 7 telas:

| Tela | FCP sem → com | CLS sem → com |
|---|---|---|
| Cadastro | 1,5 → 1,4 s | 0 → 0 |
| Login | 1,5 → 1,4 s | 0 → 0 |
| Visão geral | 1,5 → 1,4 s | 0,007 → 0 |
| Lançamentos | 1,8 → 1,7 s | 0,003 → 0 |
| Simulador | 1,6 → 1,4 s | 0,002 → 0 |
| Investimentos | 1,6 → 1,4 s | 0,005 → 0 |
| Histórico | 1,7 → 1,6 s | 0,009 → 0,002 |

O log de rede confirma que a fonte é baixada uma vez só.

## O que o Lighthouse ainda aponta

- **"Document request latency":** o HTML não é comprimido, de propósito. A página traz o token CSRF ao lado de texto digitado (a busca), que é o cenário do ataque BREACH. No `/cadastro`, a economia seria de cerca de 2 KiB.
- **"Render-blocking requests":** os dois arquivos de CSS (`tokens.css` e `app.css`) bloqueiam a primeira pintura, como todo CSS. Com a compressão, eles somam uns 10 KB.
