// 0단계 레이아웃 점검: 모든 화면이 375px에서 가로 스크롤 없이 열리는지 (BD-04, NF-08)
import { join } from 'node:path'
import { launch } from './cdp.mjs'
import { BASE, SHOTS, check } from './common.mjs'

const PAGES = [
  ['/', '어느 방에서 공부할까요?'],
  ['/login', '로그인'],
  ['/signup', '회원가입'],
  ['/me/reservations', '로그인'],   // 비회원은 로그인 화면으로 가요
  ['/admin', '로그인'],
  ['/no-such-page', '페이지를 찾을 수 없어요.'],
]

const page = await launch()
try {
  await page.viewport(375, 740)
  for (const [path, heading] of PAGES) {
    await page.goto(BASE + path)
    await page.waitFor(`document.querySelector('h1')?.textContent === ${JSON.stringify(heading)}`)
    const { scrollWidth, clientWidth } = await page.eval(
      '({ scrollWidth: document.documentElement.scrollWidth, clientWidth: document.documentElement.clientWidth })')
    check(scrollWidth <= clientWidth, `${path} 375px 가로 스크롤 없음`, `${scrollWidth}/${clientWidth}`)
    const navVisible = await page.eval(`[...document.querySelectorAll('nav[aria-label="주 메뉴"] a')]
      .every((a) => { const r = a.getBoundingClientRect(); return r.left >= 0 && r.right <= innerWidth })`)
    check(navVisible, `${path} 아래 메뉴가 화면 안에 다 보임`)
  }
  await page.goto(BASE + '/')
  await page.screenshot(join(SHOTS, 'layout-375.png'))
} catch (e) {
  check(false, '레이아웃 점검 실행', e.message)
} finally {
  page.close()
}
