import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { errorMessage } from '../api/client'
import { fetchPolicy } from '../api/board'
import { cancelReservation, checkIn, fetchMyReservations, finishUse } from '../api/reservations'
import Alert from '../components/Alert.jsx'
import ReservationDone from '../components/ReservationDone.jsx'
import SurveyCard from '../components/SurveyCard.jsx'
import { useAsync } from '../hooks/useAsync'
import { useNow } from '../hooks/useNow'
import { roomGradient } from '../utils/slots'
import { StatusBadge } from '../utils/status.jsx'
import { dateLabel, kstToday, timeOf, toMs } from '../utils/time'

/** 내 예약 (RV-09, RV-10, OP-02, OP-03): 예정·지난 예약, 취소, 개방 기간 이용 시작·종료 */
export default function MyReservationsPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const justReserved = location.state?.justReserved ?? null
  const [scope, setScope] = useState('upcoming')
  const [version, setVersion] = useState(0)
  const [message, setMessage] = useState(location.state?.message ?? null)
  const { data, error, loading } = useAsync(() => fetchMyReservations(scope), `${scope}|${version}`)
  const { data: policy } = useAsync(fetchPolicy, 'policy')
  const now = useNow(30_000)

  const tab = (value, label) => (
    <button type="button" onClick={() => setScope(value)} aria-pressed={scope === value}
      className={`flex-1 rounded-lg py-2 text-sm ${scope === value ? 'bg-white font-semibold shadow-sm' : 'text-gray-600'}`}>
      {label}
    </button>
  )

  return (
    <section>
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-bold">내 예약</h1>
        <Link to="/" className="rounded-full bg-indigo-600 px-4 py-2 text-sm font-semibold text-white">새 예약</Link>
      </div>
      <div className="mt-4 flex gap-1 rounded-xl bg-gray-200/60 p-1">
        {tab('upcoming', '예정')}
        {tab('past', '지난 예약')}
      </div>
      <div className="mt-4 space-y-3">
        {justReserved && <ReservationDone r={justReserved} policy={policy} />}
        <Alert kind="success">{message}</Alert>
        {justReserved && <SurveyCard />}
        <Alert>{error}</Alert>
        {loading && !data && <p className="text-sm text-gray-500">불러오는 중이에요…</p>}
        {data && data.content.length === 0 && (
          <p className="py-8 text-center text-sm text-gray-500">
            {scope === 'upcoming' ? '예정된 예약이 없어요.' : '지난 예약이 없어요.'}
          </p>
        )}
        <ul className="space-y-3">
          {data?.content.map((r) => (
            <ReservationCard key={r.id} r={r} now={now} policy={policy}
              onChanged={(msg) => {
                setMessage(msg)
                setVersion((v) => v + 1)
                // 취소 등을 하면 예약 완료 카드는 더 이상 맞지 않아서 내려요
                if (justReserved) navigate(location.pathname, { replace: true, state: null })
              }} />
          ))}
        </ul>
      </div>
    </section>
  )
}

function ReservationCard({ r, now, policy, onChanged }) {
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)
  const start = toMs(r.startAt)
  const before = (policy?.keyIssueBeforeMinutes ?? 10) * 60_000
  const grace = (policy?.noShowGraceMinutes ?? 15) * 60_000
  const canCancel = r.status === 'RESERVED' && now < start
  const canCheckIn = !r.keyRequired && r.status === 'RESERVED' && now >= start - before && now < start + grace
  const canFinish = !r.keyRequired && r.status === 'IN_USE'
  const date = kstToday(start)

  async function run(action, message, confirmText) {
    if (confirmText && !window.confirm(confirmText)) return
    setBusy(true)
    setError(null)
    try {
      await action(r.id)
      onChanged(message)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <li className="overflow-hidden rounded-2xl bg-white shadow-sm ring-1 ring-gray-100" data-reservation={r.id}>
      <div className={`h-1.5 bg-gradient-to-r ${roomGradient(r.room.id)}`} />
      <div className="p-4">
      <div className="flex items-start justify-between gap-2">
        <div>
          <p className="font-semibold">{r.room.name}</p>
          <p className="text-sm tabular-nums text-gray-700">
            {dateLabel(date)} {timeOf(r.startAt)}~{timeOf(r.endAt)} · {r.headcount}명
          </p>
        </div>
        <StatusBadge status={r.displayStatus} />
      </div>
      {r.status === 'RESERVED' && (
        <p className="mt-2 text-xs text-gray-600">
          {r.keyRequired
            ? '시작 10분 전부터 학과실에서 학생증을 보여주고 키를 받아요.'
            : '키 없이 이용해요. 시작 10분 전부터 15분 후까지 "이용 시작"을 눌러 주세요.'}
        </p>
      )}
      {r.cancelReason && <p className="mt-2 text-xs text-gray-500">취소 사유: {r.cancelReason}</p>}
      <Alert>{error}</Alert>
      {(canCancel || canCheckIn || canFinish) && (
        <div className="mt-3 flex flex-wrap gap-2">
          {canCheckIn && (
            <button type="button" disabled={busy} onClick={() => run(checkIn, '이용을 시작했어요.')}
              className="rounded-md bg-green-600 px-3 py-1.5 text-sm font-medium text-white">이용 시작</button>
          )}
          {canFinish && (
            <button type="button" disabled={busy} onClick={() => run(finishUse, '이용을 마쳤어요. 남은 시간은 다른 사람이 예약할 수 있어요.', '이용을 마칠까요?')}
              className="rounded-md bg-gray-900 px-3 py-1.5 text-sm font-medium text-white">이용 종료</button>
          )}
          {canCancel && (
            <button type="button" disabled={busy} onClick={() => run(cancelReservation, '예약을 취소했어요.', '이 예약을 취소할까요?')}
              className="rounded-md border border-gray-300 px-3 py-1.5 text-sm">예약 취소</button>
          )}
        </div>
      )}
      </div>
    </li>
  )
}
