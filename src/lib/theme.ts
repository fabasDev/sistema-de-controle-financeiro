import { create } from 'zustand'

export type ThemePref = 'system' | 'light' | 'dark'

const KEY = 'financas:theme'
const media = window.matchMedia('(prefers-color-scheme: dark)')

function read(): ThemePref {
  try {
    const v = localStorage.getItem(KEY)
    return v === 'light' || v === 'dark' ? v : 'system'
  } catch {
    return 'system'
  }
}

function resolve(pref: ThemePref) {
  return pref === 'dark' || (pref === 'system' && media.matches) ? 'dark' : 'light'
}

function apply(pref: ThemePref) {
  const resolved = resolve(pref)
  document.documentElement.classList.toggle('dark', resolved === 'dark')
  document.querySelector('meta[name="color-scheme"]')?.setAttribute('content', pref === 'system' ? 'light dark' : pref)
  return resolved
}

interface ThemeState {
  pref: ThemePref
  resolved: 'light' | 'dark'
  setPref: (pref: ThemePref) => void
}

/** Preferência de tema (única informação salva sem criptografia — não é dado pessoal). */
export const useTheme = create<ThemeState>()((set) => {
  const pref = read()
  media.addEventListener('change', () => set((s) => ({ resolved: apply(s.pref) })))
  return {
    pref,
    resolved: apply(pref),
    setPref: (next) => {
      try {
        if (next === 'system') localStorage.removeItem(KEY)
        else localStorage.setItem(KEY, next)
      } catch {
        /* sem armazenamento: o tema vale só nesta visita */
      }
      set({ pref: next, resolved: apply(next) })
    },
  }
})
