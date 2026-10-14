/**
 * Melhorias progressivas comuns a todas as telas. Sem JavaScript tudo continua funcionando:
 * formulários vão para o servidor e apagar passa pela página de confirmação.
 */
import { enhanceForms } from './lib/forms.js'
import { csrf } from './lib/http.js'

enhanceForms()

/**
 * Links "Apagar" com data-delete viram um POST direto: como dá para desfazer logo depois,
 * a página de confirmação só é usada sem JavaScript.
 */
document.addEventListener('click', (event) => {
  const link = event.target.closest?.('a[data-delete]')
  if (!link || event.defaultPrevented || event.button !== 0 || event.ctrlKey || event.metaKey) return
  event.preventDefault()
  const form = document.createElement('form')
  form.method = 'post'
  form.action = link.href
  const token = csrf()
  const meta = document.querySelector('meta[name="_csrf_parameter"]')?.content
  if (token && meta) {
    const input = document.createElement('input')
    input.type = 'hidden'
    input.name = meta
    input.value = token.token
    form.append(input)
  }
  document.body.append(form)
  form.submit()
})

// "Mais" na barra do celular (<details>): Esc fecha e devolve o foco ao botão; clicar fora também fecha.
const menu = document.querySelector('.tab-more')
if (menu) {
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && menu.open) {
      menu.open = false
      menu.querySelector('summary').focus()
    }
  })
  document.addEventListener('click', (event) => {
    if (menu.open && !menu.contains(event.target)) menu.open = false
  })
}

// Voltar pelo histórico do navegador pode trazer a página com o botão ainda desativado.
window.addEventListener('pageshow', () => {
  for (const button of document.querySelectorAll('button[disabled]:not([data-keep-disabled])')) {
    button.disabled = false
  }
})
