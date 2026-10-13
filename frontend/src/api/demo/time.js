// 데모 서버의 한국 시간 계산. 한국은 서머타임이 없어서 UTC+9 고정으로 계산해요 (NF-06)
const KST_OFFSET = 9 * 3600 * 1000
export const MINUTE = 60 * 1000

/** ms → "2026-10-13T14:00:00+09:00" (API 명세의 시각 형식) */
export function toKstIso(ms) {
  return new Date(ms + KST_OFFSET).toISOString().slice(0, 19) + '+09:00'
}

/** ms → 한국 날짜 "2026-10-13" */
export function kstDate(ms) {
  return new Date(ms + KST_OFFSET).toISOString().slice(0, 10)
}

/** 한국 날짜의 0시 기준 minute분 뒤 (1440이면 다음 날 0시) */
export function atMinute(date, minute) {
  return Date.parse(`${date}T00:00:00+09:00`) + minute * MINUTE
}

export function addDays(date, days) {
  return kstDate(atMinute(date, days * 1440))
}

/** 날짜 사이 일수 (b - a) */
export function daysBetween(a, b) {
  return Math.round((atMinute(b, 0) - atMinute(a, 0)) / (1440 * MINUTE))
}

export const formatMinute = (m) => `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`
