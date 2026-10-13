import { Link, useSearchParams } from 'react-router'
import { fetchBoard, fetchPolicy } from '../api/board'
import Alert from '../components/Alert.jsx'
import DateChips from '../components/DateChips.jsx'
import RoomVisual, { PeopleIcon } from '../components/RoomVisual.jsx'
import SurveyCard from '../components/SurveyCard.jsx'
import { useAsync } from '../hooks/useAsync'
import { useNow } from '../hooks/useNow'
import { usePollingTick } from '../hooks/usePollingTick'
import { roomDay } from '../utils/slots'
import { hhmm, kstToday, minuteOf } from '../utils/time'

/**
 * 홈 = 실시간 이용 현황 (BD-01~05). 숙박 앱처럼 방 카드를 보여주고, 빈 시간 칩을 누르면 바로 그 시간으로 예약 화면에 가요.
 * 로그인 없이 보고, 방·시간·상태만 보여줘요(예약자 정보 없음). 30초마다 갱신하고 탭이 안 보이면 멈춰요.
 */
export default function BoardPage() {
  const now = useNow(30_000)
  const today = kstToday(now)
  // 고른 날짜는 주소에 남겨서 방 화면에서 돌아와도 그대로예요
  const [params, setParams] = useSearchParams()
  const date = params.get('date') ?? today
  const setPicked = (d) => setParams(d === today ? {} : { date: d }, { replace: true })
  const tick = usePollingTick(30_000)
  const { data: policy } = useAsync(fetchPolicy, 'policy')
  const { data, error } = useAsync(() => fetchBoard(date), `${date}|${tick}`)
  const board = data?.date === date ? data : null
  const nowMinute = date === today ? minuteOf(new Date(now).toISOString(), today) : 0

  return (
    <section className="space-y-5">
      <div>
        <h1 className="text-2xl font-bold">어느 방에서 공부할까요?</h1>
        <p className="mt-1 text-sm text-gray-500">빈 시간을 누르면 바로 예약할 수 있어요. 한 번에 최대 {policy ? policy.maxDurationMinutes / 60 : 2}시간이에요.</p>
      </div>
      <DateChips today={today} days={policy?.bookingWindowDays ?? 7} value={date} onChange={setPicked} />
      <Alert>{error}</Alert>
      {!board && !error && <p className="text-sm text-gray-500">불러오는 중이에요…</p>}
      {board && <DayNotice board={board} />}
      {board && !board.closed && (
        <ul className="grid gap-3 sm:grid-cols-2">
          {board.rooms.map((room) => (
            <RoomCard key={room.id} room={room} board={board} nowMinute={nowMinute} isToday={date === today} />
          ))}
        </ul>
      )}
      <SurveyCard />
    </section>
  )
}

export function DayNotice({ board }) {
  if (board.closed) return <Alert kind="info">그날은 휴관이에요{board.periodName ? ` (${board.periodName})` : ''}.</Alert>
  const open = minuteOf(board.openAt, board.date)
  const close = minuteOf(board.closeAt, board.date)
  const allDay = open === 0 && close === 1440
  return (
    <div className={`rounded-2xl px-4 py-3 text-sm ${board.keyRequired ? 'bg-white ring-1 ring-gray-100' : 'bg-emerald-50 ring-1 ring-emerald-100'}`}>
      <p className="font-medium">
        {board.periodName && <span className="mr-1.5 rounded-full bg-emerald-600 px-2 py-0.5 text-xs text-white">{board.periodName}</span>}
        운영 {allDay ? '종일 (00:00~24:00)' : `${hhmm(open)}~${hhmm(close)}`}
      </p>
      <p className="mt-1 text-xs text-gray-500">
        {board.keyRequired
          ? '예약 시간에 학과실에서 학생증을 확인하고 키를 받아요.'
          : '방을 열어두는 기간이에요. 키 없이 시작 10분 전부터 앱에서 "이용 시작"을 눌러요.'}
      </p>
    </div>
  )
}

function RoomCard({ room, board, nowMinute, isToday }) {
  const day = roomDay(board, room, nowMinute)
  const roomUrl = `/rooms/${room.id}?date=${board.date}`
  const nextFree = day.free[0]
  const status = !room.active
    ? { text: '지금은 이용할 수 없어요', dot: 'bg-gray-400' }
    : isToday && day.current
      ? { text: nextFree ? `이용 중 · ${hhmm(nextFree.start)}부터 비어요` : '이용 중 · 오늘은 마감', dot: 'bg-rose-500' }
      : isToday && nextFree?.start <= Math.ceil(nowMinute / 30) * 30
        ? { text: '지금 비어 있어요', dot: 'bg-emerald-500' }
        : nextFree
          ? { text: `${hhmm(nextFree.start)}부터 예약 가능`, dot: 'bg-emerald-500' }
          : { text: '남은 빈 시간이 없어요', dot: 'bg-gray-400' }

  return (
    <li className="rounded-2xl bg-white p-3 shadow-sm ring-1 ring-gray-100" data-room={room.name}>
      <Link to={roomUrl} aria-label={`${room.name} 시간 고르기`} className="flex items-center gap-3">
        <RoomVisual room={room} size="thumb" />
        <div className="min-w-0 flex-1">
          <div className="flex items-baseline justify-between gap-2">
            <p className="text-lg font-bold">{room.name}</p>
            <span className="shrink-0 text-xs font-medium text-indigo-600">시간표 ›</span>
          </div>
          <p className="mt-0.5 flex items-center gap-1 text-xs text-gray-500"><PeopleIcon /> 최대 {room.capacity}명</p>
          <p className="mt-1 flex items-center gap-1.5 text-sm font-medium text-gray-800">
            <span className={`h-2 w-2 shrink-0 rounded-full ${status.dot}`} />
            <span className="truncate">{status.text}</span>
          </p>
        </div>
      </Link>
      <div>
        {day.free.length > 0 && (
          <div className="mt-3 flex flex-wrap gap-1.5">
            {day.free.slice(0, 3).map((f) => (
              <Link key={f.start} to={`${roomUrl}&start=${f.start}`} data-quick-slot
                className="rounded-full bg-indigo-50 px-3 py-1.5 text-sm font-medium tabular-nums text-indigo-700">
                {hhmm(f.start)}~{hhmm(f.end)}
              </Link>
            ))}
            {day.free.length > 3 && <Link to={roomUrl} className="rounded-full px-2 py-1.5 text-sm text-gray-500">+{day.free.length - 3}</Link>}
          </div>
        )}
      </div>
    </li>
  )
}
