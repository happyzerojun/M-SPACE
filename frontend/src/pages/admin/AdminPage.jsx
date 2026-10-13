import { NavLink, Route, Routes } from 'react-router'
import AdminUsersPage from './AdminUsersPage.jsx'

const tabClass = ({ isActive }) =>
  `whitespace-nowrap rounded-md px-3 py-1.5 text-sm ${isActive ? 'bg-gray-900 text-white' : 'text-gray-600 hover:bg-gray-100'}`

/** 관리자 화면 (React.lazy로 따로 불러와요). 탭은 단계마다 늘어나요: 예약·키(3~4단계), 운영 설정(5단계) */
export default function AdminPage() {
  return (
    <section>
      <h1 className="text-xl font-bold">관리자</h1>
      <nav className="mt-3 flex flex-wrap gap-1">
        <NavLink to="/admin" end className={tabClass}>
          회원
        </NavLink>
      </nav>
      <div className="mt-4">
        <Routes>
          <Route index element={<AdminUsersPage />} />
        </Routes>
      </div>
    </section>
  )
}
