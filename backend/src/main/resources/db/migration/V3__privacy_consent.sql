-- 가입할 때 받은 개인정보 수집·이용 동의 기록 (AU-01, NF-11, 2026-10-21)
-- 학교 시범운영 검토에서 총무팀이 수집·이용·보관 근거를 확인해요. 언제, 어떤 처리방침 버전에 동의했는지 남겨요.
-- 이 마이그레이션 전에 가입한 계정(설문 기간 테스트 계정)은 NULL이고, 다음 로그인 때 다시 동의를 받는 기능은 2차예요.
ALTER TABLE users ADD COLUMN privacy_agreed_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN privacy_policy_version VARCHAR(20);
