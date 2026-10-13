import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { fetchBoard, fetchPolicy } from '../api/board'
import { errorMessage, fieldErrorsOf } from '../api/client'
import { createReservation } from '../api/reservations'
import Alert from '../components/Alert.jsx'
import DateChips from '../components/DateChips.jsx'
import { useAsync } from '../hooks/useAsync'
import { useNow } from '../hooks/useNow'
import { dateLabel, freeRanges, hhmm, isoAt, kstToday, minuteOf } from '../utils/time'

const selectClass = 'mt-1 block w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-base'

/**
 * 예약 신청 (RV-01~08). 학번·이름·전화번호는 회원 정보로 자동으로 들어가서 묻지 않아요.
 * 화면은 빈 시간만 고르게 도와주는 것이고, 최종 판단(겹침·한도·운영 시간)은 서버가 해요.
 */
export default function ReservePage() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const now = useNow(60_000)
  const today = kstToday(now)
  const [form, setForm] = useState(() => ({
    date: params.get('date') ?? null,
    roomId: params.get('roomId') ? Number(params.get('roomId')) : null,
    start: params.get('start') ? Number(params.get('start')) : null,
    duration: 120,
    headcount: 2,
  }))
  const date = form.date ?? today
  const { data: policy } = useAsync(fetchPolicy, 'policy')
  const { data: board, error: loadError } = useAsync(() => fetchBoard(date), date)
  const [error, setError] = useState(null)
  const [fieldErrors, setFieldErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)

  const ready = board?.date === date && policy
  const rooms = ready && !board.closed ? board.rooms.filter((r) => r.active) : []
  const room = rooms.find((r) => r.id === form.roomId) ?? rooms[0]
  const open = ready && !board.closed ? minuteOf(board.openAt, date) : 0
  const close = ready && !board.closed ? minuteOf(board.closeAt, date) : 0
  const nowMinute = date === today ? minuteOf(new Date(now).toISOString(), today) + 1 : 0
  const blocks = room ? room.blocks.map((b) => ({ start: minuteOf(b.startAt, date), end: minuteOf(b.endAt, date) })) : []
  const free = room ? freeRanges(open, close, blocks, nowMinute) : []
  const starts = free.flatMap((r) => Array.from({ length: (r.end - r.start) / 30 }, (_, i) => r.start + i * 30))
  const start = starts.includes(form.start) ? form.start : starts[0]
  const range = free.find((r) => start >= r.start && start < r.end)
  const maxDuration = range ? Math.min(policy.maxDurationMinutes, range.end - start) : 0
  const durations = Array.from({ length: maxDuration / 30 }, (_, i) => (i + 1) * 30)
  const duration = Math.min(form.duration, maxDuration)
  const headcount = room ? Math.min(form.headcount, room.capacity) : 1

  const set = (patch) => setForm({ ...form, ...patch })

  async function submit(e) {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    setFieldErrors({})
    try {
      await createReservation({
        roomId: room.id, headcount,
        startAt: isoAt(date, start), endAt: isoAt(date, start + duration),
      })
      navigate('/me/reservations', { state: { message: '예약했어요.' } })
    } catch (err) {
      setError(errorMessage(err))
      setFieldErrors(fieldErrorsOf(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="mx-auto max-w-md">
      <h1 className="text-xl font-bold">예약 신청</h1>
      <form onSubmit={submit} className="mt-4 space-y-4">
        <div>
          <p className="text-sm font-medium text-gray-700">날짜</p>
          <div className="mt-1">
            <DateChips today={today} days={policy?.bookingWindowDays ?? 7} value={date}
              onChange={(d) => set({ date: d, start: null })} />
          </div>
        </div>

        <Alert>{loadError}</Alert>
        {ready && board.closed && <Alert kind="info">그날은 휴관이에요.</Alert>}
        {ready && !board.closed && (
          <>
            <label className="block text-sm font-medium text-gray-700">
              방
              <select className={selectClass} value={room?.id ?? ''} onChange={(e) => set({ roomId: Number(e.target.value), start: null })}>
                {rooms.map((r) => (
                  <option key={r.id} value={r.id}>{r.name} (최대 {r.capacity}명)</option>
                ))}
              </select>
            </label>

            {starts.length === 0 ? (
              <Alert kind="info">이 방은 {dateLabel(date)}에 남은 빈 시간이 없어요. 다른 방이나 날짜를 골라 주세요.</Alert>
            ) : (
              <div className="grid grid-cols-2 gap-3">
                <label className="block text-sm font-medium text-gray-700">
                  시작
                  <select className={selectClass} value={start} onChange={(e) => set({ start: Number(e.target.value) })}>
                    {starts.map((m) => <option key={m} value={m}>{hhmm(m)}</option>)}
                  </select>
                </label>
                <label className="block text-sm font-medium text-gray-700">
                  이용 시간
                  <select className={selectClass} value={duration} onChange={(e) => set({ duration: Number(e.target.value) })}>
                    {durations.map((d) => <option key={d} value={d}>{d % 60 ? `${Math.floor(d / 60) ? `${Math.floor(d / 60)}시간 ` : ''}30분` : `${d / 60}시간`}</option>)}
                  </select>
                </label>
              </div>
            )}

            <label className="block text-sm font-medium text-gray-700">
              이용 인원
              <select className={selectClass} value={headcount} onChange={(e) => set({ headcount: Number(e.target.value) })}>
                {Array.from({ length: room?.capacity ?? 1 }, (_, i) => i + 1).map((n) => <option key={n} value={n}>{n}명</option>)}
              </select>
              {fieldErrors.headcount && <span className="mt-1 block text-xs text-red-600">{fieldErrors.headcount}</span>}
            </label>

            {starts.length > 0 && (
              <div className="rounded-md bg-indigo-50 px-3 py-2 text-sm text-indigo-900" data-testid="summary">
                <p className="font-medium">
                  {room.name} · {dateLabel(date)} {hhmm(start)}~{hhmm(start + duration)} · {headcount}명
                </p>
                <p className="mt-1 text-xs">
                  {board.keyRequired
                    ? `시작 ${policy.keyIssueBeforeMinutes}분 전부터 학과실에서 학생증을 보여주고 키를 받아요. 시작 ${policy.noShowGraceMinutes}분이 지나도 안 받으면 노쇼 처리돼요.`
                    : `키 없이 이용하는 기간이에요. 시작 ${policy.keyIssueBeforeMinutes}분 전부터 ${policy.noShowGraceMinutes}분 후까지 내 예약에서 "이용 시작"을 눌러 주세요.`}
                </p>
              </div>
            )}

            <Alert>{error}</Alert>
            <button type="submit" disabled={submitting || starts.length === 0}
              className="w-full rounded-md bg-indigo-600 py-2.5 font-medium text-white disabled:opacity-50">
              {submitting ? '예약 중…' : '예약하기'}
            </button>
            <p className="text-center text-xs text-gray-500">
              한 번에 최대 {policy.maxDurationMinutes / 60}시간, 하루 {policy.dailyLimit}건까지 예약할 수 있어요.
            </p>
          </>
        )}
      </form>
      <p className="mt-4 text-center text-sm">
        <Link to="/" className="text-indigo-600 underline">현황판으로</Link>
      </p>
    </section>
  )
}
