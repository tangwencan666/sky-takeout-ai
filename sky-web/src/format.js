export function money(value) {
  if (!['number', 'string'].includes(typeof value) || (typeof value === 'string' && !value.trim())) return '—'
  const number = Number(value)
  return Number.isFinite(number) && number >= 0 ? number.toFixed(2) : '—'
}

export function text(value, fallback = '') { return typeof value === 'string' ? value : fallback }
export function list(value) { return Array.isArray(value) ? value : [] }
export function dateTime(value) {
  if (typeof value !== 'string' || !value) return '时间待更新'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '时间待更新' : date.toLocaleString('zh-CN', { hour12: false })
}

export function flavorOptions(value) {
  if (typeof value !== 'string') return []
  try {
    const parsed = JSON.parse(value)
    if (Array.isArray(parsed)) return [...new Set(parsed.filter(item => typeof item === 'string' && item.trim()).map(item => item.trim()))]
  } catch { /* 兼容旧数据中的逗号分隔选项。 */ }
  return [...new Set(value.split(/[,，、;；]/).map(item => item.trim()).filter(Boolean))]
}

export function imageUrl(value) {
  if (typeof value !== 'string') return '/images/dish-placeholder.svg'
  if (/^https?:\/\//i.test(value) || /^\/(?!\/)[^\\]*$/.test(value)) return value
  return '/images/dish-placeholder.svg'
}
