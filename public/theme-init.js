;(function () {
  var dark = false
  try {
    var pref = localStorage.getItem('financas:theme') || 'system'
    dark = pref === 'dark' || (pref === 'system' && matchMedia('(prefers-color-scheme: dark)').matches)
  } catch {
    dark = matchMedia('(prefers-color-scheme: dark)').matches
  }
  document.documentElement.classList.toggle('dark', dark)
})()
