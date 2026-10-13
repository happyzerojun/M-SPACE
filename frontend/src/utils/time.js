// 화면의 한국 시간 계산 (서버 응답은 +09:00 문자열). 한국은 서머타임이 없어 UTC+9 고정이에요
const KST = 9 * 3600 * 1000
const MIN = 60 * 1000

/** 지금 한국 날짜 "2026-10-13" — 렌더링 중이 아니라 이벤트·이펙트에서 불러요 (lint 규칙) */
export const kstToday = (nowMs) => new Date(nowMs + KST).toISOString().slice(0, 10)
export const addDays = (date, n) => new Date(Date.parse(`${date}T00:00:00Z`) + n * 86400000).toISOString().slice(0, 10)
export const toMs = (iso) => Date.parse(iso)
/** "2026-10-13", 840 → "2026-10-13T14:00:00+09:00" */
export const isoAt = (date, minute) => new Date(Date.parse(`${date}T00:00:00+09:00`) + minute * MIN + KST).toISOString().slice(0, 19) + '+09:00'
/** ISO → 그날 0시 기준 분 (다음 날 0시는 1440) */
export const minuteOf = (iso, date) => Math.round((Date.parse(iso) - Date.parse(`${date}T00:00:00+09:00`)) / MIN)
export const hhmm = (minute) => `${String(Math.floor(minute / 60)).padStart(2, '0')}:${String(minute % 60).padStart(2, '0')}`
/** ISO → "14:00" (한국 시간) */
export const timeOf = (iso) => new Date(Date.parse(iso) + KST).toISOString().slice(11, 16)

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']
/** "2026-10-13" → "10/13 (화)" */
export function dateLabel(date) {
  const d = new Date(`${date}T00:00:00Z`)
  return `${d.getUTCMonth() + 1}/${d.getUTCDate()} (${WEEKDAYS[d.getUTCDay()]})`
}

/**
 * 운영 시간에서 예약된 구간을 빼고 남은 빈 구간 [{start, end}] (분). fromMinute 이전은 지난 시간이라 빼요
 */
export function freeRanges(open, close, blocks, fromMinute = 0) {
  const result = []
  let cursor = Math.max(open, Math.ceil(fromMinute / 30) * 30)
  for (const b of [...blocks].sort((a, c) => a.start - c.start)) {
    if (b.start > cursor) result.push({ start: cursor, end: Math.min(b.start, close) })
    cursor = Math.max(cursor, b.end)
  }
  if (cursor < close) result.push({ start: cursor, end: close })
  return result.filter((r) => r.end > r.start)
}
