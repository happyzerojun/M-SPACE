# Changelog

이 프로젝트의 주요 변경 사항을 적어요.
형식은 [Keep a Changelog](https://keepachangelog.com/ko/1.1.0/)를, 버전은 [Semantic Versioning](https://semver.org/lang/ko/)을 따라요.

- 작업 브랜치를 `develop`에 합칠 때 `[Unreleased]`에 한 줄씩 추가해요.
- 릴리즈할 때 `[Unreleased]` 내용을 새 버전 제목 아래로 옮겨요.
- 분류: `Added`(새 기능), `Changed`(바뀐 동작), `Deprecated`, `Removed`, `Fixed`(버그 수정), `Security`

## [Unreleased]

## [0.3.0] - 2026-10-13

### Added
- 숙박 앱형 예약 화면: 홈 방 카드(지금 상태·빈 시간 칩) → 방 화면(날짜·시작 시각 격자·이용 시간·인원) → 아래 고정 "예약하기"
- 예약 완료 카드: 예약 내용과 노쇼 원칙(키 수령 시간, 시작 15분 후 노쇼, 2주 2회 → 7일 제한, 미리 취소하면 노쇼 아님, 반납) 안내 (NT-03). 숫자는 운영 정책 값 사용
- 아래 고정 메뉴(홈·내 예약·관리·내 정보), 방별 색 구분, 예약 직후·홈 아래 설문 참여 카드, 데모 배너에 구글폼 연결

### Changed
- 설문 소요 시간 안내 3분 → 1분 (설문 카드·폼 설명)
- 예약 화면 클릭 수: 빈 시간 칩 → 예약하기(2번). 이용 시간 기본값은 가능한 최대(2시간), 고른 값은 주소에 남겨 로그인 뒤에도 유지
- 예전 예약 주소 `/reserve`는 방 화면으로 넘겨요
- 1회 최대 이용 시간 3시간 → **2시간** (IT융합대학 M-SPACE 스터디룸 공식 규정): V2 마이그레이션, 데모 정책·예약 데이터, 설문 Q9 문구

## [0.2.0] - 2026-10-13

### Added
- 설문용 프론트 데모(`VITE_DEMO`): 브라우저 안의 가짜 서버로 현황판(30초 갱신)·예약 신청·내 예약(취소·개방 기간 이용 시작·종료)·관리자 예약·키(학생증 확인 후 지급·반납·취소) 체험, Vercel 배포 설정
- 설문용 데모 배포: https://m-space-rho.vercel.app
- 데모: 회원가입 없이 "데모 학생으로 바로 시작하기", 관리자 계정 안내는 화면에서 숨김 (학생 대상 설문)
- 학생 대상 구글폼 문항 초안 (`docs/설문-문항.md`)과 폼·응답 시트 자동 생성 Apps Script (`scripts/create-survey-form.gs`)
- 회원가입·로그인·내 정보 수정·비밀번호 변경 (AU-01~05), 임시 비밀번호 로그인 시 비밀번호 변경 강제 (AU-07)
- 관리자 회원 검색·비밀번호 초기화·계정 비활성화와 감사 기록 (AD-07, AD-08, AU-07, AU-08)
- 로그인·가입·내 정보·비밀번호 변경·관리자 회원 관리 화면, 로그인 상태 유지
- 테스트: 백엔드 통합 테스트 70건(동시 가입 포함), e2e 회원·인증 점검
- GitHub 공개 저장소 연결(기본 브랜치 `develop`, `main`·`develop` 보호, merge commit만 허용), v0.1.0 GitHub Release
- 트러블슈팅 TS-08(`gh` 없이 GitHub API 사용), TS-09(커밋 작성자 설정)

## [0.1.0] - 2026-10-12

### Added
- 요구사항·ERD·예약 상태 설계·API 명세 문서 (결정 D-2~D-5, D-9 반영, 시험기간 종일 개방)
- 백엔드 뼈대: Spring Boot 4.1, 공통 에러 규격, JWT 인증, 관리자 주소 보호, KST 시간 처리, 페이지 응답
- DB V1 스키마: 예약 시간 겹침 금지(EXCLUDE), 상태 컬럼 CHECK, 키 이력 수정·삭제 금지, 방 5개·운영 정책 초기 데이터
- 프론트엔드 뼈대: React 19, Vite 8, react-router 8, Tailwind 4, oxlint
- 테스트 기반: Testcontainers PostgreSQL 통합 테스트 35건, e2e 375px 레이아웃 점검
- 로컬 개발 환경: docker compose(`mspace-postgres`, 포트 5433), `.env.example`
- Git Flow 브랜치 전략, 버전 관리(SemVer·CHANGELOG), PR 템플릿, 트러블슈팅 기록 문서

[Unreleased]: ../../compare/v0.3.0...develop
[0.3.0]: ../../compare/v0.2.0...v0.3.0
[0.2.0]: ../../compare/v0.1.0...v0.2.0
[0.1.0]: ../../releases/tag/v0.1.0
