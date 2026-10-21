# M-SPACE API 명세서

- 문서 버전: v0.8 (2026-10-21) · 근거: `요구사항.md` v1.0, `docs/예약-상태-설계.md` v0.4
- 기본 경로 `/api`, JSON(UTF-8). 인증은 `Authorization: Bearer <accessToken>`.

## 0. 공통 규격

### 응답
- 성공: DTO를 **본문 그대로** 반환해요(감싸지 않음). 생성 `201`, 삭제 `204`, 상태 변경(취소·키 지급 등)은 `200` + 바뀐 DTO.
- 목록: `PageResponse`
  ```json
  { "content": [], "page": 1, "size": 20, "totalElements": 0, "totalPages": 0 }
  ```
  `page`는 1부터, `size` 기본 20·최대 100(넘으면 100으로 맞춤).
- 시각: ISO-8601 `+09:00` (예: `2026-10-13T14:00:00+09:00`). 날짜: `YYYY-MM-DD`(KST).
- 전화번호: 본인·관리자 외에는 `010-****-5678`로 마스킹. 현황판에는 예약자 정보가 아예 없어요.

### 에러
```json
{ "code": "RESERVATION_TIME_CONFLICT", "message": "이미 예약된 시간이에요. 다른 시간을 골라 주세요.", "fieldErrors": [] }
```
`fieldErrors` 항목: `{ "field": "startAt", "message": "30분 단위로 골라 주세요." }`

| HTTP | code | message |
|---|---|---|
| 400 | `VALIDATION_FAILED` | 입력값을 확인해 주세요. |
| 400 | `HEADCOUNT_EXCEEDS_CAPACITY` | 이 방은 최대 {n}명까지 이용할 수 있어요. |
| 400 | `RESERVATION_OUT_OF_WINDOW` | 오늘부터 {n}일 뒤까지만 예약할 수 있어요. |
| 400 | `RESERVATION_IN_PAST` | 지금 이후 시간만 예약할 수 있어요. |
| 400 | `OUTSIDE_OPERATING_HOURS` | 운영 시간({open}~{close}) 안에서만 예약할 수 있어요. |
| 400 | `CLOSED_DAY` | 그날은 휴관이에요. |
| 400 | `RESERVATION_TOO_LONG` | 한 번에 최대 {n}시간까지 예약할 수 있어요. |
| 400 | `IDENTITY_NOT_VERIFIED` | 학생증으로 본인 확인을 먼저 해 주세요. |
| 401 | `UNAUTHORIZED` | 로그인이 필요해요. |
| 401 | `LOGIN_FAILED` | 학번 또는 비밀번호가 맞지 않아요. |
| 403 | `FORBIDDEN` | 이 기능을 사용할 권한이 없어요. |
| 403 | `USER_RESTRICTED` | 노쇼 누적으로 {date}까지 예약할 수 없어요. |
| 403 | `PASSWORD_CHANGE_REQUIRED` | 임시 비밀번호로 로그인했어요. 비밀번호를 먼저 바꿔 주세요. |
| 404 | `NOT_FOUND` | 요청한 주소를 찾을 수 없어요. |
| 404 | `RESERVATION_NOT_FOUND` | 예약을 찾을 수 없어요. |
| 404 | `ROOM_NOT_FOUND` / `USER_NOT_FOUND` / `PERIOD_NOT_FOUND` / `RESTRICTION_NOT_FOUND` | …을(를) 찾을 수 없어요. |
| 405 | `METHOD_NOT_ALLOWED` | 지원하지 않는 요청 방식이에요. |
| 400 | `CANNOT_DISABLE_SELF` | 본인 계정은 비활성화할 수 없어요. |
| 409 | `DUPLICATE_STUDENT_NO` | 이미 가입된 학번이에요. 본인 학번이라면 학과실에 문의해 주세요. |
| 409 | `RESERVATION_TIME_CONFLICT` | 이미 예약된 시간이에요. 다른 시간을 골라 주세요. |
| 409 | `USER_TIME_CONFLICT` | 같은 시간에 이미 다른 예약이 있어요. |
| 409 | `DAILY_LIMIT_EXCEEDED` | 하루에 {n}건까지 예약할 수 있어요. |
| 409 | `ROOM_INACTIVE` | 지금은 이용할 수 없는 방이에요. |
| 409 | `INVALID_RESERVATION_STATE` | 지금 상태에서는 할 수 없는 작업이에요. |
| 409 | `CANCEL_AFTER_START` | 시작 시간이 지난 예약은 취소할 수 없어요. |
| 409 | `CHECK_IN_NOT_ALLOWED_TIME` | 이용 시작은 시작 {n}분 전부터 시작 {m}분 후까지 할 수 있어요. |
| 409 | `KEY_ISSUE_NOT_ALLOWED_TIME` | 키는 시작 {n}분 전부터 시작 {m}분 후까지 지급할 수 있어요. |
| 409 | `PERIOD_OVERLAP` | 다른 특별 운영 기간과 날짜가 겹쳐요. |
| 409 | `INVALID_RESTRICTION_STATE` | 이미 해제된 이용 제한이에요. |
| 409 | `CONCURRENT_UPDATE` | 다른 처리와 겹쳤어요. 새로고침 후 다시 시도해 주세요. |
| 429 | `TOO_MANY_REQUESTS` | 요청이 너무 많아요. 1분 뒤에 다시 시도해 주세요. (로그인·가입만, IP별 1분 20회, `Retry-After: 60`) |
| 500 | `INTERNAL_ERROR` | 잠시 후 다시 시도해 주세요. |

- DB 제약 위반·동시 처리 충돌은 위 409 코드로 바꿔요 (NF-05).
  - 동시에 들어온 예약 INSERT끼리 겹침 검사 중 교착(PostgreSQL `40P01`)·직렬화 실패(`40001`)로 끊긴 쪽도 `409 RESERVATION_TIME_CONFLICT`예요(재시도하지 않아요). 그 밖의 교착·잠금 실패는 `409 CONCURRENT_UPDATE` (TS-16).
  - 매핑되지 않은 제약 위반은 `500 INTERNAL_ERROR`이고, 서버 로그에는 제약 이름·SQLState만 남겨요(DB가 보내는 위반 행 값은 남기지 않아요, NF-01).
- 볼 수 없는 대상은 404, 볼 수 있지만 권한이 없으면 403. 학생이 `/api/admin/**`를 부르면 403.
- `must_change_password`인 사용자는 비밀번호 변경·내 정보 조회·로그아웃 외 API에서 `PASSWORD_CHANGE_REQUIRED`.

### 공통 DTO
```jsonc
// ReservationResponse (본인·관리자용)
{
  "id": 12,
  "room": { "id": 1, "name": "1번방", "capacity": 4 },
  "startAt": "2026-10-13T14:00:00+09:00",
  "endAt": "2026-10-13T16:00:00+09:00",
  "headcount": 3,
  "status": "RESERVED",            // RESERVED | IN_USE | RETURNED | COMPLETED | CANCELLED | NO_SHOW
  "keyRequired": true,             // false면 개방 기간 예약: 앱에서 이용 시작·종료
  "displayStatus": "RESERVED",     // + OVERDUE (IN_USE이면서 종료 시각 경과)
  "user": { "id": 3, "studentNo": "20231234", "name": "홍길동", "phone": "010-****-5678" }, // 관리자 응답은 마스킹 없음
  "keyIssuedAt": null, "returnedAt": null, "returnedLate": null,
  "checkedInAt": null, "completedAt": null,
  "cancelledAt": null, "cancelReason": null,
  "createdAt": "2026-10-12T10:00:00+09:00"
}
```

---

## 1. 인증·회원 (AU)

### POST `/api/auth/signup` — 회원가입 (AU-01) · 비로그인
요청
```json
{ "studentNo": "20231234", "name": "홍길동", "phone": "010-1234-5678", "password": "********", "privacyAgreed": true }
```
검증: 학번 숫자 8~10자리, 이름 1~50자(앞뒤 공백 제거), 전화번호 `01X-XXX(X)-XXXX`(하이픈 없이 입력해도 돼요. `010-1234-5678` 형식으로 저장), 비밀번호 8~64자(영문·숫자 모두 포함), `privacyAgreed`는 반드시 `true`(빠지거나 false면 `fieldErrors.privacyAgreed` "개인정보 수집·이용에 동의해 주세요.", NF-12). 동의 시각과 처리방침 버전을 저장해요.
같은 학번 동시 가입은 DB 제약(`uq_users_student_no_active`)으로 1건만 성공해요.
응답 `201` `UserResponse` `{ "id", "studentNo", "name", "phone", "role", "mustChangePassword" }`
에러: `400 VALIDATION_FAILED`, `409 DUPLICATE_STUDENT_NO`, `429 TOO_MANY_REQUESTS`

### POST `/api/auth/login` — 로그인 (AU-03) · 비로그인
요청 `{ "studentNo": "20231234", "password": "********" }`
응답 `200` `{ "accessToken": "…", "expiresIn": 7200, "user": UserResponse }`
에러: `401 LOGIN_FAILED` (학번 없음·비밀번호 틀림·비활성 계정 모두 같은 메시지. 학번이 없을 때도 비밀번호 해시 비교를 해서 응답 시간으로 가입 여부를 알 수 없게 해요)

임시 비밀번호(`mustChangePassword: true`)로 로그인하면 로그인은 성공하고, 그 뒤 `GET /api/me`·`PUT /api/me/password`와 비회원 공개 API 말고는 `403 PASSWORD_CHANGE_REQUIRED`예요 (AU-07).

> 로그아웃(AU-04)은 클라이언트가 토큰을 지워요. 액세스 토큰 유효 2시간, 리프레시 토큰은 1차에서 두지 않아요.

### GET `/api/me` — 내 정보 · 로그인
응답 `200` `UserResponse` (본인이라 전화번호 마스킹 없음)

### PATCH `/api/me` — 내 정보 수정 (AU-05) · 로그인
요청 `{ "name"?: "홍길동", "phone"?: "010-1234-5678" }` → `200 UserResponse` (보낸 값만 바꿔요. 학번·역할은 바꿀 수 없어요)

### PUT `/api/me/password` — 비밀번호 변경 (AU-05, AU-07) · 로그인
요청 `{ "currentPassword": "…", "newPassword": "…" }` → `204`
새 비밀번호 규칙은 가입과 같아요. 바꾸면 `mustChangePassword`가 풀려요.
에러: `400 VALIDATION_FAILED`(현재 비밀번호 불일치는 `fieldErrors.currentPassword`, 새 비밀번호가 현재와 같으면 `fieldErrors.newPassword`)

---

## 2. 현황판·공개 정보 (BD)

### GET `/api/board?date=2026-10-13` — 현황판 (BD-01~05) · 비로그인
- `date` 생략 시 오늘(KST). 오늘 ~ 오늘+`bookingWindowDays` 밖이면 `400 RESERVATION_OUT_OF_WINDOW`.
- **예약자 정보 필드는 없어요.** 쿼리는 방 목록 1회 + 그날 예약 1회 + 정책 조회로 상수 개예요.

응답 `200`
```json
{
  "date": "2026-10-13",
  "closed": false,
  "openAt": "2026-10-13T09:00:00+09:00",
  "closeAt": "2026-10-13T18:00:00+09:00",
  "periodName": null,
  "keyRequired": true,
  "rooms": [
    {
      "id": 1, "name": "1번방", "capacity": 4, "active": true,
      "blocks": [
        { "startAt": "2026-10-13T14:00:00+09:00", "endAt": "2026-10-13T16:00:00+09:00", "status": "RESERVED" }
      ]
    }
  ]
}
```
`blocks[].status`: `RESERVED` | `IN_USE` | `OVERDUE`. 비어 있는 시간은 화면에서 계산해요.

### GET `/api/rooms` — 방 목록 · 비로그인
응답 `200` `[{ "id", "name", "capacity", "active" }]` (sort_order 순, 목록이 작아 페이지 없음)

### GET `/api/policy` — 예약 규칙 안내 · 비로그인
응답 `200` `{ "slotMinutes": 30, "maxDurationMinutes": 120, "dailyLimit": 1, "bookingWindowDays": 7, "noShowGraceMinutes": 15, "keyIssueBeforeMinutes": 10, "penaltyWindowDays": 14, "penaltyThreshold": 2, "penaltyDays": 7 }` (예약 화면·예약 완료 안내 문구에 써요. 노쇼 제재 기준은 NS-02)

---

## 3. 예약 — 학생 (RV)

### POST `/api/reservations` — 예약 신청 (RV-01~08, RV-12) · 학생
요청
```json
{ "roomId": 1, "startAt": "2026-10-13T14:00:00+09:00", "endAt": "2026-10-13T16:00:00+09:00", "headcount": 3 }
```
응답 `201` `ReservationResponse` + `Location: /api/reservations/{id}`
에러: 검사 순서와 코드는 `docs/예약-상태-설계.md` 5장. (`400 VALIDATION_FAILED`, `404 ROOM_NOT_FOUND`, `409 ROOM_INACTIVE`, `400 HEADCOUNT_EXCEEDS_CAPACITY`, `400 RESERVATION_IN_PAST`, `400 RESERVATION_OUT_OF_WINDOW`, `400 OUTSIDE_OPERATING_HOURS`, `400 CLOSED_DAY`, `400 RESERVATION_TOO_LONG`, `403 USER_RESTRICTED`, `409 DAILY_LIMIT_EXCEEDED`, `409 RESERVATION_TIME_CONFLICT`, `409 USER_TIME_CONFLICT`)

### GET `/api/me/reservations?scope=upcoming&page=1&size=20` — 내 예약 목록 (RV-09) · 학생
- `scope`: `upcoming`(종료 전, 시작 오름차순, 기본) | `past`(종료 후 또는 취소·노쇼, 시작 내림차순)
응답 `200` `PageResponse<ReservationResponse>`

### GET `/api/reservations/{id}` — 예약 상세 (RV-09) · 학생(본인)
응답 `200` `ReservationResponse` · 남의 예약·없는 ID 모두 `404 RESERVATION_NOT_FOUND`

### POST `/api/reservations/{id}/check-in` — 이용 시작 (OP-02) · 학생(본인), 개방 기간 예약만
요청 본문 없음 → `200 ReservationResponse`
에러: `404 RESERVATION_NOT_FOUND`, `409 INVALID_RESERVATION_STATE`(키 예약이거나 RESERVED가 아님), `409 CHECK_IN_NOT_ALLOWED_TIME`, `409 CONCURRENT_UPDATE`

### POST `/api/reservations/{id}/finish` — 이용 종료 (OP-03) · 학생(본인), 개방 기간 예약만
요청 본문 없음 → `200 ReservationResponse`
에러: `404 RESERVATION_NOT_FOUND`, `409 INVALID_RESERVATION_STATE`

### POST `/api/reservations/{id}/cancel` — 본인 취소 (RV-10) · 학생(본인)
요청 본문 없음 → `200 ReservationResponse`
에러: `404 RESERVATION_NOT_FOUND`, `409 INVALID_RESERVATION_STATE`, `409 CANCEL_AFTER_START`

---

## 4. 관리자 (`/api/admin/**`, ADMIN만)

### 4.1 예약·키

| 메서드 | 경로 | 설명 | 요청 | 응답 | 주요 에러 |
|---|---|---|---|---|---|
| GET | `/api/admin/dashboard?date=` | 오늘 예약·이용 중·연체·노쇼 수와 처리 대기 목록 (AD-04) | — | `{ date, counts: {reserved, inUse, overdue, returned, completed, noShow, cancelled}, pendingIssue: [ReservationResponse], overdue: [ReservationResponse] }` | |
| GET | `/api/admin/reservations` | 예약 검색 (AD-02). `date`(그날) 또는 `from`~`to`(날짜, 둘 다 포함, 최대 92일), 둘 다 없으면 오늘. `roomId`, `status`, `q`(학번 앞부분·이름 일부), `page`, `size`. 시작 시각·방 순 (`outOfHours`(PO-04)는 운영 설정 화면과 함께 2차) | — | `PageResponse<ReservationResponse>` | 400(기간) |
| GET | `/api/admin/reservations/{id}` | 상세 + 키 이력 | — | `ReservationResponse` + `keyLogs: [KeyLogResponse]` | 404 |
| POST | `/api/admin/reservations/{id}/cancel` | 관리자 취소 (AD-03) | `{ "reason": "점검" }` (1~200자) | `200 ReservationResponse` | 400, 404, 409 `INVALID_RESERVATION_STATE`, 409 `CONCURRENT_UPDATE` |
| POST | `/api/admin/reservations/{id}/key-issue` | 키 지급 (KY-01, KY-02) | `{ "identityVerified": true, "memo"?: "" }` | `200 ReservationResponse` | 400 `IDENTITY_NOT_VERIFIED`, 409 `INVALID_RESERVATION_STATE`, 409 `KEY_ISSUE_NOT_ALLOWED_TIME`, 409 `CONCURRENT_UPDATE` |
| POST | `/api/admin/reservations/{id}/key-return` | 반납 확인 (KY-03). 본문 없이 보내도 돼요 | `{ "memo"?: "" }` | `200 ReservationResponse` (`returnedLate`: 종료 시각 뒤 반납) | 409 `INVALID_RESERVATION_STATE` |
| GET | `/api/admin/key-logs` | 키 이력 검색 (KY-07). `from`, `to`(날짜, 기본 최근 7일, 최대 92일), `roomId`, `q`, `page`, `size`. 최근 순 | — | `PageResponse<KeyLogResponse>` | 400(기간) |

`KeyLogResponse`: `{ id, reservationId, roomName, studentNo, studentName, action, adminName, identityVerified, late, memo, occurredAt }`

> 4.2 방·4.3 운영 정책·특별 운영 기간 API는 관리 화면과 함께 2차로 구현해요(시범운영 중 변경은 서버 담당자가 DB로 반영, `docs/운영-매뉴얼.md` 6장).

### 4.2 방 (AD-05)

| 메서드 | 경로 | 요청 | 응답 |
|---|---|---|---|
| GET | `/api/admin/rooms` | — | `[RoomResponse]` (비활성 포함, `sortOrder` 포함) |
| POST | `/api/admin/rooms` | `{ name, capacity, sortOrder }` | `201 RoomResponse` |
| PATCH | `/api/admin/rooms/{id}` | `{ name?, capacity?, sortOrder?, active? }` | `200 RoomResponse` |

방 삭제 API는 두지 않아요(비활성화로 대신). 정원을 줄여도 기존 예약은 유지하고, 응답에 `affectedReservations`(정원 초과 예약 수)를 함께 줘요.

### 4.3 운영 정책·특별 운영 기간 (AD-06, PO-01~04)

| 메서드 | 경로 | 요청 | 응답 | 에러 |
|---|---|---|---|---|
| GET | `/api/admin/policy` | — | `PolicyResponse` | |
| PUT | `/api/admin/policy` | `PolicyResponse`의 값 전체 (`defaultOpen: "09:00"`, `defaultClose: "18:00"` 등, 마감 `"24:00"` 허용) | `200 PolicyResponse` | 400 |
| GET | `/api/admin/policy/history?page=` | — | `PageResponse<{ id, target, targetId, changedBy, before, after, createdAt }>` | |
| GET | `/api/admin/operation-periods?from=&to=` | — | `[PeriodResponse]` | |
| POST | `/api/admin/operation-periods` | `{ name, startDate, endDate, closed, keyRequired, open?: "09:00", close?: "24:00" }` | `201 PeriodResponse` | 400, 409 `PERIOD_OVERLAP` |
| PUT | `/api/admin/operation-periods/{id}` | 위와 같음 | `200 PeriodResponse` | 404, 409 |
| DELETE | `/api/admin/operation-periods/{id}` | — | `204` | 404 |

`PolicyResponse`: `{ defaultOpen, defaultClose, maxDurationMinutes, dailyLimit, bookingWindowDays, keyIssueBeforeMinutes, noShowGraceMinutes, penaltyWindowDays, penaltyThreshold, penaltyDays, updatedAt, updatedBy }`
정책·기간을 바꿔 운영 시간 밖이 된 기존 예약은 그대로 두고, 응답에 `outOfHoursReservations` 수를 함께 줘요(PO-04).

### 4.4 학생·이용 제한 (AD-07, AD-08, NS-03)

| 메서드 | 경로 | 요청 | 응답 | 비고 |
|---|---|---|---|---|
| GET | `/api/admin/users?q=&status=&page=&size=` | — | `PageResponse<AdminUserResponse>` | `q`: 학번 앞부분 또는 이름 일부, `status`: `ACTIVE`·`DISABLED`(생략 시 전체), 학번 오름차순. `{ id, studentNo, name, phone, role, status, mustChangePassword, recentNoShowCount, restrictedUntil, createdAt }` — `recentNoShowCount`는 최근 `penalty_window_days`일 노쇼 수, `restrictedUntil`은 해제되지 않은 이용 제한의 종료 시각(없으면 null) |
| POST | `/api/admin/users/{id}/password-reset` | `{ "reason": "본인 방문 확인" }` (1~200자) | `200 { "temporaryPassword": "…" }` | AU-07. 임시 비밀번호(영문·숫자 10자)는 이 응답에서만 보여요(`Cache-Control: no-store`). 로그·감사 기록에 남기지 않아요. 비활성 계정은 `404 USER_NOT_FOUND` |
| POST | `/api/admin/users/{id}/disable` | `{ "reason": "…" }` (1~200자) | `200 AdminUserResponse` | AU-08. 이미 비활성이면 그대로 200(기록 추가 없음). 본인 계정은 `400 CANNOT_DISABLE_SELF`. 키 받기 전(RESERVED) 예약은 사유 "계정 비활성화"로 함께 취소해요 |
| GET | `/api/admin/restrictions?active=true&page=` | — | `PageResponse<RestrictionResponse>` | `active` 기본 true(지금 유효한 제한만), false면 전체. 최근 시작 순. `RestrictionResponse`: `{ id, userId, studentNo, studentName, startAt, endAt, reason, active, liftedAt, liftReason }` |
| POST | `/api/admin/restrictions/{id}/lift` | `{ "reason": "…" }` (1~200자) | `200 RestrictionResponse` | 이미 해제됐으면 409 `INVALID_RESTRICTION_STATE`, 없으면 404 `RESTRICTION_NOT_FOUND`. 감사 기록 `RESTRICTION_LIFT` |

---

## 5. 요구사항 ↔ API

| 요구사항 | API |
|---|---|
| AU-01, 03~05, 07 | `/api/auth/*`, `/api/me*` |
| AU-07, AU-08, AD-08 | `/api/admin/users/{id}/password-reset`, `/disable` |
| BD-01~05 | `GET /api/board` |
| RV-01~12 | `/api/reservations*`, `/api/me/reservations` |
| KY-01~07 | `/api/admin/reservations/{id}/key-*`, `/api/admin/key-logs` |
| OP-01~05 | `/api/reservations/{id}/check-in`, `/finish`, `GET /api/board`의 `keyRequired` |
| NS-01~02 | 스케줄러 (API 없음) |
| NS-03 | `/api/admin/restrictions*` |
| AD-02~06, PO-01~04 | `/api/admin/*` |

## 6. 변경 이력
| 버전 | 날짜 | 내용 |
|---|---|---|
| v0.1 | 2026-10-12 | 최초 작성 |
| v0.8 | 2026-10-21 | 2~4단계 구현과 맞춤: 현황판·예약·키·노쇼·이용 제한 API 구현, 가입 `privacyAgreed`(NF-12), `429 TOO_MANY_REQUESTS`, 관리자 예약·키 이력 검색 기간 규칙(최대 92일), 반납 본문 생략 가능, 방·운영 정책 관리 API는 2차 |
| v0.7 | 2026-10-16 | 에러 규칙: 동시 INSERT 교착(`40P01`)·직렬화 실패(`40001`)도 409(`RESERVATION_TIME_CONFLICT`/`CONCURRENT_UPDATE`), 매핑 안 된 제약 위반 로그 원칙 (TS-16) |
| v0.6 | 2026-10-13 | `GET /api/policy`에 노쇼 제재 기준(`penaltyWindowDays`·`penaltyThreshold`·`penaltyDays`) 추가 — 예약 완료 화면에 노쇼 원칙 안내 |
| v0.5 | 2026-10-13 | `GET /api/policy`에 `keyIssueBeforeMinutes` 추가 (설문용 데모 화면 안내 문구) |
| v0.4 | 2026-10-12 | 1단계(회원·인증) 세부 확정: 전화번호 정규화, 임시 비밀번호 상태 허용 API, 회원 검색 조건, `recentNoShowCount`, `CANNOT_DISABLE_SELF` |
| v0.3 | 2026-10-12 | 0단계 구현과 맞춤: `CLOSED_DAY`를 `OUTSIDE_OPERATING_HOURS`에서 분리, 공통 `NOT_FOUND`·`METHOD_NOT_ALLOWED` 추가 |
| v0.2 | 2026-10-12 | 개방 기간(D-9): check-in·finish API, `keyRequired`·`COMPLETED`, `CHECK_IN_NOT_ALLOWED_TIME` 추가 |
