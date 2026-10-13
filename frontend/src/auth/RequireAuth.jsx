import { Navigate, useLocation } from 'react-router'
import { useAuth } from './useAuth'

/**
 * 로그인이 필요한 화면. 비회원은 로그인 화면으로, 임시 비밀번호 상태면 비밀번호 변경 화면으로 보내요 (AU-07).
 * admin이면 관리자만 들어와요. 화면을 막는 건 편의일 뿐이고 실제 권한은 API가 다시 확인해요 (AD-01).
 */
export default function RequireAuth({ children, admin = false }) {
  const { user, ready } = useAuth()
  const location = useLocation()

  if (!ready) return <p className="text-sm text-gray-500">불러오는 중이에요…</p>
  if (!user) {
    const next = encodeURIComponent(location.pathname + location.search)
    return <Navigate to={`/login?next=${next}`} replace />
  }
  if (user.mustChangePassword && location.pathname !== '/me/password') {
    return <Navigate to="/me/password" replace />
  }
  if (admin && user.role !== 'ADMIN') {
    return (
      <section className="py-12 text-center">
        <h1 className="text-xl font-bold">관리자만 볼 수 있는 화면이에요.</h1>
      </section>
    )
  }
  return children
}
