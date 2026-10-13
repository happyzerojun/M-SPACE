import { NavLink } from 'react-router'
import { useAuth } from '../auth/useAuth'

const ICONS = {
  home: 'M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1v-9.5Z',
  list: 'M8 6h13M8 12h13M8 18h13M3.5 6h.01M3.5 12h.01M3.5 18h.01',
  admin: 'M12 3 4 6v6c0 4.4 3.4 8.3 8 9 4.6-.7 8-4.6 8-9V6l-8-3Z',
  user: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm-7 9c0-3.9 3.1-7 7-7s7 3.1 7 7',
}

function Tab({ to, end, icon, label }) {
  return (
    <NavLink to={to} end={end}
      className={({ isActive }) => `flex min-w-0 flex-1 flex-col items-center gap-0.5 py-2 text-[11px] ${isActive ? 'text-indigo-600' : 'text-gray-500'}`}>
      <svg aria-hidden="true" viewBox="0 0 24 24" className="h-6 w-6 fill-none stroke-current" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <path d={ICONS[icon]} />
      </svg>
      <span className="max-w-full truncate">{label}</span>
    </NavLink>
  )
}

/** 앱처럼 화면 아래에 고정한 메뉴. 휴대폰 엄지로 누르기 쉬워요 */
export default function BottomNav() {
  const { user, ready } = useAuth()
  return (
    <nav aria-label="주 메뉴" className="fixed inset-x-0 bottom-0 z-20 border-t border-gray-200 bg-white/95 pb-[env(safe-area-inset-bottom)] backdrop-blur">
      <div className="mx-auto flex max-w-3xl">
        <Tab to="/" end icon="home" label="홈" />
        {ready && user && <Tab to="/me/reservations" icon="list" label="내 예약" />}
        {ready && user?.role === 'ADMIN' && <Tab to="/admin" icon="admin" label="관리" />}
        {ready && user && <Tab to="/me" end icon="user" label={user.name} />}
        {ready && !user && <Tab to="/login" icon="user" label="로그인" />}
      </div>
    </nav>
  )
}
