import { NavLink, Outlet } from 'react-router'
import { useAuth } from '../auth/useAuth'

const navClass = ({ isActive }) =>
  `whitespace-nowrap rounded-md px-2.5 py-2 text-sm font-medium ${isActive ? 'bg-indigo-50 text-indigo-700' : 'text-gray-600 hover:bg-gray-100'}`

/** 모바일 우선 레이아웃: 375px에서 가로 스크롤이 생기지 않게 폭을 고정하지 않아요 (BD-04, NF-08) */
export default function Layout() {
  const { user, ready } = useAuth()

  return (
    <div className="min-h-dvh">
      <header className="sticky top-0 z-10 border-b border-gray-200 bg-white">
        <div className="mx-auto flex max-w-3xl items-center justify-between gap-2 px-4 py-3">
          <NavLink to="/" className="text-lg font-bold text-indigo-700">
            M-SPACE
          </NavLink>
          <nav className="flex gap-0.5">
            <NavLink to="/" end className={navClass}>
              현황
            </NavLink>
            {ready && user && (
              <NavLink to="/me/reservations" className={navClass}>
                내 예약
              </NavLink>
            )}
            {ready && user?.role === 'ADMIN' && (
              <NavLink to="/admin" className={navClass}>
                관리
              </NavLink>
            )}
            {ready && user && (
              <NavLink to="/me" end className={(s) => `${navClass(s)} max-w-24 truncate`} aria-label="내 정보" title={user.name}>
                {user.name}
              </NavLink>
            )}
            {ready && !user && (
              <NavLink to="/login" className={navClass}>
                로그인
              </NavLink>
            )}
          </nav>
        </div>
      </header>
      <main className="mx-auto max-w-3xl px-4 py-6">
        <Outlet />
      </main>
    </div>
  )
}
