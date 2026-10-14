// Testes da lógica pura em JS. Rodar: node --test src/test/js/
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { formatDuration, isPositiveAmount, parseBRL } from '../../main/resources/static/js/lib/money.js'

// Mesmos casos de parseBRL em legacy-web/src/lib/finance.test.ts. O resultado agora é texto decimal.
const cases = [
  ['1.234,56', '1234.56'],
  ['1234,5', '1234.5'],
  ['R$ 2.500', '2500'],
  ['12.5', '12.5'],
  ['1.000.000', '1000000'],
  ['', null],
  ['abc', null],
  ['-', null],
  ['1,2,3', null],
]

for (const [input, expected] of cases) {
  test(`parseBRL(${JSON.stringify(input)}) → ${expected}`, () => {
    assert.equal(parseBRL(input), expected)
  })
}

test('valor positivo', () => {
  assert.equal(isPositiveAmount('0,01'), true)
  assert.equal(isPositiveAmount('0,00'), false)
  assert.equal(isPositiveAmount('-5'), false)
  assert.equal(isPositiveAmount('abc'), false)
})

test('formata durações como o app antigo', () => {
  assert.equal(formatDuration(27), '2 anos e 3 meses')
  assert.equal(formatDuration(12), '1 ano')
  assert.equal(formatDuration(1), '1 mês')
})
