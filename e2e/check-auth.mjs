// 1단계 회원·인증 화면 점검 (AU-01~08): 가입 → 로그아웃 → 로그인 실패/성공 → 권한 → 관리자 비밀번호 초기화 → 강제 변경
// 학번은 실행마다 새 가짜 값(99로 시작)을 써요. 실제 학생 정보는 쓰지 않아요
import { join } from 'node:path'
import { launch } from './cdp.mjs'
import { API, BASE, SHOTS, check, sql } from './common.mjs'

const rnd = () => String(Math.floor(Math.random() * 1e6)).padStart(6, '0')
const student = `99${rnd()}`
const admin = `99${rnd()}`
const PASSWORD = 'mspace1234'

const page = await launch()
const noHScroll = () => page.eval('document.documentElement.scrollWidth <= document.documentElement.clientWidth')
const heading = () => page.eval(`document.querySelector('h1')?.textContent`)
const navText = () => page.eval(`document.querySelector('header nav').innerText`)

async function fillLogin(studentNo, password) {
  await page.type('#field-studentNo', studentNo)
  await page.type('#field-password', password)
  await page.clickButton('로그인')
}

try {
  await page.viewport(375, 740)

  // 1. 가입하면 바로 로그인돼 현황판으로 (AU-01)
  await page.goto(`${BASE}/signup`)
  await page.clickButton('가입하기')
  await page.waitFor(`document.querySelectorAll('[aria-invalid="true"]').length >= 4`)
  check(await noHScroll(), '빈 가입 폼 에러 표시 + 375px 가로 스크롤 없음')
  await page.type('#field-studentNo', student)
  await page.type('#field-name', '점검학생')
  await page.type('#field-phone', '01000000000')
  await page.type('#field-password', PASSWORD)
  await page.clickButton('가입하기')
  await page.waitFor(`location.pathname === '/' && document.querySelector('header nav').innerText.includes('점검학생')`)
  check(true, '가입 후 자동 로그인, 상단에 이름 표시')

  // 2. 같은 학번 재가입은 409 안내
  const dup = await fetch(`${API}/auth/signup`, { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ studentNo: student, name: 'x', phone: '010-0000-0000', password: PASSWORD }) })
  check(dup.status === 409, '같은 학번 재가입 409', String(dup.status))

  // 3. 새로고침해도 로그인 유지, 내 정보 화면에서 로그아웃 (AU-03, AU-04)
  await page.goto(`${BASE}/me`)
  await page.waitFor(`document.querySelector('h1')?.textContent === '내 정보'`)
  check((await page.eval(`document.querySelector('#field-phone').value`)) === '010-0000-0000', '내 정보에 정규화된 전화번호')
  check(await noHScroll(), '/me 375px 가로 스크롤 없음')
  await page.clickButton('로그아웃')
  await page.waitFor(`document.querySelector('header nav').innerText.includes('로그인')`)
  check(true, '로그아웃하면 로그인 메뉴로 바뀜')

  // 4. 로그인 필요 화면 → 로그인 → 원래 화면으로 (next)
  await page.goto(`${BASE}/me/reservations`)
  await page.waitFor(`location.pathname === '/login'`)
  check((await page.path()).includes('next=%2Fme%2Freservations'), '비회원이 내 예약 접근 시 로그인으로 이동(next 유지)')
  await fillLogin(student, 'wrong12345')
  await page.waitFor(`document.querySelector('[role=alert]')?.textContent === '학번 또는 비밀번호가 맞지 않아요.'`)
  check(true, '틀린 비밀번호 안내 문구')
  await page.type('#field-password', PASSWORD)
  await page.clickButton('로그인')
  await page.waitFor(`location.pathname === '/me/reservations'`)
  check(true, '로그인 후 원래 화면(내 예약)으로 돌아감')

  // 5. 학생은 관리자 화면·메뉴가 없음
  check(!(await navText()).includes('관리'), '학생 상단 메뉴에 관리 없음')
  await page.goto(`${BASE}/admin`)
  await page.waitFor(`document.querySelector('h1')?.textContent === '관리자만 볼 수 있는 화면이에요.'`)
  check(true, '학생이 /admin 접근하면 안내')

  // 6. 관리자: 회원 검색 → 비밀번호 초기화 (AU-06, AU-07)
  const res = await fetch(`${API}/auth/signup`, { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ studentNo: admin, name: '점검관리자', phone: '010-0000-0000', password: PASSWORD }) })
  check(res.status === 201, '관리자용 계정 가입')
  sql(`UPDATE users SET role = 'ADMIN' WHERE student_no = '${admin}' AND status = 'ACTIVE'`)
  await page.goto(`${BASE}/me`)
  await page.waitFor(`document.querySelector('h1')?.textContent === '내 정보'`)
  await page.clickButton('로그아웃')
  await page.goto(`${BASE}/login?next=%2Fadmin`)
  await fillLogin(admin, PASSWORD)
  await page.waitFor(`location.pathname === '/admin' && document.querySelector('input[aria-label="학번 또는 이름"]')`)
  check((await navText()).includes('관리'), '관리자 상단 메뉴에 관리')
  await page.type('input[aria-label="학번 또는 이름"]', student)
  await page.clickButton('검색')
  await page.waitFor(`document.querySelectorAll('li[data-student-no]').length === 1`)
  check(await noHScroll(), '관리자 회원 목록 375px 가로 스크롤 없음')
  await page.clickButton('비밀번호 초기화')
  await page.eval(`(() => { const el = document.querySelector('li[data-student-no] form input');
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(el, '학생증 확인');
    el.dispatchEvent(new Event('input', { bubbles: true })) })()`)
  await page.clickButton('초기화')
  await page.waitFor(`document.querySelector('[data-testid=temp-password]')`)
  const temp = await page.eval(`document.querySelector('[data-testid=temp-password]').textContent`)
  check(/^(?=.*[A-Za-z])(?=.*\d)[A-Za-z0-9]{10}$/.test(temp), '임시 비밀번호 표시(영문·숫자 10자)')
  await page.screenshot(join(SHOTS, 'auth-admin-reset-375.png'))

  // 7. 학생이 임시 비밀번호로 로그인 → 비밀번호 변경 강제 → 변경 후 정상 이용
  await page.goto(`${BASE}/me`)
  await page.waitFor(`document.querySelector('h1')?.textContent === '내 정보'`)
  await page.clickButton('로그아웃')
  await page.goto(`${BASE}/login`)
  await fillLogin(student, temp)
  await page.waitFor(`location.pathname === '/me/password'`)
  check((await page.text()).includes('임시 비밀번호로 로그인했어요'), '임시 비밀번호 로그인 → 비밀번호 변경 화면 + 안내')
  await page.goto(`${BASE}/me/reservations`)
  await page.waitFor(`location.pathname === '/me/password'`)
  check(true, '변경 전에는 다른 화면으로 가도 비밀번호 변경으로 돌아옴')
  await page.type('#field-currentPassword', temp)
  await page.type('#field-newPassword', 'newpass99')
  await page.type('#field-confirm', 'newpass99')
  await page.clickButton('비밀번호 바꾸기')
  await page.waitFor(`location.pathname === '/'`)
  await page.goto(`${BASE}/me/reservations`)
  await page.waitFor(`location.pathname === '/me/reservations'`)
  check(true, '변경 후 내 예약 화면 이용 가능')
  check((await heading()) === '내 예약', '내 예약 제목')

  // 8. 긴 이름이어도 상단 메뉴가 375px 안에 들어감
  sql(`UPDATE users SET name = '아주아주긴이름을가진점검학생입니다' WHERE student_no = '${student}' AND status = 'ACTIVE'`)
  await page.goto(`${BASE}/me`)
  await page.waitFor(`document.querySelector('h1')?.textContent === '내 정보'`)
  check(await noHScroll(), '긴 이름에서도 375px 가로 스크롤 없음')
  check(await page.eval(`[...document.querySelectorAll('header nav a')].every((a) => a.getBoundingClientRect().right <= innerWidth)`),
    '긴 이름에서도 상단 메뉴가 화면 안에 다 보임')
} catch (e) {
  check(false, '회원·인증 점검 실행', e.message)
  await page.screenshot(join(SHOTS, 'auth-failure.png')).catch(() => {})
} finally {
  page.close()
}
