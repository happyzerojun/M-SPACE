const dateTime = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Seoul',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

/** 서버의 +09:00 시각 → "2026. 10. 13. 14:00" (항상 한국 시간으로 보여줘요, NF-06) */
export function formatDateTime(iso) {
  return iso ? dateTime.format(new Date(iso)) : ''
}

/** 로그인 뒤 돌아갈 주소. 다른 사이트로 보내는 주소(//evil.com 등)는 받지 않아요 */
export function safeNext(next) {
  return next && next.startsWith('/') && !next.startsWith('//') ? next : '/'
}
