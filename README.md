# M-SPACE 예약 시스템

IT융합대학 M-SPACE실(스터디룸)의 실시간 이용 현황 조회·온라인 예약과 학과실의 키 대여·반납 기록을 전산화하는 서비스예요.

- 요구사항: [`요구사항.md`](요구사항.md)
- 설계: [`docs/ERD.md`](docs/ERD.md) · [`docs/예약-상태-설계.md`](docs/예약-상태-설계.md) · [`docs/API명세서.md`](docs/API명세서.md)
- 개발 규칙: [`CLAUDE.md`](CLAUDE.md)

## 기술 스택

| 영역 | 기술 |
|---|---|
| 백엔드 | Java 21, Spring Boot 4.1, Spring Security 7, Hibernate 7, Jackson 3, Flyway, jjwt 0.12 |
| 프론트엔드 | React 19, Vite 8, react-router 8, axios, Tailwind 4, oxlint |
| DB | PostgreSQL 17 (`btree_gist`) |
| 테스트 | JUnit 5 + MockMvc + Testcontainers(PostgreSQL), 화면은 `e2e/`(헤드리스 Chrome) |

## 설문용 데모

백엔드 없이 화면만으로 동작하는 체험판이에요 (데이터는 방문자 브라우저에만 저장).

**데모 주소: https://m-space-rho.vercel.app** (Vercel, `develop`에 병합하면 자동 배포)

```bash
cd frontend && npm run dev:demo    # 로컬에서 보기
cd frontend && npm run build:demo  # Vercel 배포용 빌드 (frontend/vercel.json)
```

- 학생: 로그인 화면의 "데모 학생으로 바로 시작하기" (회원가입 없이 체험, 가짜 계정 `20991111`)
- 관리자 화면 체험 계정: 학번 `20990000` / 비밀번호 `demo1234` (가짜 계정). **학생 대상 설문이라 화면에는 안내하지 않아요.** 학과실에 보여 줄 때만 써요
- 설문 문항: [`docs/설문-문항.md`](docs/설문-문항.md), 구글폼 자동 생성: [`scripts/create-survey-form.gs`](scripts/create-survey-form.gs) (Apps Script), 설문 주소는 `frontend/.env.demo`의 `VITE_SURVEY_URL`

## 로컬 실행

필요한 것: JDK 21, Node 24, Docker, Chrome(화면 점검용)

```bash
cp .env.example .env        # DB_PASSWORD, JWT_SECRET(32바이트 이상)을 바꿔요. .env는 커밋하지 않아요
docker compose up -d        # mspace-postgres (호스트 포트 5433)
cd backend && ./gradlew bootRun          # API :8080 (첫 실행 때 Flyway가 스키마와 방 5개·운영 정책을 넣어요)
cd frontend && npm install && npm run dev  # 화면 :5173 (/api → 8080 프록시)
```

포트가 겹치면 `SERVER_PORT=8081 ./gradlew bootRun`, `API_TARGET=http://localhost:8081 npm run dev -- --port 5175`처럼 바꿔서 띄워요. 띄우기 전에 `lsof -nP -iTCP:<포트> -sTCP:LISTEN`으로 비어 있는지 확인해요.

## 검증

```bash
cd backend && ./gradlew test                 # Docker 필요 (Testcontainers)
cd frontend && npm run lint && npm run build
node e2e/run-all.mjs                         # BASE=http://localhost:5174 처럼 대상 주소를 바꿀 수 있어요
```

## 브랜치·버전

- Git Flow: `main`(릴리즈) · `develop`(통합) · `feature/*` · `fix/*` · `release/*` · `hotfix/*`. 자세한 규칙은 `CLAUDE.md` 12장
- 커밋은 Conventional Commits, 버전은 SemVer 태그(`v0.1.0`), 변경 내역은 [`CHANGELOG.md`](CHANGELOG.md)
- 저장소: https://github.com/happyzerojun/M-SPACE (기본 브랜치 `develop`, 릴리즈는 `main`의 태그)
- 막혔던 문제와 해결 방법: [`docs/트러블슈팅.md`](docs/트러블슈팅.md)

## 폴더

```
backend/   Spring Boot API (com.mspace.{auth,user,room,reservation,key,policy,admin,global})
frontend/  React 화면 (src/{api,auth,components,hooks,pages,utils})
e2e/       화면 점검 (설치 없이 Chrome DevTools Protocol 사용)
docs/      설계 문서
```

## 진행 상황

| 단계 | 내용 | 상태 |
|---|---|---|
| 0 | 프로젝트 뼈대, 공통 기반(에러·JWT·보안·KST), V1 스키마, 테스트·e2e 기반, Git Flow·버전 관리 | 완료 (`v0.1.0`) |
| 1 | 회원·인증 (가입·로그인·내 정보·관리자 회원 관리) | 완료 |
| 데모 | 설문용 프론트 데모: 현황판·예약·내 예약·개방 기간 이용 시작·관리자 키 지급 (가짜 서버) | 완료 |
| 2~ | 현황판 → 예약 → 키·노쇼 → 관리자 백엔드 → 배포 | 예정 (설문 결과 반영) |
