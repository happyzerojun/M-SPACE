import { freeRanges, minuteOf } from './time'

/**
 * 현황판 응답(board)에서 방 하나의 그날 상태를 계산해요 (서버 호출 없이 화면에서만).
 * slots: 30분 칸마다 free(예약 가능) · booked(예약됨) · past(지난 시간)
 * free: 빈 구간 [{start, end}] (분), nowFreeUntil: 지금 비어 있으면 언제까지
 */
export function roomDay(board, room, nowMinute) {
  if (!board || board.closed || !room?.active) return { open: 0, close: 0, slots: [], free: [], blocks: [] }
  const open = minuteOf(board.openAt, board.date)
  const close = minuteOf(board.closeAt, board.date)
  const blocks = room.blocks.map((b) => ({
    start: Math.max(minuteOf(b.startAt, board.date), 0),
    end: Math.min(minuteOf(b.endAt, board.date), 1440),
    status: b.status,
  }))
  const slots = []
  for (let m = open; m < close; m += 30) {
    const booked = blocks.some((b) => m < b.end && b.start < m + 30)
    slots.push({ start: m, state: m + 30 <= nowMinute ? 'past' : booked ? 'booked' : m < nowMinute ? 'past' : 'free' })
  }
  const free = freeRanges(open, close, blocks, nowMinute)
  const current = blocks.find((b) => b.start <= nowMinute && nowMinute < b.end)
  return { open, close, slots, free, blocks, current }
}

/** start부터 이어서 비어 있는 시간(분). 이용 시간 칩을 고를 수 있는지 판단해요 */
export function freeLengthFrom(slots, start) {
  let length = 0
  for (const s of slots) {
    if (s.start < start) continue
    if (s.start !== start + length || s.state !== 'free') break
    length += 30
  }
  return length
}

export function durationLabel(minutes) {
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return [h && `${h}시간`, m && `${m}분`].filter(Boolean).join(' ')
}

/** 방마다 다른 색 (실제 방 사진이 생기면 바꿔요) */
const GRADIENTS = [
  'from-indigo-400 to-violet-500',
  'from-sky-400 to-cyan-500',
  'from-emerald-400 to-teal-500',
  'from-amber-400 to-orange-500',
  'from-rose-400 to-pink-500',
]
export const roomGradient = (roomId) => GRADIENTS[(roomId - 1) % GRADIENTS.length]
