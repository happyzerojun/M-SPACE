import { useState } from 'react'
import { adminCancel, fetchDashboard, fetchDayReservations, issueKey, returnKey } from '../../api/adminReservations'
import { errorMessage } from '../../api/client'
import Alert from '../../components/Alert.jsx'
import DateChips from '../../components/DateChips.jsx'
import { useAsync } from '../../hooks/useAsync'
import { useNow } from '../../hooks/useNow'
import { usePollingTick } from '../../hooks/usePollingTick'
import { StatusBadge } from '../../utils/status.jsx'
import { kstToday, timeOf } from '../../utils/time'

/**
 * 관리자 오늘 예약 (AD-02, AD-04, KY-01~04): 학생증 확인 후 키 지급, 반납 확인, 연체 표시, 관리자 취소.
 * 학과실 책상에서 휴대폰으로도 처리할 수 있게 카드 목록이에요.
 */
export default function AdminTodayPage() {
  const now = useNow(30_000)
  const today = kstToday(now)
  const [picked, setPicked] = useState(null)
  const date = picked ?? today
  const tick = usePollingTick(30_000)
  const [version, setVersion] = useState(0)
  const key = `${date}|${tick}|${version}`
  const { data: dash } = useAsync(() => fetchDashboard(date), key)
  const { data: list, error } = useAsync(() => fetchDayReservations(date), key)
  const refresh = () => setVersion((v) => v + 1)
  const c = dash?.counts

  return (
    <div>
      <DateChips today={today} days={1} value={date} onChange={setPicked} />
      {c && (
        <dl className="mt-3 grid grid-cols-4 gap-2 text-center">
          {[['예약', c.reserved], ['이용 중', c.inUse], ['반납 지연', c.overdue], ['노쇼', c.noShow]].map(([label, n]) => (
            <div key={label} className={`rounded-md border bg-white py-2 ${label === '반납 지연' && n > 0 ? 'border-red-300' : 'border-gray-200'}`}>
              <dt className="text-xs text-gray-500">{label}</dt>
              <dd className="text-lg font-semibold">{n}</dd>
            </div>
          ))}
        </dl>
      )}
      <div className="mt-4 space-y-3">
        <Alert>{error}</Alert>
        {list && list.content.length === 0 && <p className="py-6 text-center text-sm text-gray-500">예약이 없어요.</p>}
        <ul className="space-y-3">
          {list?.content.map((r) => <AdminReservationCard key={r.id} r={r} onChanged={refresh} />)}
        </ul>
      </div>
    </div>
  )
}

function AdminReservationCard({ r, onChanged }) {
  const [mode, setMode] = useState(null) // 'issue' | 'cancel'
  const [verified, setVerified] = useState(false)
  const [reason, setReason] = useState('')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  async function run(action) {
    setBusy(true)
    setError(null)
    try {
      await action()
      setMode(null)
      onChanged()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <li className={`rounded-lg border bg-white p-3 ${r.displayStatus === 'OVERDUE' ? 'border-red-300' : 'border-gray-200'}`} data-reservation={r.id}>
      <div className="flex items-start justify-between gap-2">
        <div>
          <p className="font-semibold">
            {r.room.name} <span className="text-sm font-normal tabular-nums text-gray-700">{timeOf(r.startAt)}~{timeOf(r.endAt)}</span>
          </p>
          <p className="text-sm text-gray-700">
            {r.user.name} · {r.user.studentNo} · {r.headcount}명
          </p>
          <p className="text-xs text-gray-500">{r.user.phone}{!r.keyRequired && ' · 개방 기간(키 없음)'}</p>
        </div>
        <StatusBadge status={r.displayStatus} />
      </div>

      <Alert>{error}</Alert>

      {mode === null && (
        <div className="mt-3 flex flex-wrap gap-2">
          {r.keyRequired && r.status === 'RESERVED' && (
            <button type="button" onClick={() => { setMode('issue'); setVerified(false) }}
              className="rounded-md bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white">키 지급</button>
          )}
          {r.keyRequired && r.status === 'IN_USE' && (
            <button type="button" disabled={busy} onClick={() => run(() => returnKey(r.id))}
              className="rounded-md bg-gray-900 px-3 py-1.5 text-sm font-medium text-white">반납 확인</button>
          )}
          {r.status === 'RESERVED' && (
            <button type="button" onClick={() => setMode('cancel')}
              className="rounded-md border border-gray-300 px-3 py-1.5 text-sm">예약 취소</button>
          )}
        </div>
      )}

      {mode === 'issue' && (
        <div className="mt-3 space-y-2 rounded-md bg-indigo-50 p-2">
          <label className="flex items-start gap-2 text-sm">
            <input type="checkbox" checked={verified} onChange={(e) => setVerified(e.target.checked)} className="mt-1" />
            <span>실물 학생증의 학번·이름이 <strong>{r.user.studentNo} {r.user.name}</strong>와 같아요. (학생증은 확인 후 돌려줘요)</span>
          </label>
          <div className="flex gap-2">
            <button type="button" disabled={!verified || busy} onClick={() => run(() => issueKey(r.id))}
              className="rounded-md bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50">본인 확인 완료 · 키 지급</button>
            <button type="button" onClick={() => setMode(null)} className="px-3 py-1.5 text-sm text-gray-600">닫기</button>
          </div>
        </div>
      )}

      {mode === 'cancel' && (
        <form className="mt-3 space-y-2" onSubmit={(e) => { e.preventDefault(); run(() => adminCancel(r.id, reason)) }}>
          <input aria-label="취소 사유" placeholder="취소 사유 (학생에게 보여요)" value={reason} maxLength={200}
            onChange={(e) => setReason(e.target.value)} className="block w-full rounded-md border border-gray-300 px-3 py-2" />
          <div className="flex gap-2">
            <button type="submit" disabled={!reason.trim() || busy}
              className="rounded-md bg-red-600 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50">취소하기</button>
            <button type="button" onClick={() => setMode(null)} className="px-3 py-1.5 text-sm text-gray-600">닫기</button>
          </div>
        </form>
      )}
    </li>
  )
}
