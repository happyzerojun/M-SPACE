-- 개인정보 파기 절차 (NF-11, 개인정보 처리방침 "보관 기간")
-- 실행: docker exec -i mspace-postgres psql -U mspace -d mspace -v ON_ERROR_STOP=1 < scripts/purge-personal-data.sql
-- 이 파일은 되돌릴 수 없는 작업이에요. 실행 전에 반드시 백업(scripts/backup-db.sh)을 받고, 학과 담당자와 날짜를 정한 뒤 실행해요.
-- 기본은 1번(탈퇴 계정 파기)만 실행되고, 2번(시범운영 종료 전체 파기)은 주석을 풀어야 실행돼요.

BEGIN;

-- ─────────────────────────────────────────────
-- 1. 탈퇴(비활성화) 계정의 개인정보 파기
--    학과실에 탈퇴를 요청하면 관리자가 계정을 비활성화하고(키 받기 전 예약은 자동 취소), 이 단계로 이름·학번·전화번호를 지워요.
--    예약·키 기록은 "몇 시에 몇 번방이 쓰였는지"라는 운영 기록으로 남지만, 누구인지는 알 수 없게 돼요.
--    학번 자리에는 겹치지 않는 표시값(DELETED-회원번호)을 넣어요(학번 유니크는 활성 계정에만 걸려 있어요).
-- ─────────────────────────────────────────────
UPDATE users
SET student_no             = 'DELETED-' || id,
    name                   = '파기된 회원',
    phone                  = '000-0000-0000',
    password_hash          = 'purged',
    privacy_agreed_at      = NULL,
    privacy_policy_version = NULL,
    updated_at             = now()
WHERE status = 'DISABLED'
  AND student_no NOT LIKE 'DELETED-%';

-- 관리자 조치 사유에 개인정보가 섞였을 수 있어서, 파기한 회원을 대상으로 한 기록의 사유도 지워요
UPDATE admin_audit_log
SET reason = '(파기)'
WHERE target_type = 'USER'
  AND target_id IN (SELECT id FROM users WHERE student_no LIKE 'DELETED-%');

-- ─────────────────────────────────────────────
-- 2. 시범운영 종료 시 전체 파기 (학교 정식 운영으로 넘기지 않을 때)
--    회원·예약·키 기록·이용 제한·감사 기록을 모두 지워요. 방과 운영 정책 설정만 남아요.
--    실행하려면 아래 블록의 주석을 풀어요.
-- ─────────────────────────────────────────────
-- TRUNCATE key_log, reservation, user_restriction, policy_change_log, admin_audit_log RESTART IDENTITY;
-- UPDATE operation_policy SET updated_by = NULL;
-- DELETE FROM users;

COMMIT;

-- 확인: 남은 개인정보가 없는지 (1번 뒤에는 비활성 계정이 모두 DELETED-로, 2번 뒤에는 0건이어야 해요)
SELECT status, count(*) AS accounts, count(*) FILTER (WHERE student_no LIKE 'DELETED-%') AS purged
FROM users GROUP BY status;
