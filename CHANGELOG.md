# Changelog

이 프로젝트의 주요 변경 사항을 적어요.
형식은 [Keep a Changelog](https://keepachangelog.com/ko/1.1.0/)를, 버전은 [Semantic Versioning](https://semver.org/lang/ko/)을 따라요.

- 작업 브랜치를 `develop`에 합칠 때 `[Unreleased]`에 한 줄씩 추가해요.
- 릴리즈할 때 `[Unreleased]` 내용을 새 버전 제목 아래로 옮겨요.
- 분류: `Added`(새 기능), `Changed`(바뀐 동작), `Deprecated`, `Removed`, `Fixed`(버그 수정), `Security`

## [Unreleased]

### Added
- 요구사항·ERD·예약 상태 설계·API 명세 문서 (결정 D-2~D-5, D-9 반영, 시험기간 종일 개방)
- 백엔드 뼈대: Spring Boot 4.1, 공통 에러 규격, JWT 인증, 관리자 주소 보호, KST 시간 처리, 페이지 응답
- DB V1 스키마: 예약 시간 겹침 금지(EXCLUDE), 상태 컬럼 CHECK, 키 이력 수정·삭제 금지, 방 5개·운영 정책 초기 데이터
- 프론트엔드 뼈대: React 19, Vite 8, react-router 8, Tailwind 4, oxlint
- 테스트 기반: Testcontainers PostgreSQL 통합 테스트 35건, e2e 375px 레이아웃 점검
- 로컬 개발 환경: docker compose(`mspace-postgres`, 포트 5433), `.env.example`
- Git Flow 브랜치 전략, 버전 관리(SemVer·CHANGELOG), PR 템플릿, 트러블슈팅 기록 문서

