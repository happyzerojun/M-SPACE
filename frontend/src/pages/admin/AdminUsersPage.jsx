import { useState } from 'react'
import { disableUser, resetPassword, searchUsers } from '../../api/adminUsers'
import { errorMessage } from '../../api/client'
import { useAuth } from '../../auth/useAuth'
import Alert from '../../components/Alert.jsx'
import { useAsync } from '../../hooks/useAsync'
import { formatDateTime } from '../../utils/format'

/** AD-07 회원 검색, AU-07 비밀번호 초기화, AU-08 계정 비활성화. 휴대폰에서도 쓰게 표 대신 카드 목록이에요 */
export default function AdminUsersPage() {
  const [input, setInput] = useState('')
  const [query, setQuery] = useState({ q: '', status: 'ACTIVE', page: 1 })
  const [version, setVersion] = useState(0)
  const key = `${query.q}|${query.status}|${query.page}|${version}`
  const { data, error, loading } = useAsync(
    () => searchUsers({ q: query.q || undefined, status: query.status || undefined, page: query.page }),
    key,
  )

  function search(e) {
    e.preventDefault()
    setQuery({ ...query, q: input.trim(), page: 1 })
  }

  return (
    <div>
      <form onSubmit={search} className="flex flex-wrap gap-2">
        <input
          aria-label="학번 또는 이름"
          placeholder="학번 앞부분 또는 이름"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          className="min-w-0 flex-1 rounded-md border border-gray-300 px-3 py-2"
        />
        <select
          aria-label="계정 상태"
          value={query.status}
          onChange={(e) => setQuery({ ...query, status: e.target.value, page: 1 })}
          className="rounded-md border border-gray-300 px-2 py-2 text-sm"
        >
          <option value="ACTIVE">활성</option>
          <option value="DISABLED">비활성</option>
          <option value="">전체</option>
        </select>
        <button type="submit" className="rounded-md bg-gray-900 px-4 py-2 text-sm text-white">
          검색
        </button>
      </form>

      <div className="mt-4">
        <Alert>{error}</Alert>
        {loading && <p className="text-sm text-gray-500">불러오는 중이에요…</p>}
        {!loading && data && (
          <>
            <p className="text-xs text-gray-500">{data.totalElements}명</p>
            <ul className="mt-2 space-y-3">
              {data.content.map((u) => (
                <UserCard key={u.id} user={u} onChanged={() => setVersion((v) => v + 1)} />
              ))}
            </ul>
            {data.totalPages > 1 && (
              <div className="mt-4 flex items-center justify-center gap-3 text-sm">
                <button type="button" disabled={query.page <= 1}
                  onClick={() => setQuery({ ...query, page: query.page - 1 })} className="rounded border px-3 py-1 disabled:opacity-40">
                  이전
                </button>
                <span>{query.page} / {data.totalPages}</span>
                <button type="button" disabled={query.page >= data.totalPages}
                  onClick={() => setQuery({ ...query, page: query.page + 1 })} className="rounded border px-3 py-1 disabled:opacity-40">
                  다음
                </button>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}

function UserCard({ user, onChanged }) {
  const { user: me } = useAuth()
  const [action, setAction] = useState(null) // 'reset' | 'disable'
  const [reason, setReason] = useState('')
  const [error, setError] = useState(null)
  const [tempPassword, setTempPassword] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const active = user.status === 'ACTIVE'

  async function confirm(e) {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      if (action === 'reset') {
        setTempPassword((await resetPassword(user.id, reason)).temporaryPassword)
      } else {
        await disableUser(user.id, reason)
        onChanged()
      }
      setAction(null)
      setReason('')
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <li className="rounded-lg border border-gray-200 bg-white p-3" data-student-no={user.studentNo}>
      <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
        <p className="font-medium">
          {user.name} <span className="text-sm text-gray-500">{user.studentNo}</span>
        </p>
        <div className="flex flex-wrap gap-1 text-xs">
          {user.role === 'ADMIN' && <span className="rounded bg-gray-900 px-1.5 py-0.5 text-white">관리자</span>}
          {!active && <span className="rounded bg-gray-200 px-1.5 py-0.5">비활성</span>}
          {user.mustChangePassword && <span className="rounded bg-amber-100 px-1.5 py-0.5 text-amber-800">임시 비밀번호</span>}
          {user.restrictedUntil && (
            <span className="rounded bg-red-100 px-1.5 py-0.5 text-red-700">제한 ~{formatDateTime(user.restrictedUntil)}</span>
          )}
        </div>
      </div>
      <p className="mt-1 text-sm text-gray-600">
        {user.phone} · 최근 노쇼 {user.recentNoShowCount}회
      </p>

      {tempPassword && (
        <div className="mt-2 rounded-md border border-amber-300 bg-amber-50 p-2 text-sm">
          <p>
            임시 비밀번호: <strong className="font-mono text-base tracking-wider" data-testid="temp-password">{tempPassword}</strong>
          </p>
          <p className="mt-1 text-xs text-amber-800">이 화면에서만 보여요. 학생에게 직접 알려 주세요. 다음 로그인 때 바꾸게 돼요.</p>
        </div>
      )}

      {active && action === null && (
        <div className="mt-2 flex flex-wrap gap-2">
          <button type="button" onClick={() => { setAction('reset'); setTempPassword(null) }}
            className="rounded-md border border-gray-300 px-3 py-1.5 text-sm">
            비밀번호 초기화
          </button>
          {user.id !== me.id && (
            <button type="button" onClick={() => setAction('disable')}
              className="rounded-md border border-red-300 px-3 py-1.5 text-sm text-red-700">
              비활성화
            </button>
          )}
        </div>
      )}

      {action && (
        <form onSubmit={confirm} className="mt-2 space-y-2">
          <label className="block text-sm">
            {action === 'reset' ? '초기화 사유 (학생증 확인 등)' : '비활성화 사유'}
            <input value={reason} onChange={(e) => setReason(e.target.value)} maxLength={200} required
              className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2" />
          </label>
          <Alert>{error}</Alert>
          <div className="flex gap-2">
            <button type="submit" disabled={submitting || !reason.trim()}
              className={`rounded-md px-3 py-1.5 text-sm text-white disabled:opacity-50 ${action === 'reset' ? 'bg-gray-900' : 'bg-red-600'}`}>
              {action === 'reset' ? '초기화' : '비활성화'}
            </button>
            <button type="button" onClick={() => { setAction(null); setError(null) }} className="px-3 py-1.5 text-sm text-gray-600">
              취소
            </button>
          </div>
        </form>
      )}
    </li>
  )
}
