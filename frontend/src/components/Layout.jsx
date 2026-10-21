import { Link, Outlet } from 'react-router'
import BottomNav from './BottomNav.jsx'
import DemoBanner from './DemoBanner.jsx'

/** 모바일 우선 레이아웃: 위는 로고, 아래는 고정 메뉴. 375px에서 가로 스크롤이 생기지 않아요 (BD-04, NF-08) */
export default function Layout() {
  return (
    <div className="min-h-dvh bg-slate-50">
      <DemoBanner />
      <header className="border-b border-gray-100 bg-white">
        <div className="mx-auto flex max-w-3xl items-center justify-between px-4 py-3">
          <Link to="/" className="text-lg font-extrabold tracking-tight text-indigo-600">
            M-SPACE
          </Link>
          <span className="text-xs text-gray-400">IT융합대학 스터디룸</span>
        </div>
      </header>
      <main className="mx-auto max-w-3xl px-4 pb-28 pt-5">
        <Outlet />
        <footer className="mt-10 text-center text-xs text-gray-400">
          <Link to="/privacy" className="underline">개인정보 처리방침</Link>
        </footer>
      </main>
      <BottomNav />
    </div>
  )
}
