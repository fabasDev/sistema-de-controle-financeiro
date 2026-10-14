/**
 * Melhorias de formulário (o servidor continua validando tudo; isto é só para avisar antes):
 * - mensagem ao lado do campo, em português, no lugar do balão do navegador;
 * - aria-invalid sincronizado com o estado visual (:user-invalid);
 * - foco no primeiro campo com problema;
 * - botão desativado depois do envio, para não salvar duas vezes.
 */

const MESSAGES = {
  valueMissing: 'Preencha este campo.',
  patternMismatch: 'Formato inválido.',
  rangeUnderflow: 'Valor menor que o permitido.',
  rangeOverflow: 'Valor maior que o permitido.',
  tooLong: 'Texto longo demais.',
  typeMismatch: 'Formato inválido.',
  badInput: 'Formato inválido.',
}

function messageFor(field) {
  const v = field.validity
  if (v.customError) return field.validationMessage
  for (const key of Object.keys(MESSAGES)) {
    if (v[key]) return field.dataset[`msg${key[0].toUpperCase()}${key.slice(1)}`] ?? MESSAGES[key]
  }
  return field.validationMessage
}

/** Parágrafo de erro do cliente, ligado ao campo por aria-describedby. */
function errorElement(field) {
  const id = `${field.id || field.name}-erro-cliente`
  let el = document.getElementById(id)
  if (!el) {
    el = document.createElement('p')
    el.id = id
    el.className = 'field-error'
    const anchor = field.closest('.field') ?? field.parentElement
    anchor.append(el)
    const described = (field.getAttribute('aria-describedby') ?? '').split(' ').filter(Boolean)
    field.setAttribute('aria-describedby', [...described, id].join(' '))
  }
  return el
}

function showError(field) {
  errorElement(field).textContent = messageFor(field)
  field.setAttribute('aria-invalid', 'true')
}

function clearError(field) {
  const el = document.getElementById(`${field.id || field.name}-erro-cliente`)
  if (el) el.textContent = ''
  if (field.checkValidity()) field.removeAttribute('aria-invalid')
}

export function enhanceForms(root = document) {
  root.addEventListener(
    'invalid',
    (event) => {
      const field = event.target
      event.preventDefault()
      showError(field)
      const form = field.form
      // Foca só o primeiro campo inválido de cada tentativa de envio.
      if (form && !form.dataset.focused) {
        form.dataset.focused = 'true'
        field.focus()
        queueMicrotask(() => delete form.dataset.focused)
      }
    },
    true,
  )
  root.addEventListener(
    'blur',
    (event) => {
      const field = event.target
      if (!field.matches?.('input, select, textarea')) return
      if (field.matches(':user-invalid')) showError(field)
    },
    true,
  )
  root.addEventListener('input', (event) => {
    const field = event.target
    if (!field.matches?.('input, select, textarea')) return
    field.setCustomValidity('')
    if (field.getAttribute('aria-invalid') === 'true') clearError(field)
  })
  root.addEventListener('submit', (event) => {
    const form = event.target
    if (!(form instanceof HTMLFormElement) || form.method.toLowerCase() !== 'post') return
    const button = event.submitter
    if (button) {
      // Depois do envio válido, evita o duplo clique (o servidor ainda responde normalmente).
      setTimeout(() => {
        button.disabled = true
      }, 0)
    }
  })
}
