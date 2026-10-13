import { useState } from 'react'
import { Link } from 'react-router'
import { fetchBoard, fetchPolicy } from '../api/board'
import { useAuth } from '../auth/useAuth'
import Alert from '../components/Alert.jsx'
import DateChips from '../components/DateChips.jsx'
import { useAsync } from '../hooks/useAsync'
import { useNow } from '../hooks/useNow'
import { usePollingTick } from '../hooks/usePollingTick'
import { StatusBadge } from '../utils/status.jsx'
import { freeRanges, hhmm, kstToday, minuteOf } from '../utils/time'

/**
 * 실시간 이용 현황 (BD-01~05). 로그인 없이 보고, 방·시간·상태만 보여줘요(예약자 정보 없음).
 * 30초마다 갱신하고 탭이 안 보이면 멈춰요. 휴대폰에서는 방별 세로 목록이에요.
 */
export default function BoardPage() {
  const now = useNow(30_000)
  const today = kstToday(now)
  const [picked, setPicked] = useState(null)
  const date = picked ?? today
  const tick = usePollingTick(30_000)
  const { data: policy } = useAsync(fetchPolicy, 'policy')
  const { data, error } = useAsync(() => fetchBoard(date), `${date}|${tick}`)
  const board = data?.date === date ? data : null
  const nowMinute = date === today ? minuteOf(new Date(now).toISOString(), today) : 0

  return (
    <section>
      <h1 className="text-xl font-bold">실시간 이용 현황</h1>
      <p className="mt-1 text-sm text-gray-600">빈 시간을 누르면 바로 예약할 수 있어요.</p>
      <div className="mt-4">
        <DateChips today={today} days={policy?.bookingWindowDays ?? 7} value={date} onChange={setPicked} />
      </div>
      <div className="mt-4 space-y-3">
        <Alert>{error}</Alert>
        {!board && !error && <p className="text-sm text-gray-500">불러오는 중이에요…</p>}
        {board && <DayInfo board={board} />}
        {board && !board.closed && (
          <ul className="space-y-3">
            {board.rooms.map((room) => (
              <RoomCard key={room.id} room={room} board={board} nowMinute={nowMinute} />
            ))}
          </ul>
        )}
      </div>
    </section>
  )
}

function DayInfo({ board }) {
  if (board.closed) return <Alert kind="info">그날은 휴관이에요{board.periodName ? ` (${board.periodName})` : ''}.</Alert>
  const open = minuteOf(board.openAt, board.date)
  const close = minuteOf(board.closeAt, board.date)
  return (
    <div className="rounded-md border border-gray-200 bg-white px-3 py-2 text-sm">
      <p>
        운영 시간 <strong>{open === 0 && close === 1440 ? '종일 (00:00~24:00)' : `${hhmm(open)}~${hhmm(close)}`}</strong>
        {board.periodName && <span className="ml-1 text-indigo-700">· {board.periodName}</span>}
      </p>
      <p className="mt-0.5 text-xs text-gray-500">
        {board.keyRequired
          ? '예약 시간에 학과실에서 학생증을 확인하고 키를 받아요.'
          : '방을 열어두는 기간이에요. 키 없이 시작 10분 전부터 앱에서 "이용 시작"을 눌러요.'}
      </p>
    </div>
  )
}

function RoomCard({ room, board, nowMinute }) {
  const { user } = useAuth()
  const open = minuteOf(board.openAt, board.date)
  const close = minuteOf(board.closeAt, board.date)
  const blocks = room.blocks.map((b) => ({
    start: Math.max(minuteOf(b.startAt, board.date), 0),
    end: Math.min(minuteOf(b.endAt, board.date), 1440),
    status: b.status,
  }))
  const free = room.active ? freeRanges(open, close, blocks, nowMinute) : []
  const items = [...blocks.map((b) => ({ ...b, kind: 'block' })), ...free.map((f) => ({ ...f, kind: 'free' }))]
    .filter((x) => x.end > Math.max(open, x.kind === 'block' ? 0 : nowMinute))
    .sort((a, b) => a.start - b.start)

  return (
    <li className="rounded-lg border border-gray-200 bg-white p-3" data-room={room.name}>
      <div className="flex items-baseline justify-between">
        <h2 className="font-semibold">{room.name}</h2>
        <span className="text-xs text-gray-500">최대 {room.capacity}명</span>
      </div>
      {!room.active && <p className="mt-2 text-sm text-gray-500">지금은 이용할 수 없어요.</p>}
      {room.active && items.length === 0 && <p className="mt-2 text-sm text-gray-500">남은 운영 시간이 없어요.</p>}
      <ul className="mt-2 space-y-1.5">
        {items.map((x) =>
          x.kind === 'block' ? (
            <li key={`b${x.start}`} className="flex items-center justify-between rounded-md bg-gray-50 px-2 py-1.5 text-sm">
              <span className="tabular-nums">{hhmm(x.start)}~{hhmm(x.end)}</span>
              <StatusBadge status={x.status} />
            </li>
          ) : (
            <li key={`f${x.start}`}>
              <Link
                to={`/reserve?roomId=${room.id}&date=${board.date}&start=${x.start}`}
                className="flex items-center justify-between rounded-md border border-dashed border-indigo-300 px-2 py-1.5 text-sm text-indigo-700"
              >
                <span className="tabular-nums">{hhmm(x.start)}~{hhmm(x.end)}</span>
                <span className="text-xs font-medium">{user ? '비어 있음 · 예약하기 ›' : '비어 있음 · 로그인 후 예약 ›'}</span>
              </Link>
            </li>
          ),
        )}
      </ul>
    </li>
  )
}
