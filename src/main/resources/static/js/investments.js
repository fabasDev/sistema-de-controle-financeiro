/**
 * Tela de investimentos: questionário de perfil e assistente.
 * - O questionário roda aqui e manda ao servidor só o perfil e a pontuação (as respostas ficam no navegador).
 * - O assistente busca os números da conta uma vez (/api/assistente) e responde localmente.
 * Tudo é montado com createElement/textContent, nunca innerHTML: o nome de exibição é texto do usuário.
 */
import { answer, greeting, PROFILES, SUGGESTIONS } from './lib/advisor.js'
import { getJson } from './lib/http.js'
import { MAX_SCORE, QUIZ, scoreProfile } from './lib/quiz.js'

setupQuiz()
setupAdvisor()

function setupQuiz() {
  const root = document.getElementById('questionario-js')
  const saveForm = document.getElementById('perfil-form')
  if (!root || !saveForm) return

  const form = document.createElement('form')
  form.className = 'form quiz'
  QUIZ.forEach((q, qi) => {
    const fieldset = document.createElement('fieldset')
    fieldset.className = 'field'
    const legend = document.createElement('legend')
    legend.textContent = `${qi + 1}. ${q.question}`
    fieldset.append(legend)
    if (q.help) {
      const help = document.createElement('p')
      help.className = 'hint'
      help.textContent = q.help
      fieldset.append(help)
    }
    q.options.forEach((option, oi) => {
      const label = document.createElement('label')
      const input = document.createElement('input')
      input.type = 'radio'
      input.name = `quiz-${q.id}`
      input.value = String(oi)
      input.required = true
      input.dataset.msgValueMissing = 'Escolha uma opção.'
      label.append(input, document.createTextNode(` ${option.label}`))
      fieldset.append(label)
    })
    form.append(fieldset)
  })
  const submit = document.createElement('button')
  submit.type = 'submit'
  submit.className = 'button-primary'
  submit.textContent = 'Ver meu perfil'
  form.append(submit)

  const result = document.createElement('div')
  result.className = 'quiz-result'
  result.setAttribute('role', 'status')

  form.addEventListener('submit', (event) => {
    event.preventDefault()
    const answers = {}
    for (const q of QUIZ) {
      const checked = form.querySelector(`input[name="quiz-${q.id}"]:checked`)
      answers[q.id] = Number(checked.value)
    }
    const { score, profile, serverProfile } = scoreProfile(answers)
    const info = PROFILES[serverProfile]
    const title = document.createElement('p')
    const strong = document.createElement('strong')
    strong.textContent = info.name
    title.append('Seu perfil: ', strong, ` (${score} de ${MAX_SCORE} pontos).`)
    const summary = document.createElement('p')
    summary.textContent = `${info.summary} ${info.detail}`
    const save = document.createElement('button')
    save.type = 'button'
    save.className = 'button-primary'
    save.textContent = 'Salvar este perfil'
    save.addEventListener('click', () => {
      saveForm.elements.namedItem('profile').value = serverProfile
      saveForm.elements.namedItem('score').value = String(score)
      saveForm.submit()
    })
    result.replaceChildren(title, summary, save)
  })

  root.replaceChildren(form, result)
}

function setupAdvisor() {
  const root = document.getElementById('assistente-js')
  if (!root) return
  const log = document.createElement('div')
  log.className = 'chat-log'
  log.setAttribute('role', 'log')
  log.setAttribute('aria-live', 'polite')
  const status = document.createElement('p')
  status.className = 'small'
  status.setAttribute('role', 'status')
  status.textContent = 'Carregando seus números…'
  root.replaceChildren(status)

  getJson('/api/assistente')
    .then((ctx) => {
      ctx.maxScore = MAX_SCORE
      status.textContent = ''
      render(ctx)
    })
    .catch((error) => {
      status.textContent = `${error.message} O assistente precisa dos seus números para responder.`
    })

  function render(ctx) {
    const suggestions = document.createElement('div')
    suggestions.className = 'chat-suggestions'
    for (const s of SUGGESTIONS) {
      const button = document.createElement('button')
      button.type = 'button'
      button.textContent = s
      button.addEventListener('click', () => ask(s))
      suggestions.append(button)
    }
    const form = document.createElement('form')
    form.className = 'chat-form'
    const label = document.createElement('label')
    label.htmlFor = 'chat-pergunta'
    label.textContent = 'Sua pergunta'
    const input = document.createElement('input')
    input.id = 'chat-pergunta'
    input.type = 'text'
    input.maxLength = 300
    input.autocomplete = 'off'
    input.enterKeyHint = 'send'
    const send = document.createElement('button')
    send.type = 'submit'
    send.textContent = 'Perguntar'
    form.append(label, input, send)
    form.addEventListener('submit', (event) => {
      event.preventDefault()
      const question = input.value.trim()
      if (!question) return
      input.value = ''
      ask(question)
    })
    root.append(log, suggestions, form)
    addMessage('assistant', greeting(ctx))

    function ask(question) {
      suggestions.hidden = true
      addMessage('user', [{ t: 'p', text: [question] }])
      addMessage('assistant', answer(question, ctx))
    }
  }

  function addMessage(who, blocks) {
    const message = document.createElement('div')
    message.className = `chat-message chat-${who}`
    const sender = document.createElement('p')
    sender.className = 'visually-hidden'
    sender.textContent = who === 'user' ? 'Você:' : 'Assistente:'
    message.append(sender, ...blocks.map(renderBlock))
    log.append(message)
    message.scrollIntoView({ block: 'nearest' })
  }
}

/** Monta um bloco da resposta com elementos do DOM (texto sempre via textContent). */
function renderBlock(block) {
  switch (block.t) {
    case 'p': {
      const p = document.createElement('p')
      p.append(...block.text.map(rich))
      return p
    }
    case 'ul': {
      const ul = document.createElement('ul')
      for (const item of block.items) {
        const li = document.createElement('li')
        li.append(...item.map(rich))
        ul.append(li)
      }
      return ul
    }
    case 'kv': {
      const dl = document.createElement('dl')
      dl.className = 'figures'
      for (const [k, v] of block.rows) {
        const dt = document.createElement('dt')
        dt.textContent = k
        const dd = document.createElement('dd')
        dd.className = 'value'
        dd.textContent = v
        dl.append(dt, dd)
      }
      return dl
    }
    case 'actions': {
      const p = document.createElement('p')
      for (const a of block.actions) {
        const link = document.createElement('a')
        link.href = a.href
        link.textContent = a.label
        p.append(link)
      }
      return p
    }
    default: {
      const p = document.createElement('p')
      p.className = 'small'
      p.textContent = block.text
      return p
    }
  }
}

function rich(part) {
  if (typeof part === 'string') return document.createTextNode(part)
  const strong = document.createElement('strong')
  strong.textContent = part.b
  return strong
}
