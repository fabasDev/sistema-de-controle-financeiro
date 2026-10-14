/**
 * Simulador ao vivo: ao mudar um campo, espera 400 ms (debounce), pede a conta ao servidor
 * (/api/simulacao, em BigDecimal) e redesenha números, tabela e gráfico. Um pedido novo cancela o
 * anterior (AbortController), para uma resposta lenta não sobrescrever um resultado mais novo.
 * Sem JavaScript, a página funciona pelo botão "Simular".
 */
import { compactBRL, linePath, nearestIndex, ticks } from './lib/chart.js'
import { getJson } from './lib/http.js'
import { formatBRL, formatDuration, parseBRL } from './lib/money.js'

const form = document.getElementById('simulador')
const section = document.getElementById('resultado')

/** Campo do formulário (nome em português) → parâmetro da API. */
const FIELDS = {
  inicial: { api: 'initial', empty: '0', max: 1e12 },
  mensal: { api: 'monthly', empty: '0', max: 1e12 },
  taxa: { api: 'annualRate', empty: '0', max: 100 },
  anos: { api: 'years', empty: null, integer: true, min: 1, max: 50 },
  inflacao: { api: 'inflation', empty: '0', max: 100 },
  aumento: { api: 'yearlyIncrease', empty: '0', max: 100 },
  meta: { api: 'target', empty: '0', max: 1e12 },
}

const SVG = 'http://www.w3.org/2000/svg'
const VIEW = { width: 640, height: 260, left: 64, right: 96, top: 12, bottom: 28 }
const SERIES = [
  { key: 'total', label: 'Total', cls: 'line-total' },
  { key: 'invested', label: 'Investido', cls: 'line-invested' },
  { key: 'real', label: 'Em valores de hoje', cls: 'line-real' },
]

if (form && section) init()

function init() {
  const state = document.getElementById('resultado-estado')
  const figure = document.getElementById('sim-grafico')
  const svg = figure.querySelector('svg')
  const tooltip = figure.querySelector('.chart-tooltip')
  let timer
  let controller
  let points = []

  form.addEventListener('input', () => {
    clearTimeout(timer)
    timer = setTimeout(run, 400)
  })
  form.addEventListener('submit', (event) => {
    event.preventDefault()
    clearTimeout(timer)
    run()
  })
  setupHover()
  run()

  /** Lê os campos. Devolve null (e marca o campo) se algum valor não for um número válido. */
  function collect() {
    const params = {}
    let ok = true
    for (const [name, spec] of Object.entries(FIELDS)) {
      const input = form.elements.namedItem(name)
      const raw = input.value.trim()
      let value = raw === '' ? spec.empty : spec.integer ? (/^\d+$/.test(raw) ? raw : null) : parseBRL(raw)
      const number = value === null ? NaN : Number(value)
      const valid = value !== null && !value.startsWith('-') && number <= spec.max && (!spec.min || number >= spec.min)
      input.setCustomValidity(valid ? '' : 'Valor inválido.')
      if (!valid) ok = false
      params[spec.api] = value
    }
    return ok ? params : null
  }

  async function run() {
    const params = collect()
    if (!params) {
      state.textContent = 'Corrija os campos marcados para ver o resultado.'
      return
    }
    controller?.abort()
    controller = new AbortController()
    section.setAttribute('aria-busy', 'true')
    state.textContent = 'Calculando…'
    try {
      const data = await getJson(`${form.dataset.api}?${new URLSearchParams(params)}`, { signal: controller.signal })
      points = data.points
      render(data)
      state.textContent = ''
      syncUrlAndSaveForm()
    } catch (error) {
      if (error.name !== 'AbortError') state.textContent = error.message
    } finally {
      section.removeAttribute('aria-busy')
    }
  }

  function render(data) {
    setText('sim-total', formatBRL(data.finalTotal))
    setText('sim-real', formatBRL(data.finalReal))
    setText('sim-investido', formatBRL(data.finalInvested))
    setText('sim-juros', formatBRL(data.interestEarned))
    const hasTarget = data.requiredMonthly !== null
    for (const id of ['sim-meta-rotulo', 'sim-meta', 'sim-aporte-rotulo', 'sim-aporte']) {
      document.getElementById(id).hidden = !hasTarget
    }
    if (hasTarget) {
      setText('sim-meta', data.monthsToTarget === null ? 'mais de 100 anos' : formatDuration(data.monthsToTarget))
      setText('sim-aporte', `${formatBRL(data.requiredMonthly)} por mês`)
    }
    renderTable(data.points)
    renderChart(data.points)
  }

  function renderTable(all) {
    const tbody = document.querySelector('#sim-tabela tbody')
    const rows = all.filter((p) => p.month > 0 && p.month % 12 === 0).map((p) => {
      const tr = document.createElement('tr')
      tr.append(cell(String(p.month / 12)), cell(formatBRL(p.invested), true), cell(formatBRL(p.total), true),
        cell(formatBRL(p.real), true))
      return tr
    })
    tbody.replaceChildren(...rows)
  }

  function geometry(all) {
    const max = Math.max(...all.map((p) => Math.max(p.total, p.invested, p.real)))
    const grid = ticks(max)
    const plotWidth = VIEW.width - VIEW.left - VIEW.right
    const plotHeight = VIEW.height - VIEW.top - VIEW.bottom
    const x = (i) => VIEW.left + (all.length > 1 ? (i * plotWidth) / (all.length - 1) : 0)
    const y = (v) => VIEW.top + plotHeight - (v / grid.top) * plotHeight
    return { grid, x, y, baseline: VIEW.top + plotHeight }
  }

  function renderChart(all) {
    const { grid, x, y, baseline } = geometry(all)
    const desc = svg.querySelector('desc')
    svg.setAttribute('viewBox', `0 0 ${VIEW.width} ${VIEW.height}`)
    const nodes = [desc]
    const gridGroup = el('g', { class: 'chart-grid' })
    for (const v of grid.values) {
      gridGroup.append(el('line', { x1: VIEW.left, x2: VIEW.width - VIEW.right, y1: y(v), y2: y(v) }))
      gridGroup.append(text(compactBRL(v), { x: VIEW.left - 6, y: y(v) + 4, 'text-anchor': 'end', class: 'chart-tick' }))
    }
    nodes.push(gridGroup)
    const years = (all.length - 1) / 12
    const every = years <= 10 ? 1 : years <= 25 ? 5 : 10
    for (let year = 0; year <= years; year += every) {
      nodes.push(text(String(year), { x: x(year * 12), y: baseline + 18, 'text-anchor': 'middle', class: 'chart-tick' }))
    }
    nodes.push(el('line', { x1: VIEW.left, x2: VIEW.width - VIEW.right, y1: baseline, y2: baseline, class: 'chart-baseline' }))
    for (const s of SERIES) {
      nodes.push(el('path', { d: linePath(all.map((p) => p[s.key]), x, y), class: `line ${s.cls}` }))
    }
    // Rótulos diretos no fim das linhas, só quando não se encostam (a legenda já identifica as séries).
    const ends = SERIES.map((s) => ({ s, y: y(all.at(-1)[s.key]) })).sort((a, b) => a.y - b.y)
    const apart = ends.every((e, i) => i === 0 || e.y - ends[i - 1].y >= 14)
    if (apart) {
      for (const e of ends) {
        nodes.push(text(e.s.label, { x: VIEW.width - VIEW.right + 6, y: e.y + 4, class: 'chart-end-label' }))
      }
    }
    nodes.push(el('line', { class: 'chart-crosshair', x1: 0, x2: 0, y1: VIEW.top, y2: baseline, visibility: 'hidden' }))
    svg.replaceChildren(...nodes)
  }

  /** Tooltip: mouse (o ponto mais perto) e teclado (setas andam um ano; Home/End vão às pontas). */
  function setupHover() {
    let index = null
    const show = (i, fromKeyboard) => {
      if (!points.length) return
      index = Math.max(0, Math.min(points.length - 1, i))
      const { x } = geometry(points)
      const p = points[index]
      const crosshair = svg.querySelector('.chart-crosshair')
      crosshair.setAttribute('x1', x(index))
      crosshair.setAttribute('x2', x(index))
      crosshair.setAttribute('visibility', 'visible')
      const title = document.createElement('strong')
      title.textContent = p.month === 0 ? 'Hoje' : `Depois de ${formatDuration(p.month)}`
      const rows = SERIES.map((s) => {
        const row = document.createElement('div')
        const key = document.createElement('span')
        key.className = `key ${s.cls}`
        const value = document.createElement('b')
        value.textContent = formatBRL(p[s.key])
        row.append(key, value, document.createTextNode(` ${s.label}`))
        return row
      })
      tooltip.replaceChildren(title, ...rows)
      tooltip.hidden = false
      tooltip.setAttribute('aria-live', fromKeyboard ? 'polite' : 'off')
      const rect = svg.getBoundingClientRect()
      const left = (x(index) / VIEW.width) * rect.width
      tooltip.style.left = `${Math.min(left + 12, rect.width - tooltip.offsetWidth)}px`
    }
    const hide = () => {
      svg.querySelector('.chart-crosshair')?.setAttribute('visibility', 'hidden')
      tooltip.hidden = true
    }
    svg.addEventListener('pointermove', (event) => {
      if (!points.length) return
      const rect = svg.getBoundingClientRect()
      const px = ((event.clientX - rect.left) / rect.width) * VIEW.width
      show(nearestIndex(px, points.length, geometry(points).x), false)
    })
    svg.addEventListener('pointerleave', hide)
    svg.addEventListener('blur', hide)
    svg.addEventListener('focus', () => show(points.length - 1, true))
    svg.addEventListener('keydown', (event) => {
      const steps = { ArrowRight: 12, ArrowLeft: -12, Home: -Infinity, End: Infinity }
      if (!(event.key in steps) || !points.length) return
      event.preventDefault()
      const step = steps[event.key]
      const current = index ?? points.length - 1
      show(step === -Infinity ? 0 : step === Infinity ? points.length - 1 : current + step, true)
    })
  }

  function syncUrlAndSaveForm() {
    const query = new URLSearchParams()
    for (const name of Object.keys(FIELDS)) query.set(name, form.elements.namedItem(name).value)
    history.replaceState(null, '', `${location.pathname}?${query}`)
    const save = document.getElementById('simulador-salvar')
    if (save) {
      for (const name of Object.keys(FIELDS)) {
        const hidden = save.elements.namedItem(name)
        if (hidden) hidden.value = form.elements.namedItem(name).value
      }
    }
  }
}

function setText(id, value) {
  document.getElementById(id).textContent = value
}

function cell(value, numeric = false) {
  const td = document.createElement('td')
  td.textContent = value
  if (numeric) td.className = 'value num'
  return td
}

function el(name, attrs) {
  const node = document.createElementNS(SVG, name)
  for (const [k, v] of Object.entries(attrs)) node.setAttribute(k, String(v))
  return node
}

function text(content, attrs) {
  const node = el('text', attrs)
  node.textContent = content
  return node
}
