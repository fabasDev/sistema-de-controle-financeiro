/**
 * Geometria de gráficos de linha em SVG (só desenho; os valores já vêm calculados do servidor).
 * Funções puras, testadas com node --test.
 */

/** Passo "redondo" (1, 2, 2,5 ou 5 × 10ⁿ) para umas 4 linhas de grade. */
export function niceStep(max) {
  if (!(max > 0)) return 250
  const raw = max / 4
  const magnitude = 10 ** Math.floor(Math.log10(raw))
  const normalized = raw / magnitude
  for (const m of [1, 2, 2.5, 5, 10]) {
    if (normalized <= m + 1e-9) return m * magnitude
  }
  return 10 * magnitude
}

/** Linhas da grade: de 0 até o primeiro múltiplo do passo que cobre o máximo. */
export function ticks(max) {
  const step = niceStep(max)
  const top = max > 0 ? Math.ceil(max / step - 1e-9) * step : step * 4
  const out = []
  for (let v = 0; v <= top + 1e-9; v += step) out.push(v)
  return { step, top, values: out }
}

/** "M x,y L x,y ..." com coordenadas arredondadas a 0,1 px. */
export function linePath(values, x, y) {
  return values
    .map((v, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${y(v).toFixed(1)}`)
    .join(' ')
}

/** Índice do ponto mais perto da posição horizontal (para o tooltip). */
export function nearestIndex(px, count, x) {
  let best = 0
  let bestDistance = Infinity
  for (let i = 0; i < count; i++) {
    const d = Math.abs(x(i) - px)
    if (d < bestDistance) {
      best = i
      bestDistance = d
    }
  }
  return best
}

const compact = new Intl.NumberFormat('pt-BR', { notation: 'compact', maximumFractionDigits: 1 })

/** "R$ 0", "R$ 2,5 mil", "R$ 1 mi": rótulos dos eixos. */
export function compactBRL(value) {
  return `R$ ${compact.format(value)}`
}
