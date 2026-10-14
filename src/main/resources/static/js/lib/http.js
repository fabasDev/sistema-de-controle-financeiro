/**
 * Chamadas ao servidor a partir do JavaScript.
 * - Manda o token CSRF (lido das <meta> do cabeçalho) em toda requisição que altera dados.
 * - Percebe a sessão expirada: o servidor redireciona para /login e o fetch recebe HTML em vez de JSON.
 */

export class SessionExpiredError extends Error {
  constructor() {
    super('Sua sessão expirou. Recarregue a página e entre de novo.')
    this.name = 'SessionExpiredError'
  }
}

export class RequestError extends Error {
  constructor(status) {
    super('Não foi possível concluir agora. Confira a conexão e tente de novo.')
    this.name = 'RequestError'
    this.status = status
  }
}

export function csrf() {
  const token = document.querySelector('meta[name="_csrf"]')?.content
  const header = document.querySelector('meta[name="_csrf_header"]')?.content
  return token && header ? { header, token } : null
}

async function parse(response) {
  const type = response.headers.get('content-type') ?? ''
  if (response.redirected || (response.ok && !type.includes('application/json'))) {
    throw new SessionExpiredError()
  }
  if (!response.ok) throw new RequestError(response.status)
  return response.json()
}

/** GET que devolve JSON. {@code signal} permite cancelar (AbortController). */
export async function getJson(url, { signal } = {}) {
  const response = await fetch(url, { headers: { Accept: 'application/json' }, credentials: 'same-origin', signal })
  return parse(response)
}

/** POST de formulário (application/x-www-form-urlencoded) com o token CSRF no cabeçalho. */
export async function postForm(url, data) {
  const headers = { Accept: 'application/json' }
  const token = csrf()
  if (token) headers[token.header] = token.token
  const response = await fetch(url, {
    method: 'POST',
    headers,
    body: new URLSearchParams(data),
    credentials: 'same-origin',
  })
  return parse(response)
}
