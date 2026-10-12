import { lazy, Suspense } from 'react'
import { Route, Routes } from 'react-router'
import Layout from './components/Layout.jsx'
import BoardPage from './pages/BoardPage.jsx'
import LoginPage from './pages/LoginPage.jsx'
import MyReservationsPage from './pages/MyReservationsPage.jsx'
import NotFoundPage from './pages/NotFoundPage.jsx'
import SignupPage from './pages/SignupPage.jsx'

// 관리자 화면은 학생 대부분이 받지 않아도 되게 따로 불러요 (CLAUDE.md 7장)
const AdminPage = lazy(() => import('./pages/admin/AdminPage.jsx'))

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<BoardPage />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="signup" element={<SignupPage />} />
        <Route path="me/reservations" element={<MyReservationsPage />} />
        <Route
          path="admin/*"
          element={
            <Suspense fallback={<p className="p-4 text-sm text-gray-500">불러오는 중이에요…</p>}>
              <AdminPage />
            </Suspense>
          }
        />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
