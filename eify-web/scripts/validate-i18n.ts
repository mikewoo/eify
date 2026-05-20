import { readFileSync, readdirSync } from 'node:fs'
import { resolve, basename, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const localesDir = resolve(__dirname, '..', 'src', 'i18n', 'locales')

function getKeys(obj: unknown, prefix = ''): string[] {
  if (typeof obj !== 'object' || obj === null) return [prefix]
  const keys: string[] = []
  for (const [k, v] of Object.entries(obj as Record<string, unknown>)) {
    const path = prefix ? `${prefix}.${k}` : k
    if (typeof v === 'object' && v !== null && !Array.isArray(v)) {
      keys.push(...getKeys(v, path))
    } else {
      keys.push(path)
    }
  }
  return keys
}

const localeFiles = readdirSync(localesDir)
  .filter(f => f.endsWith('.json'))
  .map(f => basename(f, '.json'))

if (localeFiles.length < 2) {
  console.log('[i18n] Only one locale found, nothing to compare.')
  process.exit(0)
}

const refLocale = localeFiles.includes('zh-CN') ? 'zh-CN' : localeFiles[0]
const otherLocales = localeFiles.filter(l => l !== refLocale)

const refData = JSON.parse(readFileSync(resolve(localesDir, `${refLocale}.json`), 'utf-8'))
const refKeys = new Set(getKeys(refData))

let hasError = false

for (const locale of otherLocales) {
  const data = JSON.parse(readFileSync(resolve(localesDir, `${locale}.json`), 'utf-8'))
  const keys = new Set(getKeys(data))
  const missing = [...refKeys].filter(k => !keys.has(k))
  const extra = [...keys].filter(k => !refKeys.has(k))

  if (missing.length) {
    console.error(`[i18n] ${locale}.json — missing keys: ${missing.join(', ')}`)
    hasError = true
  }
  if (extra.length) {
    console.error(`[i18n] ${locale}.json — extra keys: ${extra.join(', ')}`)
    hasError = true
  }
}

if (!hasError) {
  console.log(`[i18n] All ${localeFiles.length} locale(s) verified against ${refLocale}.json.`)
} else {
  process.exit(1)
}
