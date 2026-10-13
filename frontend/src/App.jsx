import { lazy, Suspense } from 'react'
import { Route, Routes } from 'react-router'
import RequireAuth from './auth/RequireAuth.jsx'
import Layout from './components/Layout.jsx'
import BoardPage from './pages/BoardPage.jsx'
import LoginPage from './pages/LoginPage.jsx'
import MePage from './pages/MePage.jsx'
import MyReservationsPage from './pages/MyReservationsPage.jsx'
import NotFoundPage from './pages/NotFoundPage.jsx'
import PasswordChangePage from './pages/PasswordChangePage.jsx'
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
        <Route path="me" element={<RequireAuth><MePage /></RequireAuth>} />
        <Route path="me/password" element={<RequireAuth><PasswordChangePage /></RequireAuth>} />
        <Route path="me/reservations" element={<RequireAuth><MyReservationsPage /></RequireAuth>} />
        <Route
          path="admin/*"
          element={
            <RequireAuth admin>
              <Suspense fallback={<p className="text-sm text-gray-500">불러오는 중이에요…</p>}>
                <AdminPage />
              </Suspense>
            </RequireAuth>
          }
        />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
