import assert from 'node:assert/strict'
import { test } from 'node:test'
import { compactBRL, linePath, nearestIndex, niceStep, ticks } from '../../main/resources/static/js/lib/chart.js'

test('passos redondos (mesma regra do HistoryChart em Java)', () => {
  assert.equal(niceStep(4000), 1000)
  assert.equal(niceStep(5200), 2000)
  assert.equal(niceStep(900), 250)
  assert.equal(niceStep(12345.67), 5000)
  assert.equal(niceStep(0), 250)
})

test('a grade cobre o máximo', () => {
  const t = ticks(5200)
  assert.equal(t.top, 6000)
  assert.deepEqual(t.values, [0, 2000, 4000, 6000])
})

test('caminho da linha e ponto mais perto', () => {
  const x = (i) => i * 10
  const y = (v) => 100 - v
  assert.equal(linePath([0, 50], x, y), 'M0.0,100.0 L10.0,50.0')
  assert.equal(nearestIndex(14, 3, x), 1)
  assert.equal(nearestIndex(16, 3, x), 2)
})

test('rótulo compacto em reais', () => {
  assert.equal(compactBRL(0), 'R$ 0')
  assert.match(compactBRL(2500), /^R\$ 2,5\s?mil$/)
})
