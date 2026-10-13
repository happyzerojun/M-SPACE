-- 1회 최대 이용 시간을 공식 규정(IT융합대학 M-SPACE 스터디룸: 1회 예약 최대 2시간)에 맞춰 3시간 → 2시간 (RV-05, D-4)
-- V1은 이미 적용된 환경이 있어서 고치지 않고 값만 바꿔요 (Flyway는 추가만)
UPDATE operation_policy SET max_duration_minutes = 120, updated_at = now() WHERE id = 1;
