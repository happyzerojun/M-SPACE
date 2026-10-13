// 데모 데이터 저장소: 방문자 브라우저의 localStorage에만 저장돼요 (서버로 보내지 않아요).
// 모든 사람·연락처는 가짜 값이에요 (CLAUDE.md 6장: 실제 학생 정보 금지)
import { DEMO_ADMIN, DEMO_STUDENT } from '../client'
import { addDays, atMinute, kstDate, MINUTE } from './time'

// v2: 체험 학생 계정 추가. 키를 바꿔서 예전 데모 데이터가 남은 브라우저도 새로 시작해요
const KEY = 'mspace.demo.v2'

function seed(now) {
  const today = kstDate(now)
  const users = [
    { id: 1, studentNo: DEMO_ADMIN.studentNo, name: '데모관리자', phone: '010-0000-0000', password: DEMO_ADMIN.password, role: 'ADMIN', status: 'ACTIVE', mustChangePassword: false },
    { id: 2, studentNo: '20990001', name: '김가상', phone: '010-0000-0001', password: 'demo1234', role: 'STUDENT', status: 'ACTIVE', mustChangePassword: false },
    { id: 3, studentNo: '20990002', name: '이예시', phone: '010-0000-0002', password: 'demo1234', role: 'STUDENT', status: 'ACTIVE', mustChangePassword: false },
    { id: 4, studentNo: '20990003', name: '박샘플', phone: '010-0000-0003', password: 'demo1234', role: 'STUDENT', status: 'ACTIVE', mustChangePassword: false },
    { id: 5, studentNo: '20990004', name: '최데모', phone: '010-0000-0004', password: 'demo1234', role: 'STUDENT', status: 'ACTIVE', mustChangePassword: false },
    // "데모 학생으로 바로 시작하기" 계정: 예약이 하나도 없어서 하루 한도에 걸리지 않고 바로 예약해 볼 수 있어요
    { id: 6, studentNo: DEMO_STUDENT.studentNo, name: '체험학생', phone: '010-0000-1111', password: DEMO_STUDENT.password, role: 'STUDENT', status: 'ACTIVE', mustChangePassword: false },
  ]
  const rooms = [
    { id: 1, name: '1번방', capacity: 4, active: true },
    { id: 2, name: '2번방', capacity: 4, active: true },
    { id: 3, name: '3번방', capacity: 4, active: true },
    { id: 4, name: '4번방', capacity: 4, active: true },
    { id: 5, name: '5번방', capacity: 8, active: true },
  ]
  const policy = {
    defaultOpenMinute: 540, defaultCloseMinute: 1080, maxDurationMinutes: 180, dailyLimit: 1, bookingWindowDays: 7,
    keyIssueBeforeMinutes: 10, noShowGraceMinutes: 15, penaltyWindowDays: 14, penaltyThreshold: 2, penaltyDays: 7,
  }
  // 모레부터 사흘은 시험기간 종일 개방 (키 대신 앱에서 이용 시작, D-9)
  const periods = [{ id: 1, name: '시험기간 (데모)', startDate: addDays(today, 2), endDate: addDays(today, 4), closed: false, keyRequired: false, openMinute: 0, closeMinute: 1440 }]

  // 오늘 예약은 고정 시각에 두고, 지금 시각에 맞춰 상태를 정해요 (언제 열어도 현황판이 살아 있게)
  const reservations = []
  const add = (roomId, userId, date, startMin, endMin, keyRequired = true) => {
    const startAt = atMinute(date, startMin)
    const endAt = atMinute(date, endMin)
    let r = { id: reservations.length + 1, roomId, userId, startAt, endAt, headcount: 3, status: 'RESERVED', keyRequired, createdAt: now - 86400000 }
    if (endAt <= now) r = { ...r, status: 'RETURNED', keyIssuedAt: startAt - 5 * MINUTE, returnedAt: endAt - 10 * MINUTE, returnedLate: false }
    else if (startAt <= now) r = { ...r, status: 'IN_USE', keyIssuedAt: startAt - 5 * MINUTE }
    reservations.push(r)
  }
  add(1, 2, today, 600, 720)
  add(2, 3, today, 780, 900)
  add(5, 4, today, 840, 1020)
  add(3, 5, today, 990, 1080)
  add(2, 2, addDays(today, 1), 600, 780)
  add(4, 3, addDays(today, 1), 840, 960)
  add(1, 4, addDays(today, 2), 1260, 1440, false)
  add(5, 5, addDays(today, 3), 120, 300, false)
  return { users, rooms, policy, periods, reservations, keyLogs: [], nextId: { user: 7, reservation: reservations.length + 1 } }
}

export function load(now) {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY))
    if (saved?.users) return saved
  } catch {
    // 깨진 값이면 새로 만들어요
  }
  const fresh = seed(now)
  save(fresh)
  return fresh
}

export function save(db) {
  try {
    localStorage.setItem(KEY, JSON.stringify(db))
  } catch {
    // 저장이 막힌 브라우저(사생활 보호 모드 등)에서는 새로고침하면 처음 상태로 돌아가요
  }
}

/** 데모 초기화 버튼 */
export function resetDemo() {
  try {
    localStorage.removeItem(KEY)
  } catch {
    // 무시
  }
}
