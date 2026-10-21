import { useState } from 'react'
import { fetchRestrictions, liftRestriction } from '../../api/adminRestrictions'
import { errorMessage } from '../../api/client'
import Alert from '../../components/Alert.jsx'
import { useAsync } from '../../hooks/useAsync'
import { formatDateTime } from '../../utils/format'

/** NS-03 노쇼 누적 이용 제한 목록·해제. 해제 사유는 감사 기록에 남아요 (AD-08) */
export default function AdminRestrictionsPage() {
  const [activeOnly, setActiveOnly] = useState(true)
  const [page, setPage] = useState(1)
  const [version, setVersion] = useState(0)
  const { data, error, loading } = useAsync(() => fetchRestrictions(activeOnly, page), `${activeOnly}|${page}|${version}`)

  return (
    <div>
      <div className="flex items-center justify-between gap-2">
        <p className="text-sm text-gray-600">2주 안에 노쇼 2번이면 7일 동안 예약할 수 없어요. 사정이 확인되면 해제해요.</p>
        <select aria-label="제한 상태" value={String(activeOnly)}
          onChange={(e) => { setActiveOnly(e.target.value === 'true'); setPage(1) }}
          className="rounded-md border border-gray-300 px-2 py-2 text-sm">
          <option value="true">진행 중</option>
          <option value="false">전체</option>
        </select>
      </div>
      <div className="mt-4">
        <Alert>{error}</Alert>
        {loading && <p className="text-sm text-gray-500">불러오는 중이에요…</p>}
        {!loading && data && (
          <>
            <p className="text-xs text-gray-500">{data.totalElements}건</p>
            <ul className="mt-2 space-y-3">
              {data.content.map((r) => (
                <RestrictionCard key={r.id} restriction={r} onChanged={() => setVersion((v) => v + 1)} />
              ))}
            </ul>
            {data.totalPages > 1 && (
              <div className="mt-4 flex items-center justify-center gap-3 text-sm">
                <button type="button" disabled={page <= 1} onClick={() => setPage(page - 1)}
                  className="rounded border px-3 py-1 disabled:opacity-40">이전</button>
                <span>{page} / {data.totalPages}</span>
                <button type="button" disabled={page >= data.totalPages} onClick={() => setPage(page + 1)}
                  className="rounded border px-3 py-1 disabled:opacity-40">다음</button>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}

function RestrictionCard({ restriction: r, onChanged }) {
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState('')
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  async function lift(e) {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await liftRestriction(r.id, reason)
      onChanged()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <li className="rounded-lg border border-gray-200 bg-white p-3" data-restriction-id={r.id}>
      <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
        <p className="font-medium">
          {r.studentName} <span className="text-sm text-gray-500">{r.studentNo}</span>
        </p>
        {r.active
          ? <span className="rounded bg-red-100 px-1.5 py-0.5 text-xs text-red-700">제한 중</span>
          : <span className="rounded bg-gray-200 px-1.5 py-0.5 text-xs">{r.liftedAt ? '해제됨' : '종료'}</span>}
      </div>
      <p className="mt-1 text-sm text-gray-600">
        {formatDateTime(r.startAt)} ~ {formatDateTime(r.endAt)} · {r.reason}
      </p>
      {r.liftedAt && <p className="mt-1 text-xs text-gray-500">해제 {formatDateTime(r.liftedAt)} · {r.liftReason}</p>}

      {r.active && !open && (
        <button type="button" onClick={() => setOpen(true)} className="mt-2 rounded-md border border-gray-300 px-3 py-1.5 text-sm">
          제한 해제
        </button>
      )}
      {open && (
        <form onSubmit={lift} className="mt-2 space-y-2">
          <label className="block text-sm">
            해제 사유
            <input value={reason} onChange={(e) => setReason(e.target.value)} maxLength={200} required
              className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2" />
          </label>
          <Alert>{error}</Alert>
          <div className="flex gap-2">
            <button type="submit" disabled={submitting || !reason.trim()}
              className="rounded-md bg-gray-900 px-3 py-1.5 text-sm text-white disabled:opacity-50">해제</button>
            <button type="button" onClick={() => { setOpen(false); setError(null) }} className="px-3 py-1.5 text-sm text-gray-600">
              취소
            </button>
          </div>
        </form>
      )}
    </li>
  )
}
