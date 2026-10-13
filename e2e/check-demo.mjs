// 설문용 데모 점검 (VITE_DEMO=true 빌드·개발 서버 대상, 백엔드 없음)
// 학생: 현황판 → 가입 → 예약 → 하루 한도 → 취소 → 개방 기간 이용 시작·종료 / 관리자: 키 지급 → 반납
import { join } from 'node:path'
import { launch } from './cdp.mjs'
import { BASE, SHOTS, check } from './common.mjs'

const page = await launch()
const noHScroll = () => page.eval('document.documentElement.scrollWidth <= document.documentElement.clientWidth')
const DB = 'mspace.demo.v1'
/** 데모 저장소를 직접 고쳐요 (시각이 맞아야 하는 이용 시작·키 지급을 지금 점검하려고) */
const patchDb = (fn) => page.eval(`(() => { const db = JSON.parse(localStorage.getItem('${DB}')); (${fn})(db); localStorage.setItem('${DB}', JSON.stringify(db)) })()`)
// 확인 창(window.confirm)은 페이지를 옮기면 원래대로 돌아와서, 누를 때마다 '확인'으로 바꿔 둬요 (안 그러면 Chrome이 창에서 멈춰요)
const clickIn = (selector, text) => page.eval(`window.confirm = () => true;
  [...document.querySelectorAll('${selector} button')].find(b => b.textContent.trim() === ${JSON.stringify(text)}).click()`)

try {
  await page.viewport(375, 740)

  // 1. 현황판: 비회원도 보고, 예약자 이름은 없음 (BD-01, BD-02)
  await page.goto(`${BASE}/`)
  await page.waitFor(`document.querySelectorAll('li[data-room]').length === 5`)
  check((await page.text()).includes('체험용 데모예요'), '데모 안내 배너')
  check(await noHScroll(), '현황판 375px 가로 스크롤 없음')
  const text = await page.text()
  check(!['김가상', '이예시', '박샘플', '최데모', '010-'].some((s) => text.includes(s)), '현황판에 예약자 이름·전화번호 없음')
  await page.screenshot(join(SHOTS, 'demo-board-375.png'))
  await page.clickText('내일')
  await page.waitFor(`document.querySelector('[aria-pressed=true]')?.textContent === '내일' && document.querySelectorAll('li[data-room]').length === 5`)
  check(true, '날짜를 바꾸면 그날 현황')

  // 2. 가입 → 빈 시간 눌러 예약 (RV-01)
  await page.goto(`${BASE}/signup`)
  await page.type('#field-studentNo', '20995555')
  await page.type('#field-name', '설문체험')
  await page.type('#field-phone', '010-0000-5555')
  await page.type('#field-password', 'demo1234')
  await page.clickButton('가입하기')
  await page.waitFor(`location.pathname === '/' && document.querySelectorAll('li[data-room]').length === 5`)
  await page.clickText('내일')
  await page.waitFor(`document.querySelector('li[data-room="3번방"] a[href^="/reserve"]')`)
  await page.click('li[data-room="3번방"] a[href^="/reserve"]')
  await page.waitFor(`document.querySelector('[data-testid=summary]')`)
  check((await page.eval(`document.querySelector('[data-testid=summary]').textContent`)).includes('3번방'), '빈 시간을 누르면 방·날짜가 채워진 예약 폼')
  check(await noHScroll(), '예약 폼 375px 가로 스크롤 없음')
  await page.screenshot(join(SHOTS, 'demo-reserve-375.png'))
  await page.clickButton('예약하기')
  await page.waitFor(`location.pathname === '/me/reservations' && document.querySelectorAll('li[data-reservation]').length === 1`)
  check((await page.text()).includes('예약했어요.'), '예약 완료 → 내 예약에 표시')

  // 3. 같은 날 두 번째 예약은 하루 한도 안내 (RV-06)
  await page.goto(`${BASE}/`)
  await page.waitFor(`document.querySelectorAll('li[data-room]').length === 5`)
  await page.clickText('내일')
  await page.waitFor(`document.querySelector('li[data-room="4번방"] a[href^="/reserve"]')`)
  await page.click('li[data-room="4번방"] a[href^="/reserve"]')
  await page.waitFor(`document.querySelector('[data-testid=summary]')`)
  await page.clickButton('예약하기')
  await page.waitFor(`document.querySelector('[role=alert]')?.textContent.includes('하루에 1건')`)
  check(true, '하루 한도 초과 안내 문구')

  // 4. 취소 (RV-10)
  await page.goto(`${BASE}/me/reservations`)
  await page.waitFor(`document.querySelectorAll('li[data-reservation]').length === 1`)
  await clickIn('li[data-reservation]', '예약 취소')
  await page.waitFor(`document.body.innerText.includes('예약을 취소했어요.')`)
  check(true, '예약 취소')

  // 5. 개방 기간 예약: 이용 시작 → 이용 종료 (OP-02, OP-03)
  await page.goto(`${BASE}/`)
  await page.waitFor(`document.querySelectorAll('li[data-room]').length === 5`)
  const chips = await page.eval(`[...document.querySelectorAll('[aria-label="날짜 선택"] button')].map(b => b.textContent)`)
  await page.eval(`document.querySelectorAll('[aria-label="날짜 선택"] button')[3].click()`)
  await page.waitFor(`document.body.innerText.includes('시험기간') && document.querySelector('li[data-room="2번방"] a[href^="/reserve"]')`)
  check((await page.text()).includes('종일 (00:00~24:00)'), `시험기간(${chips[3]})은 종일 개방으로 표시`)
  await page.click('li[data-room="2번방"] a[href^="/reserve"]')
  await page.waitFor(`document.querySelector('[data-testid=summary]')?.textContent.includes('키 없이')`)
  await page.clickButton('예약하기')
  await page.waitFor(`document.querySelectorAll('li[data-reservation]').length === 1`)
  // 시작 시각을 5분 뒤로 당겨서(30분 단위 규칙은 화면 점검과 무관) 이용 시작 가능 시간으로 만들어요
  await patchDb(`(db) => { const r = db.reservations.at(-1); const d = r.endAt - r.startAt; r.startAt = Date.now() + 5 * 60000; r.endAt = r.startAt + d }`)
  await page.goto(`${BASE}/me/reservations`)
  await page.waitFor(`[...document.querySelectorAll('li[data-reservation] button')].some(b => b.textContent === '이용 시작')`)
  await clickIn('li[data-reservation]', '이용 시작')
  await page.waitFor(`document.body.innerText.includes('이용을 시작했어요.') && document.body.innerText.includes('이용 중')`)
  check(true, '개방 기간: 이용 시작 → 이용 중')
  await clickIn('li[data-reservation]', '이용 종료')
  await page.waitFor(`document.body.innerText.includes('이용을 마쳤어요.')`)
  check(true, '개방 기간: 이용 종료')

  // 6. 관리자: 학생증 확인 체크 후 키 지급 → 반납 확인 (KY-01, KY-03)
  await page.goto(`${BASE}/me`)
  await page.waitFor(`document.querySelector('h1')?.textContent === '내 정보'`)
  await page.clickButton('로그아웃')
  await page.goto(`${BASE}/login?next=%2Fadmin`)
  check((await page.text()).includes('20990000'), '로그인 화면에 데모 관리자 안내')
  await page.type('#field-studentNo', '20990000')
  await page.type('#field-password', 'demo1234')
  await page.clickButton('로그인')
  await page.waitFor(`location.pathname === '/admin' && document.querySelector('dl')`)
  // 오늘 키 예약 하나를 지금 지급 가능한 시각으로 옮겨요
  const target = await page.eval(`(() => { const db = JSON.parse(localStorage.getItem('${DB}'));
    const r = { id: db.nextId.reservation++, roomId: 4, userId: 2, startAt: Date.now() + 5 * 60000, endAt: Date.now() + 65 * 60000,
      headcount: 2, status: 'RESERVED', keyRequired: true, createdAt: Date.now() };
    db.reservations.push(r); localStorage.setItem('${DB}', JSON.stringify(db)); return r.id })()`)
  await page.goto(`${BASE}/admin`)
  await page.waitFor(`document.querySelector('li[data-reservation="${target}"]')`)
  check(await noHScroll(), '관리자 예약·키 375px 가로 스크롤 없음')
  await clickIn(`li[data-reservation="${target}"]`, '키 지급')
  check(await page.eval(`[...document.querySelectorAll('li[data-reservation="${target}"] button')].find(b => b.textContent.includes('키 지급')).disabled`),
    '학생증 확인 체크 전에는 지급 버튼 비활성')
  await page.click(`li[data-reservation="${target}"] input[type=checkbox]`)
  await clickIn(`li[data-reservation="${target}"]`, '본인 확인 완료 · 키 지급')
  await page.waitFor(`document.querySelector('li[data-reservation="${target}"]')?.innerText.includes('이용 중')`)
  check(true, '키 지급 → 이용 중')
  await page.screenshot(join(SHOTS, 'demo-admin-375.png'))
  await clickIn(`li[data-reservation="${target}"]`, '반납 확인')
  await page.waitFor(`document.querySelector('li[data-reservation="${target}"]')?.innerText.includes('반납 완료')`)
  check(true, '반납 확인 → 반납 완료')
} catch (e) {
  check(false, '데모 점검 실행', e.message)
  await page.screenshot(join(SHOTS, 'demo-failure.png')).catch(() => {})
} finally {
  page.close()
}
