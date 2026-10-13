import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, type Plugin } from 'vite'

/**
 * Content Security Policy: só permite código e recursos do próprio app.
 * Nenhuma requisição externa (fontes são locais, sem analytics/CDN).
 * `style-src 'unsafe-inline'` é necessário para os estilos inline dos gráficos (Recharts).
 */
const CSP_DIRECTIVES = [
  "default-src 'self'",
  "script-src 'self'",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob:",
  "font-src 'self' data:",
  "connect-src 'self'",
  "worker-src 'self'",
  "object-src 'none'",
  "base-uri 'none'",
  "form-action 'none'",
]

const securityHeaders = {
  'X-Content-Type-Options': 'nosniff',
  'X-Frame-Options': 'DENY',
  'Referrer-Policy': 'no-referrer',
  'Permissions-Policy': 'camera=(), microphone=(), geolocation=(), payment=(), usb=(), interest-cohort=()',
  'Cross-Origin-Opener-Policy': 'same-origin',
  'Cross-Origin-Resource-Policy': 'same-origin',
}

/** Injeta a CSP no HTML do build (o servidor de dev usa scripts inline para o HMR). */
function cspMeta(): Plugin {
  return {
    name: 'financas-csp-meta',
    apply: 'build',
    transformIndexHtml: (html) =>
      html.replace(
        '<meta charset="UTF-8" />',
        `<meta charset="UTF-8" />\n    <meta http-equiv="Content-Security-Policy" content="${CSP_DIRECTIVES.join('; ')}" />`,
      ),
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss(), cspMeta()],
  resolve: {
    alias: {
      '@': `${import.meta.dirname}/src`,
    },
  },
  server: {
    // Só acessível neste computador (não expõe o app na rede local).
    host: 'localhost',
    headers: securityHeaders,
  },
  preview: {
    host: 'localhost',
    headers: {
      ...securityHeaders,
      'Content-Security-Policy': [...CSP_DIRECTIVES, "frame-ancestors 'none'"].join('; '),
    },
  },
  build: {
    sourcemap: false,
  },
})
