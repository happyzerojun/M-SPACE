import { useState } from 'react'
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router'
import { fetchBoard, fetchPolicy } from '../api/board'
import { errorMessage } from '../api/client'
import { createReservation } from '../api/reservations'
import { useAuth } from '../auth/useAuth'
import Alert from '../components/Alert.jsx'
import DateChips from '../components/DateChips.jsx'
import RoomVisual from '../components/RoomVisual.jsx'
import { useAsync } from '../hooks/useAsync'
import { useNow } from '../hooks/useNow'
import { durationLabel, freeLengthFrom, roomDay } from '../utils/slots'
import { dateLabel, hhmm, isoAt, kstToday, minuteOf } from '../utils/time'
import { DayNotice } from './BoardPage.jsx'

/**
 * 방 상세·예약 (RV-01~08). 날짜 → 시작 시각 → (이용 시간·인원은 기본값) → 아래 고정 "예약하기".
 * 고른 값은 주소(?date&start&duration&headcount)에 남겨서, 로그인하고 돌아와도 그대로예요.
 * 화면은 빈 시간만 고르게 도와주고, 최종 판단(겹침·한도·운영 시간)은 서버가 해요.
 */
export default function RoomPage() {
  const { roomId } = useParams()
  const [params, setParams] = useSearchParams()
  const location = useLocation()
  const navigate = useNavigate()
  const { user } = useAuth()
  const now = useNow(30_000)
  const today = kstToday(now)
  const date = params.get('date') ?? today
  const { data: policy } = useAsync(fetchPolicy, 'policy')
  const { data: board, error: loadError } = useAsync(() => fetchBoard(date), date)
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  const ready = board?.date === date && policy
  const room = ready ? board.rooms.find((r) => r.id === Number(roomId)) : null
  const nowMinute = date === today ? minuteOf(new Date(now).toISOString(), today) : 0
  const day = roomDay(board, room, nowMinute)
  const maxMinutes = policy?.maxDurationMinutes ?? 120

  const startParam = params.get('start')
  const start = startParam !== null && day.slots.some((s) => s.start === Number(startParam) && s.state === 'free') ? Number(startParam) : null
  const available = start === null ? 0 : Math.min(freeLengthFrom(day.slots, start), maxMinutes)
  const durations = Array.from({ length: maxMinutes / 30 }, (_, i) => (i + 1) * 30)
  // 이용 시간은 고르지 않으면 가능한 가장 긴 시간(최대 2시간)으로 미리 정해 둬요 → 시작 시각만 누르면 돼요
  const duration = Math.min(Number(params.get('duration')) || maxMinutes, available)
  const capacity = room?.capacity ?? 1
  const headcount = Math.min(Math.max(Number(params.get('headcount')) || Math.min(2, capacity), 1), capacity)

  const update = (patch) => {
    const next = new URLSearchParams(params)
    Object.entries(patch).forEach(([k, v]) => (v === null ? next.delete(k) : next.set(k, String(v))))
    setParams(next, { replace: true })
    setError(null)
  }

  async function reserve() {
    if (!user) {
      navigate(`/login?next=${encodeURIComponent(location.pathname + location.search)}`)
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      await createReservation({ roomId: room.id, headcount, startAt: isoAt(date, start), endAt: isoAt(date, start + duration) })
      navigate('/me/reservations', { state: { message: '예약했어요.', justReserved: true } })
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  if (loadError) return <Alert>{loadError}</Alert>
  if (!ready) return <p className="text-sm text-gray-500">불러오는 중이에요…</p>
  if (!room) return <Alert>방을 찾을 수 없어요.</Alert>

  return (
    <section className="space-y-5 pb-24">
      <Link to={`/?date=${date}`} className="inline-flex items-center gap-1 text-sm text-gray-500">‹ 전체 방</Link>
      <div className="overflow-hidden rounded-2xl shadow-sm">
        <RoomVisual room={room} size="lg" />
      </div>
      <h1 className="sr-only">{room.name} 예약</h1>
      <ul className="grid grid-cols-3 gap-2 text-center text-xs text-gray-600">
        <li className="rounded-xl bg-white py-2 ring-1 ring-gray-100"><b className="block text-sm text-gray-900">최대 {maxMinutes / 60}시간</b>1회 예약</li>
        <li className="rounded-xl bg-white py-2 ring-1 ring-gray-100"><b className="block text-sm text-gray-900">하루 {policy.dailyLimit}건</b>1인 기준</li>
        <li className="rounded-xl bg-white py-2 ring-1 ring-gray-100"><b className="block text-sm text-gray-900">30분</b>단위</li>
      </ul>

      <div className="space-y-2">
        <h2 className="text-sm font-semibold text-gray-700">날짜</h2>
        <DateChips today={today} days={policy.bookingWindowDays} value={date}
          onChange={(d) => update({ date: d, start: null })} />
      </div>
      <DayNotice board={board} />

      {!board.closed && (
        <div className="space-y-2">
          <h2 className="text-sm font-semibold text-gray-700">시작 시간</h2>
          {day.slots.every((s) => s.state !== 'free') ? (
            <Alert kind="info">{dateLabel(date)}에는 남은 빈 시간이 없어요. 다른 날짜나 방을 골라 주세요.</Alert>
          ) : (
            <div className="grid grid-cols-4 gap-2" role="group" aria-label="시작 시간">
              {day.slots.filter((s) => s.state !== 'past').map((s) => {
                const inRange = start !== null && s.start >= start && s.start < start + duration
                const isStart = s.start === start
                return (
                  <button key={s.start} type="button" disabled={s.state !== 'free'} aria-pressed={isStart}
                    onClick={() => update({ start: s.start })}
                    className={`rounded-xl py-2 text-sm tabular-nums transition ${
                      s.state === 'booked' ? 'cursor-not-allowed bg-gray-100 text-gray-300 line-through'
                        : isStart ? 'bg-indigo-600 font-semibold text-white shadow-sm'
                          : inRange ? 'bg-indigo-100 font-medium text-indigo-700'
                            : 'bg-white text-gray-800 ring-1 ring-gray-200'
                    }`}>
                    {hhmm(s.start)}
                    {s.state === 'booked' && <span className="sr-only"> 예약됨</span>}
                  </button>
                )
              })}
            </div>
          )}
          <p className="text-xs text-gray-400">회색 칸은 이미 예약된 시간이에요.</p>
        </div>
      )}

      {start !== null && (
        <>
          <div className="space-y-2">
            <h2 className="text-sm font-semibold text-gray-700">이용 시간</h2>
            <div className="grid grid-cols-4 gap-2" role="group" aria-label="이용 시간">
              {durations.map((d) => (
                <button key={d} type="button" disabled={d > available} aria-pressed={d === duration}
                  onClick={() => update({ duration: d })}
                  className={`rounded-xl py-2 text-sm ${
                    d === duration ? 'bg-indigo-600 font-semibold text-white' : d > available ? 'bg-gray-100 text-gray-300' : 'bg-white ring-1 ring-gray-200'
                  }`}>
                  {durationLabel(d)}
                </button>
              ))}
            </div>
          </div>
          <div className="flex items-center justify-between rounded-2xl bg-white px-4 py-3 ring-1 ring-gray-100">
            <h2 className="text-sm font-semibold text-gray-700">이용 인원</h2>
            <div className="flex items-center gap-3">
              <button type="button" aria-label="인원 줄이기" disabled={headcount <= 1} onClick={() => update({ headcount: headcount - 1 })}
                className="h-9 w-9 rounded-full bg-gray-100 text-lg disabled:opacity-40">−</button>
              <span className="w-10 text-center font-semibold" data-testid="headcount">{headcount}명</span>
              <button type="button" aria-label="인원 늘리기" disabled={headcount >= capacity} onClick={() => update({ headcount: headcount + 1 })}
                className="h-9 w-9 rounded-full bg-gray-100 text-lg disabled:opacity-40">+</button>
            </div>
          </div>
        </>
      )}

      {/* 아래 고정 예약 바 (아래 메뉴를 덮어요) */}
      <div className="fixed inset-x-0 bottom-0 z-30 border-t border-gray-200 bg-white pb-[env(safe-area-inset-bottom)]">
        <div className="mx-auto max-w-3xl px-4 py-3">
          {error && <div className="mb-2"><Alert>{error}</Alert></div>}
          <div className="flex items-center gap-3">
            <div className="min-w-0 flex-1" data-testid="summary">
              {start === null ? (
                <p className="text-sm text-gray-500">시작 시간을 골라 주세요</p>
              ) : (
                <>
                  <p className="truncate text-sm font-semibold">{room.name} · {headcount}명</p>
                  <p className="truncate text-xs text-gray-500 tabular-nums">{dateLabel(date)} {hhmm(start)}~{hhmm(start + duration)}</p>
                </>
              )}
            </div>
            <button type="button" onClick={reserve} disabled={start === null || submitting}
              className="shrink-0 rounded-xl bg-indigo-600 px-6 py-3 font-semibold text-white shadow-sm disabled:bg-gray-300">
              {submitting ? '예약 중…' : '예약하기'}
            </button>
          </div>
          {start !== null && (
            <p className="mt-1.5 text-[11px] text-gray-400">
              {board.keyRequired
                ? `시작 ${policy.keyIssueBeforeMinutes}분 전부터 학과실에서 학생증 확인 후 키를 받아요. ${policy.noShowGraceMinutes}분 지나도 안 받으면 노쇼예요.`
                : `시작 ${policy.keyIssueBeforeMinutes}분 전부터 ${policy.noShowGraceMinutes}분 후까지 내 예약에서 "이용 시작"을 눌러요.`}
            </p>
          )}
        </div>
      </div>
    </section>
  )
}
