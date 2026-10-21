// 설문용 데모 서버: 백엔드 없이 브라우저 안에서 API 명세(docs/API명세서.md)대로 응답해요.
// 규칙(30분 단위, 운영 시간, 최대 2시간, 하루 1건, 7일, 겹침 금지, 노쇼, 개방 기간)은 실제 서비스와 같게 맞춰요.
// 실제 백엔드를 붙이면 VITE_DEMO를 끄기만 하면 되고, 이 파일은 번들에 들어가지 않아요.
import { AxiosError } from 'axios'
import { load, save } from './store'
import { atMinute, daysBetween, formatMinute, kstDate, MINUTE, toKstIso } from './time'

const ACTIVE = ['RESERVED', 'IN_USE']

class ApiError extends Error {
  constructor(status, code, message, fieldErrors = []) {
    super(message)
    Object.assign(this, { status, code, fieldErrors })
  }
}
const fail = (status, code, message, fieldErrors) => {
  throw new ApiError(status, code, message, fieldErrors)
}
const invalid = (field, message) => fail(400, 'VALIDATION_FAILED', '입력값을 확인해 주세요.', [{ field, message }])

// ───────── 응답 모양 ─────────
const maskPhone = (phone) => phone.replace(/^(\d{3})-\d{3,4}-(\d{4})$/, '$1-****-$2')

function userResponse(u) {
  return { id: u.id, studentNo: u.studentNo, name: u.name, phone: u.phone, role: u.role, mustChangePassword: u.mustChangePassword }
}

function reservationResponse(db, r, viewer, now) {
  const room = db.rooms.find((x) => x.id === r.roomId)
  const owner = db.users.find((x) => x.id === r.userId)
  const overdue = r.status === 'IN_USE' && r.keyRequired && now >= r.endAt
  return {
    id: r.id,
    room: { id: room.id, name: room.name, capacity: room.capacity },
    startAt: toKstIso(r.startAt),
    endAt: toKstIso(r.endAt),
    headcount: r.headcount,
    status: r.status,
    displayStatus: overdue ? 'OVERDUE' : r.status,
    keyRequired: r.keyRequired,
    user: {
      id: owner.id, studentNo: owner.studentNo, name: owner.name,
      phone: viewer.role === 'ADMIN' || viewer.id === owner.id ? owner.phone : maskPhone(owner.phone),
    },
    keyIssuedAt: r.keyIssuedAt ? toKstIso(r.keyIssuedAt) : null,
    returnedAt: r.returnedAt ? toKstIso(r.returnedAt) : null,
    returnedLate: r.returnedLate ?? null,
    checkedInAt: r.checkedInAt ? toKstIso(r.checkedInAt) : null,
    completedAt: r.completedAt ? toKstIso(r.completedAt) : null,
    cancelledAt: r.cancelledAt ? toKstIso(r.cancelledAt) : null,
    cancelReason: r.cancelReason ?? null,
    createdAt: toKstIso(r.createdAt),
  }
}

function pageOf(items, params, defaultSize = 20) {
  const size = Math.min(Number(params.size) || defaultSize, 100)
  const page = Math.max(Number(params.page) || 1, 1)
  return {
    content: items.slice((page - 1) * size, page * size),
    page, size, totalElements: items.length, totalPages: Math.ceil(items.length / size),
  }
}

// ───────── 운영 시간 (PO-01) ─────────
function hoursOf(db, date) {
  const period = db.periods.find((p) => p.startDate <= date && date <= p.endDate)
  if (period) {
    return period.closed
      ? { closed: true, periodName: period.name, keyRequired: true }
      : { closed: false, open: period.openMinute, close: period.closeMinute, periodName: period.name, keyRequired: period.keyRequired }
  }
  return { closed: false, open: db.policy.defaultOpenMinute, close: db.policy.defaultCloseMinute, periodName: null, keyRequired: true }
}

// ───────── 스케줄러 흉내 (NS-01, OP-03): 요청마다 한 번 돌아요. 여러 번 돌아도 결과가 같아요 ─────────
function tick(db, now) {
  for (const r of db.reservations) {
    if (r.status === 'RESERVED' && now >= r.startAt + db.policy.noShowGraceMinutes * MINUTE) {
      Object.assign(r, { status: 'NO_SHOW', noShowAt: now })
    } else if (r.status === 'IN_USE' && !r.keyRequired && now >= r.endAt) {
      Object.assign(r, { status: 'COMPLETED', completedAt: r.endAt })
    }
  }
}

// ───────── 인증 ─────────
function currentUser(db, config) {
  const header = config.headers?.Authorization ?? config.headers?.get?.('Authorization')
  const id = Number(String(header ?? '').replace('Bearer demo-', ''))
  return db.users.find((u) => u.id === id && u.status === 'ACTIVE') ?? null
}
function requireUser(me, method, path) {
  if (!me) fail(401, 'UNAUTHORIZED', '로그인이 필요해요.')
  const allowed = (method === 'get' && path === '/me') || (method === 'put' && path === '/me/password')
  if (me.mustChangePassword && !allowed) fail(403, 'PASSWORD_CHANGE_REQUIRED', '임시 비밀번호로 로그인했어요. 비밀번호를 먼저 바꿔 주세요.')
  return me
}
function requireAdmin(me) {
  if (me.role !== 'ADMIN') fail(403, 'FORBIDDEN', '이 기능을 사용할 권한이 없어요.')
  return me
}

const STUDENT_NO = /^\d{8,10}$/
const PHONE = /^01[016789]-?\d{3,4}-?\d{4}$/
const PASSWORD = /^(?=.*[A-Za-z])(?=.*\d).{8,64}$/
const normalizePhone = (p) => {
  const d = p.replaceAll('-', '')
  return `${d.slice(0, 3)}-${d.slice(3, d.length - 4)}-${d.slice(-4)}`
}

function validateSignup(b) {
  const errors = []
  if (!STUDENT_NO.test(b.studentNo ?? '')) errors.push({ field: 'studentNo', message: '학번은 숫자 8~10자리로 입력해 주세요.' })
  if (!(b.name ?? '').trim() || b.name.trim().length > 50) errors.push({ field: 'name', message: '이름은 1~50자로 입력해 주세요.' })
  if (!PHONE.test(b.phone ?? '')) errors.push({ field: 'phone', message: '전화번호는 010-1234-5678 형식으로 입력해 주세요.' })
  if (!PASSWORD.test(b.password ?? '')) errors.push({ field: 'password', message: '비밀번호는 영문과 숫자를 모두 넣어 8~64자로 입력해 주세요.' })
  if (b.privacyAgreed !== true) errors.push({ field: 'privacyAgreed', message: '개인정보 수집·이용에 동의해 주세요.' })
  if (errors.length) fail(400, 'VALIDATION_FAILED', '입력값을 확인해 주세요.', errors)
}

// ───────── 예약 생성 검사 (docs/예약-상태-설계.md 5장 순서) ─────────
function createReservation(db, me, b, now) {
  const start = Date.parse(b.startAt)
  const end = Date.parse(b.endAt)
  if (Number.isNaN(start) || Number.isNaN(end)) invalid('startAt', '시작·종료 시각을 골라 주세요.')
  if ((start / MINUTE) % 30 !== 0 || (end / MINUTE) % 30 !== 0) invalid('startAt', '30분 단위로 골라 주세요.')
  if (end <= start) invalid('endAt', '종료 시각은 시작 시각보다 늦어야 해요.')
  if (!(b.headcount >= 1)) invalid('headcount', '이용 인원은 1명 이상이에요.')

  const room = db.rooms.find((r) => r.id === Number(b.roomId))
  if (!room) fail(404, 'ROOM_NOT_FOUND', '방을 찾을 수 없어요.')
  if (!room.active) fail(409, 'ROOM_INACTIVE', '지금은 이용할 수 없는 방이에요.')
  if (b.headcount > room.capacity) fail(400, 'HEADCOUNT_EXCEEDS_CAPACITY', `이 방은 최대 ${room.capacity}명까지 이용할 수 있어요.`)

  if (start <= now) fail(400, 'RESERVATION_IN_PAST', '지금 이후 시간만 예약할 수 있어요.')
  const date = kstDate(start)
  const window = db.policy.bookingWindowDays
  if (daysBetween(kstDate(now), date) > window) fail(400, 'RESERVATION_OUT_OF_WINDOW', `오늘부터 ${window}일 뒤까지만 예약할 수 있어요.`)

  const hours = hoursOf(db, date)
  if (hours.closed) fail(400, 'CLOSED_DAY', '그날은 휴관이에요.')
  if (start < atMinute(date, hours.open) || end > atMinute(date, hours.close)) {
    fail(400, 'OUTSIDE_OPERATING_HOURS', `운영 시간(${formatMinute(hours.open)}~${formatMinute(hours.close)}) 안에서만 예약할 수 있어요.`)
  }
  if (end - start > db.policy.maxDurationMinutes * MINUTE) {
    fail(400, 'RESERVATION_TOO_LONG', `한 번에 최대 ${db.policy.maxDurationMinutes / 60}시간까지 예약할 수 있어요.`)
  }

  const sameDay = db.reservations.filter((r) => r.userId === me.id && kstDate(r.startAt) === date
    && ['RESERVED', 'IN_USE', 'RETURNED', 'COMPLETED'].includes(r.status))
  if (sameDay.length >= db.policy.dailyLimit) fail(409, 'DAILY_LIMIT_EXCEEDED', `하루에 ${db.policy.dailyLimit}건까지 예약할 수 있어요.`)

  const overlaps = (r) => ACTIVE.includes(r.status) && r.startAt < end && start < r.endAt
  if (db.reservations.some((r) => r.roomId === room.id && overlaps(r))) {
    fail(409, 'RESERVATION_TIME_CONFLICT', '이미 예약된 시간이에요. 다른 시간을 골라 주세요.')
  }
  if (db.reservations.some((r) => r.userId === me.id && overlaps(r))) {
    fail(409, 'USER_TIME_CONFLICT', '같은 시간에 이미 다른 예약이 있어요.')
  }

  const r = { id: db.nextId.reservation++, roomId: room.id, userId: me.id, startAt: start, endAt: end,
    headcount: Number(b.headcount), status: 'RESERVED', keyRequired: hours.keyRequired, createdAt: now }
  db.reservations.push(r)
  return r
}

function findOwnReservation(db, me, id) {
  const r = db.reservations.find((x) => x.id === Number(id))
  // 남의 예약은 있는지조차 알려주지 않아요 (RV-09)
  if (!r || (r.userId !== me.id && me.role !== 'ADMIN')) fail(404, 'RESERVATION_NOT_FOUND', '예약을 찾을 수 없어요.')
  return r
}
const requireState = (r, status) => {
  if (r.status !== status) fail(409, 'INVALID_RESERVATION_STATE', '지금 상태에서는 할 수 없는 작업이에요.')
}
function inUseWindow(db, r, now, code, label) {
  const { keyIssueBeforeMinutes: before, noShowGraceMinutes: grace } = db.policy
  if (now < r.startAt - before * MINUTE || now >= r.startAt + grace * MINUTE) {
    fail(409, code, `${label}는 시작 ${before}분 전부터 시작 ${grace}분 후까지 할 수 있어요.`)
  }
}

// ───────── 라우팅 ─────────
const routes = []
/** isPublic: 비회원도 부를 수 있는 API (SecurityConfig와 같은 목록) */
const route = (method, pattern, handler, isPublic = false) => {
  const keys = []
  const regex = new RegExp(`^${pattern.replace(/:(\w+)/g, (_, k) => (keys.push(k), '([^/]+)'))}$`)
  routes.push({ method, regex, keys, handler, isPublic })
}

route('post', '/auth/signup', ({ db, body }) => {
  validateSignup(body)
  if (db.users.some((u) => u.studentNo === body.studentNo && u.status === 'ACTIVE')) {
    fail(409, 'DUPLICATE_STUDENT_NO', '이미 가입된 학번이에요. 본인 학번이라면 학과실에 문의해 주세요.')
  }
  const user = { id: db.nextId.user++, studentNo: body.studentNo, name: body.name.trim(), phone: normalizePhone(body.phone),
    password: body.password, role: 'STUDENT', status: 'ACTIVE', mustChangePassword: false }
  db.users.push(user)
  return [201, userResponse(user)]
}, true)

route('post', '/auth/login', ({ db, body }) => {
  const user = db.users.find((u) => u.studentNo === body.studentNo && u.status === 'ACTIVE')
  if (!user || user.password !== body.password) fail(401, 'LOGIN_FAILED', '학번 또는 비밀번호가 맞지 않아요.')
  return [200, { accessToken: `demo-${user.id}`, expiresIn: 7200, user: userResponse(user) }]
}, true)

route('get', '/me', ({ me }) => [200, userResponse(me)])
route('patch', '/me', ({ me, body }) => {
  if (body.name !== undefined && !body.name.trim()) invalid('name', '이름은 1~50자로 입력해 주세요.')
  if (body.phone !== undefined && !PHONE.test(body.phone)) invalid('phone', '전화번호는 010-1234-5678 형식으로 입력해 주세요.')
  if (body.name !== undefined) me.name = body.name.trim()
  if (body.phone !== undefined) me.phone = normalizePhone(body.phone)
  return [200, userResponse(me)]
})
route('put', '/me/password', ({ me, body }) => {
  if (me.password !== body.currentPassword) invalid('currentPassword', '현재 비밀번호가 맞지 않아요.')
  if (!PASSWORD.test(body.newPassword ?? '')) invalid('newPassword', '비밀번호는 영문과 숫자를 모두 넣어 8~64자로 입력해 주세요.')
  if (body.newPassword === body.currentPassword) invalid('newPassword', '현재 비밀번호와 다른 비밀번호를 입력해 주세요.')
  Object.assign(me, { password: body.newPassword, mustChangePassword: false })
  return [204, null]
})

// 현황판: 방·시간·상태만, 예약자 정보 없음 (BD-02)
route('get', '/board', ({ db, params, now }) => {
  const today = kstDate(now)
  const date = params.date || today
  const diff = daysBetween(today, date)
  if (diff < 0 || diff > db.policy.bookingWindowDays) {
    fail(400, 'RESERVATION_OUT_OF_WINDOW', `오늘부터 ${db.policy.bookingWindowDays}일 뒤까지만 볼 수 있어요.`)
  }
  const hours = hoursOf(db, date)
  const dayStart = atMinute(date, 0)
  const dayEnd = atMinute(date, 1440)
  return [200, {
    date,
    closed: hours.closed,
    openAt: hours.closed ? null : toKstIso(atMinute(date, hours.open)),
    closeAt: hours.closed ? null : toKstIso(atMinute(date, hours.close)),
    periodName: hours.periodName,
    keyRequired: hours.keyRequired,
    rooms: db.rooms.map((room) => ({
      id: room.id, name: room.name, capacity: room.capacity, active: room.active,
      blocks: db.reservations
        .filter((r) => r.roomId === room.id && ACTIVE.includes(r.status) && r.startAt < dayEnd && dayStart < r.endAt)
        .sort((a, b) => a.startAt - b.startAt)
        .map((r) => ({
          startAt: toKstIso(r.startAt), endAt: toKstIso(r.endAt),
          status: r.status === 'IN_USE' && r.keyRequired && now >= r.endAt ? 'OVERDUE' : r.status,
        })),
    })),
  }]
}, true)

route('get', '/rooms', ({ db }) => [200, db.rooms.map(({ id, name, capacity, active }) => ({ id, name, capacity, active }))], true)
route('get', '/policy', ({ db }) => [200, {
  slotMinutes: 30, maxDurationMinutes: db.policy.maxDurationMinutes, dailyLimit: db.policy.dailyLimit,
  bookingWindowDays: db.policy.bookingWindowDays, noShowGraceMinutes: db.policy.noShowGraceMinutes,
  keyIssueBeforeMinutes: db.policy.keyIssueBeforeMinutes, penaltyWindowDays: db.policy.penaltyWindowDays,
  penaltyThreshold: db.policy.penaltyThreshold, penaltyDays: db.policy.penaltyDays,
}], true)

route('post', '/reservations', ({ db, me, body, now }) => [201, reservationResponse(db, createReservation(db, me, body, now), me, now)])

route('get', '/me/reservations', ({ db, me, params, now }) => {
  const upcoming = params.scope !== 'past'
  const list = db.reservations
    .filter((r) => r.userId === me.id && (ACTIVE.includes(r.status) && r.endAt > now) === upcoming)
    .sort((a, b) => (upcoming ? a.startAt - b.startAt : b.startAt - a.startAt))
    .map((r) => reservationResponse(db, r, me, now))
  return [200, pageOf(list, params)]
})

route('get', '/reservations/:id', ({ db, me, id, now }) => [200, reservationResponse(db, findOwnReservation(db, me, id), me, now)])

route('post', '/reservations/:id/cancel', ({ db, me, id, now }) => {
  const r = findOwnReservation(db, me, id)
  if (r.userId !== me.id) fail(404, 'RESERVATION_NOT_FOUND', '예약을 찾을 수 없어요.')
  requireState(r, 'RESERVED')
  if (now >= r.startAt) fail(409, 'CANCEL_AFTER_START', '시작 시간이 지난 예약은 취소할 수 없어요.')
  Object.assign(r, { status: 'CANCELLED', cancelledAt: now, cancelledBy: me.id })
  return [200, reservationResponse(db, r, me, now)]
})

// 개방 기간 예약: 키 대신 앱에서 이용 시작·종료 (OP-02, OP-03)
route('post', '/reservations/:id/check-in', ({ db, me, id, now }) => {
  const r = findOwnReservation(db, me, id)
  if (r.keyRequired) fail(409, 'INVALID_RESERVATION_STATE', '지금 상태에서는 할 수 없는 작업이에요.')
  requireState(r, 'RESERVED')
  inUseWindow(db, r, now, 'CHECK_IN_NOT_ALLOWED_TIME', '이용 시작')
  Object.assign(r, { status: 'IN_USE', checkedInAt: now })
  return [200, reservationResponse(db, r, me, now)]
})
route('post', '/reservations/:id/finish', ({ db, me, id, now }) => {
  const r = findOwnReservation(db, me, id)
  if (r.keyRequired) fail(409, 'INVALID_RESERVATION_STATE', '지금 상태에서는 할 수 없는 작업이에요.')
  requireState(r, 'IN_USE')
  Object.assign(r, { status: 'COMPLETED', completedAt: now })
  return [200, reservationResponse(db, r, me, now)]
})

// ───────── 관리자 (AD-01: /admin/** 는 ADMIN만) ─────────
function dayReservations(db, date) {
  const from = atMinute(date, 0)
  const to = atMinute(date, 1440)
  return db.reservations.filter((r) => r.startAt < to && from < r.endAt).sort((a, b) => a.startAt - b.startAt || a.roomId - b.roomId)
}

route('get', '/admin/dashboard', ({ db, me, params, now }) => {
  const date = params.date || kstDate(now)
  const list = dayReservations(db, date)
  const view = (r) => reservationResponse(db, r, me, now)
  const isOverdue = (r) => r.status === 'IN_USE' && r.keyRequired && now >= r.endAt
  const count = (s) => list.filter((r) => r.status === s).length
  return [200, {
    date,
    counts: {
      reserved: count('RESERVED'), inUse: count('IN_USE') - list.filter(isOverdue).length, overdue: list.filter(isOverdue).length,
      returned: count('RETURNED'), completed: count('COMPLETED'), noShow: count('NO_SHOW'), cancelled: count('CANCELLED'),
    },
    pendingIssue: list.filter((r) => r.status === 'RESERVED' && r.keyRequired).map(view),
    overdue: db.reservations.filter(isOverdue).map(view),
  }]
})

route('get', '/admin/reservations', ({ db, me, params, now }) => {
  const date = params.date || kstDate(now)
  let list = dayReservations(db, date)
  if (params.status) list = list.filter((r) => r.status === params.status)
  return [200, pageOf(list.map((r) => reservationResponse(db, r, me, now)), params, 100)]
})

route('post', '/admin/reservations/:id/key-issue', ({ db, me, id, body, now }) => {
  const r = findOwnReservation(db, me, id)
  if (!r.keyRequired) fail(409, 'INVALID_RESERVATION_STATE', '지금 상태에서는 할 수 없는 작업이에요.')
  requireState(r, 'RESERVED')
  if (body.identityVerified !== true) fail(400, 'IDENTITY_NOT_VERIFIED', '학생증으로 본인 확인을 먼저 해 주세요.')
  inUseWindow(db, r, now, 'KEY_ISSUE_NOT_ALLOWED_TIME', '키 지급')
  Object.assign(r, { status: 'IN_USE', keyIssuedAt: now })
  db.keyLogs.push({ reservationId: r.id, action: 'ISSUE', adminId: me.id, identityVerified: true, occurredAt: now })
  return [200, reservationResponse(db, r, me, now)]
})
route('post', '/admin/reservations/:id/key-return', ({ db, me, id, now }) => {
  const r = findOwnReservation(db, me, id)
  if (!r.keyRequired) fail(409, 'INVALID_RESERVATION_STATE', '지금 상태에서는 할 수 없는 작업이에요.')
  requireState(r, 'IN_USE')
  Object.assign(r, { status: 'RETURNED', returnedAt: now, returnedLate: now > r.endAt })
  db.keyLogs.push({ reservationId: r.id, action: 'RETURN', adminId: me.id, late: now > r.endAt, occurredAt: now })
  return [200, reservationResponse(db, r, me, now)]
})
route('post', '/admin/reservations/:id/cancel', ({ db, me, id, body, now }) => {
  const r = findOwnReservation(db, me, id)
  if (!(body.reason ?? '').trim()) invalid('reason', '사유를 입력해 주세요.')
  requireState(r, 'RESERVED')
  Object.assign(r, { status: 'CANCELLED', cancelledAt: now, cancelledBy: me.id, cancelReason: body.reason.trim() })
  return [200, reservationResponse(db, r, me, now)]
})

route('get', '/admin/users', ({ db, params, now }) => {
  const q = (params.q ?? '').trim()
  const list = db.users
    .filter((u) => !params.status || u.status === params.status)
    .filter((u) => !q || u.studentNo.startsWith(q) || u.name.includes(q))
    .sort((a, b) => a.studentNo.localeCompare(b.studentNo))
    .map((u) => ({
      ...userResponse(u), status: u.status, createdAt: toKstIso(now), restrictedUntil: null,
      recentNoShowCount: db.reservations.filter((r) => r.userId === u.id && r.status === 'NO_SHOW'
        && r.startAt >= now - db.policy.penaltyWindowDays * 1440 * MINUTE).length,
    }))
  return [200, pageOf(list, params)]
})
// 데모는 노쇼 제재를 흉내 내지 않아서 이용 제한이 생기지 않아요 (빈 목록)
route('get', '/admin/restrictions', ({ params }) => [200, pageOf([], params)])
route('post', '/admin/restrictions/:id/lift', () => fail(404, 'RESTRICTION_NOT_FOUND', '이용 제한을 찾을 수 없어요.'))

route('post', '/admin/users/:id/password-reset', ({ db, id, body }) => {
  if (!(body.reason ?? '').trim()) invalid('reason', '사유를 입력해 주세요.')
  const user = db.users.find((u) => u.id === Number(id) && u.status === 'ACTIVE')
  if (!user) fail(404, 'USER_NOT_FOUND', '회원을 찾을 수 없어요.')
  const temporaryPassword = `demo${Math.floor(100000 + Math.random() * 900000)}`
  Object.assign(user, { password: temporaryPassword, mustChangePassword: true })
  return [200, { temporaryPassword }]
})
route('post', '/admin/users/:id/disable', ({ db, me, id, body, now }) => {
  if (!(body.reason ?? '').trim()) invalid('reason', '사유를 입력해 주세요.')
  if (Number(id) === me.id) fail(400, 'CANNOT_DISABLE_SELF', '본인 계정은 비활성화할 수 없어요.')
  const user = db.users.find((u) => u.id === Number(id))
  if (!user) fail(404, 'USER_NOT_FOUND', '회원을 찾을 수 없어요.')
  user.status = 'DISABLED'
  for (const r of db.reservations) {
    if (r.userId === user.id && r.status === 'RESERVED') Object.assign(r, { status: 'CANCELLED', cancelledAt: now, cancelReason: '계정 비활성화' })
  }
  return [200, { ...userResponse(user), status: user.status, recentNoShowCount: 0, restrictedUntil: null, createdAt: toKstIso(now) }]
})

/** axios 어댑터: 요청을 받아 위 라우트로 처리하고 axios 응답(또는 에러)으로 돌려줘요 */
export async function handle(config) {
  const now = Date.now()
  const method = config.method.toLowerCase()
  const [path, query] = config.url.split('?')
  const params = { ...Object.fromEntries(new URLSearchParams(query ?? '')), ...(config.params ?? {}) }
  const body = typeof config.data === 'string' && config.data ? JSON.parse(config.data) : (config.data ?? {})
  const db = load(now)
  tick(db, now)

  const respond = (status, data) => ({ data, status, statusText: String(status), headers: {}, config, request: {} })
  try {
    const found = routes.map((r) => ({ r, m: r.method === method && path.match(r.regex) })).find((x) => x.m)
    if (!found) fail(404, 'NOT_FOUND', '요청한 주소를 찾을 수 없어요.')
    const ctx = { db, body, params, now, me: null }
    found.r.keys.forEach((k, i) => (ctx[k] = decodeURIComponent(found.m[i + 1])))
    if (!found.r.isPublic) {
      ctx.me = requireUser(currentUser(db, config), method, path)
      if (path.startsWith('/admin/')) requireAdmin(ctx.me)
    }
    const [status, data] = found.r.handler(ctx)
    save(db)
    await new Promise((r) => setTimeout(r, 120)) // 실제 네트워크처럼 아주 잠깐 기다려요 (로딩 표시 확인용)
    return respond(status, data)
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
    const response = respond(e.status, { code: e.code, message: e.message, fieldErrors: e.fieldErrors })
    throw new AxiosError(e.message, String(e.status), config, {}, response)
  }
}

