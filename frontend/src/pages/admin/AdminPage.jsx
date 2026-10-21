import { NavLink, Route, Routes } from 'react-router'
import AdminRestrictionsPage from './AdminRestrictionsPage.jsx'
import AdminTodayPage from './AdminTodayPage.jsx'
import AdminUsersPage from './AdminUsersPage.jsx'

const tabClass = ({ isActive }) =>
  `whitespace-nowrap rounded-md px-3 py-1.5 text-sm ${isActive ? 'bg-gray-900 text-white' : 'text-gray-600 hover:bg-gray-100'}`

/** 관리자 화면 (React.lazy로 따로 불러와요). 운영 설정 탭은 5단계에서 추가해요 */
export default function AdminPage() {
  return (
    <section>
      <h1 className="text-xl font-bold">관리자</h1>
      <nav className="mt-3 flex flex-wrap gap-1">
        <NavLink to="/admin" end className={tabClass}>예약·키</NavLink>
        <NavLink to="/admin/users" className={tabClass}>회원</NavLink>
        <NavLink to="/admin/restrictions" className={tabClass}>이용 제한</NavLink>
      </nav>
      <div className="mt-4">
        <Routes>
          <Route index element={<AdminTodayPage />} />
          <Route path="users" element={<AdminUsersPage />} />
          <Route path="restrictions" element={<AdminRestrictionsPage />} />
        </Routes>
      </div>
    </section>
  )
}
