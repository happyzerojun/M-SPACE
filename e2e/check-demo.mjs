// 설문용 데모 점검 (VITE_DEMO=true 서버 대상, 백엔드 없음)
// 학생: 홈(방 카드) → 빈 시간 칩 → 바로 시작 → 방 화면 예약(2번 누름) → 하루 한도 → 취소 → 가입 → 시험기간 이용 시작·종료
// 관리자: 키 지급 → 반납. 공식 규정: 1회 최대 2시간
import { join } from 'node:path'
import { launch } from './cdp.mjs'
import { BASE, SHOTS, check } from './common.mjs'

const page = await launch()
const noHScroll = () => page.eval('document.documentElement.scrollWidth <= document.documentElement.clientWidth')
const DB = 'mspace.demo.v3'
const clickIn = (selector, text) => page.eval(`window.confirm = () => true;
  [...document.querySelectorAll('${selector} button')].find(b => b.textContent.trim() === ${JSON.stringify(text)}).click()`)
/** 날짜 격자의 n번째 칸 (0=오늘, 1=내일) */
const pickDate = (n) => page.eval(`document.querySelectorAll('[aria-label="날짜 선택"] button')[${n}].click()`)
const summary = () => page.eval(`document.querySelector('[data-testid=summary]').textContent`)

try {
  await page.viewport(375, 740)

  // 1. 홈: 비회원도 방 카드로 현황을 보고, 예약자 정보는 없음 (BD-01, BD-02)
  await page.goto(`${BASE}/`)
  await page.waitFor(`document.querySelectorAll('li[data-room]').length === 5`, 15_000)
  const home = await page.text()
  check(home.includes('체험용 데모예요'), '데모 안내 배너')
  check(await page.eval(`[...document.querySelectorAll('a')].some(a => a.href.startsWith('https://forms.gle/'))`), '설문 참여 링크(구글폼)')
  check(home.includes('최대 2시간'), '홈에 공식 규정 최대 2시간 안내')
  check(await noHScroll(), '홈 375px 가로 스크롤 없음')
  check(!['김가상', '이예시', '박샘플', '최데모', '010-'].some((s) => home.includes(s)), '홈에 예약자 이름·전화번호 없음')
  await page.screenshot(join(SHOTS, 'demo-home-375.png'))

  // 2. 데모 데이터에 2시간이 넘는 예약이 없음 (공식 규정)
  const longest = await page.eval(`Math.max(...JSON.parse(localStorage.getItem('${DB}')).reservations.map(r => r.endAt - r.startAt)) / 60000`)
  check(longest <= 120, '데모 예약 데이터 모두 2시간 이내', `최장 ${longest}분`)

  // 3. 내일 3번방 빈 시간 칩 → 로그인(바로 시작) → 방 화면에 그 시간이 골라져 있음 → 예약하기 = 학생이 누르는 건 3번
  await pickDate(1)
  await page.waitFor(`document.querySelector('[aria-label="날짜 선택"] button[aria-pressed=true]') === document.querySelectorAll('[aria-label="날짜 선택"] button')[1] && document.querySelector('li[data-room="3번방"] a[data-quick-slot]')`)
  await page.click('li[data-room="3번방"] a[data-quick-slot]')
  await page.waitFor(`location.pathname === '/rooms/3' && document.querySelector('[data-testid=summary]')?.textContent.includes('3번방')`)
  check((await summary()).includes('~'), '빈 시간 칩을 누르면 시작 시각·이용 시간이 골라진 방 화면')
  check(await noHScroll(), '방 화면 375px 가로 스크롤 없음')
  check(await page.eval(`[...document.querySelectorAll('[aria-label="이용 시간"] button')].map(b => b.textContent).at(-1) === '2시간'`), '이용 시간 선택지 최대 2시간')
  await page.screenshot(join(SHOTS, 'demo-room-375.png'))
  await page.clickButton('예약하기')
  await page.waitFor(`location.pathname === '/login'`)
  const loginText = await page.text()
  check(loginText.includes('데모 학생으로 바로 시작하기'), '비회원이 예약하기 → 로그인 화면에 바로 체험 버튼')
  check(!loginText.includes('20990000') && !loginText.includes('관리자'), '로그인 화면에 관리자 계정 안내 없음')
  await page.clickButton('데모 학생으로 바로 시작하기')
  await page.waitFor(`location.pathname === '/rooms/3' && document.querySelector('[data-testid=summary]')?.textContent.includes('~')`)
  check(true, '로그인 후 고른 시간 그대로 방 화면으로 돌아옴')
  await page.clickButton('예약하기')
  await page.waitFor(`location.pathname === '/me/reservations' && document.querySelectorAll('li[data-reservation]').length === 1`)
  const mine = await page.text()
  check(mine.includes('예약했어요.'), '예약 완료 → 내 예약에 표시')
  check(mine.includes('써 보니 어땠나요?'), '예약 직후 설문 참여 카드')

  // 4. 같은 날 두 번째 예약은 하루 한도 안내 (RV-06)
  await page.goto(`${BASE}/`)
  await page.waitFor(`document.querySelectorAll('li[data-room]').length === 5`)
  await pickDate(1)
  await page.waitFor(`document.querySelector('[aria-label="날짜 선택"] button[aria-pressed=true]') === document.querySelectorAll('[aria-label="날짜 선택"] button')[1] && document.querySelector('li[data-room="4번방"] a[data-quick-slot]')`)
  await page.click('li[data-room="4번방"] a[data-quick-slot]')
  await page.waitFor(`document.querySelector('[data-testid=summary]')?.textContent.includes('4번방')`)
  await page.clickButton('예약하기')
  await page.waitFor(`document.querySelector('[role=alert]')?.textContent.includes('하루에 1건')`)
  check(true, '하루 한도 초과 안내 문구')

  // 5. 취소 (RV-10)
  await page.goto(`${BASE}/me/reservations`)
  await page.waitFor(`document.querySelectorAll('li[data-reservation]').length === 1`)
  await clickIn('li[data-reservation]', '예약 취소')
  await page.waitFor(`document.body.innerText.includes('예약을 취소했어요.')`)
  check(true, '예약 취소')

  // 6. 직접 가입해서 쓰는 흐름도 그대로 동작 (AU-01)
  await page.goto(`${BASE}/me`)
  await page.waitFor(`document.querySelector('h1')?.textContent === '내 정보'`)
  await page.clickButton('로그아웃')
  await page.goto(`${BASE}/signup`)
  await page.type('#field-studentNo', '20995555')
  await page.type('#field-name', '설문체험')
  await page.type('#field-phone', '010-0000-5555')
  await page.type('#field-password', 'demo1234')
  await page.clickButton('가입하기')
  await page.waitFor(`location.pathname === '/' && document.querySelector('nav[aria-label="주 메뉴"]').innerText.includes('설문체험')`)
  check(true, '회원가입 후 바로 로그인')

  // 7. 시험기간(개방 기간): 방 화면에서 예약 → 이용 시작 → 이용 종료 (OP-02, OP-03)
  await pickDate(3)
  await page.waitFor(`document.body.innerText.includes('시험기간') && document.querySelector('li[data-room="2번방"] a[data-quick-slot]')`)
  check((await page.text()).includes('종일 (00:00~24:00)'), '시험기간은 종일 개방으로 표시')
  await page.click('li[data-room="2번방"] a[data-quick-slot]')
  await page.waitFor(`document.querySelector('[data-testid=summary]')?.textContent.includes('2번방')`)
  check((await page.text()).includes('이용 시작'), '시험기간 방 화면에 키 대신 이용 시작 안내')
  await page.clickButton('예약하기')
  await page.waitFor(`document.querySelectorAll('li[data-reservation]').length === 1`)
  await page.eval(`(() => { const db = JSON.parse(localStorage.getItem('${DB}')); const r = db.reservations.at(-1);
    const d = r.endAt - r.startAt; r.startAt = Date.now() + 5 * 60000; r.endAt = r.startAt + d; localStorage.setItem('${DB}', JSON.stringify(db)) })()`)
  await page.goto(`${BASE}/me/reservations`)
  await page.waitFor(`[...document.querySelectorAll('li[data-reservation] button')].some(b => b.textContent === '이용 시작')`)
  await clickIn('li[data-reservation]', '이용 시작')
  await page.waitFor(`document.body.innerText.includes('이용을 시작했어요.') && document.body.innerText.includes('이용 중')`)
  check(true, '개방 기간: 이용 시작 → 이용 중')
  await clickIn('li[data-reservation]', '이용 종료')
  await page.waitFor(`document.body.innerText.includes('이용을 마쳤어요.')`)
  check(true, '개방 기간: 이용 종료')
  await page.screenshot(join(SHOTS, 'demo-mine-375.png'))

  // 8. 관리자: 학생증 확인 체크 후 키 지급 → 반납 확인 (KY-01, KY-03). 계정은 README에만 있어요
  await page.goto(`${BASE}/me`)
  await page.waitFor(`document.querySelector('h1')?.textContent === '내 정보'`)
  await page.clickButton('로그아웃')
  await page.goto(`${BASE}/login?next=%2Fadmin`)
  await page.type('#field-studentNo', '20990000')
  await page.type('#field-password', 'demo1234')
  await page.clickButton('로그인')
  await page.waitFor(`location.pathname === '/admin' && document.querySelector('dl')`)
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
  await clickIn(`li[data-reservation="${target}"]`, '반납 확인')
  await page.waitFor(`document.querySelector('li[data-reservation="${target}"]')?.innerText.includes('반납 완료')`)
  check(true, '반납 확인 → 반납 완료')
} catch (e) {
  check(false, '데모 점검 실행', e.message)
  await page.screenshot(join(SHOTS, 'demo-failure.png')).catch(() => {})
} finally {
  page.close()
}
