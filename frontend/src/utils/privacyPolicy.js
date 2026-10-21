/**
 * 개인정보 처리방침 (NF-11). 가입 화면의 동의 체크가 이 내용을 가리켜요.
 * 수집 항목·목적·보관 기간을 바꾸면 PRIVACY_POLICY_VERSION과 백엔드 PrivacyPolicy.CURRENT_VERSION을 함께 올려요
 * → users.privacy_policy_version에 누가 어느 버전에 동의했는지 남아요.
 */
export const PRIVACY_POLICY_VERSION = '2026-10-21'

/** 가입 화면에도 그대로 보여주는 요약 (항목·목적·보관 기간) */
export const PRIVACY_SUMMARY = [
  { label: '수집 항목', value: '학번, 이름, 대표 전화번호, 비밀번호(암호화해서 저장)' },
  { label: '이용 목적', value: '스터디룸 예약·이용 관리, 키 지급 때 학생증과 대조하는 본인 확인, 반납 지연·분실 때 연락' },
  { label: '보관 기간', value: '회원 탈퇴 요청 또는 시범운영 종료 후 30일 안에 파기 (학교 정식 운영으로 넘어가면 학교 지침을 따라요)' },
]
