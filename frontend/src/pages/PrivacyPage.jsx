import { Link } from 'react-router'
import { PRIVACY_POLICY_VERSION, PRIVACY_SUMMARY } from '../utils/privacyPolicy'

const ROWS = [
  ...PRIVACY_SUMMARY,
  {
    label: '함께 남는 기록',
    value: '예약(방·시간·인원·상태), 키 지급·반납(처리한 관리자, 시각, 학생증 확인 여부), 노쇼·이용 제한, 동의 시각',
  },
  { label: '받지 않는 정보', value: '학생증·신분증 사진, 주민등록번호, 이메일 — 본인 확인은 학과실에서 실물 학생증으로 해요' },
  {
    label: '볼 수 있는 사람',
    value: '본인과 학과실 관리자(조교·근로학생)만 봐요. 로그인 없이 보는 현황판에는 방·시간·상태만 나오고 예약자 정보는 없어요.',
  },
  { label: '보호 조치', value: '접속 구간 암호화(HTTPS), 비밀번호 단방향 암호화, 관리자 권한 분리, 관리자 조치 기록' },
  {
    label: '동의를 거부할 권리',
    value: '동의하지 않을 수 있어요. 이 경우 온라인 예약은 할 수 없지만, 지금처럼 학과실에 방문해서 이용할 수 있어요.',
  },
  {
    label: '열람·정정·삭제',
    value: '내 정보 화면에서 이름·전화번호를 바로 고칠 수 있어요. 탈퇴(삭제)는 학과실에 요청하면 계정을 닫고 정보를 파기해요.',
  },
  { label: '문의', value: 'IT융합대학 학과 사무실 (M-SPACE 관리)' },
]

/** 개인정보 처리방침 화면 (NF-11). 가입 화면의 동의 체크가 이 내용을 가리켜요 */
export default function PrivacyPage() {
  return (
    <section className="mx-auto max-w-xl">
      <h1 className="text-xl font-bold">개인정보 처리방침</h1>
      <p className="mt-2 text-sm text-gray-600">
        M-SPACE 스터디룸 예약 시스템이 어떤 정보를 왜 받고, 언제 지우는지 알려드려요. (시행 {PRIVACY_POLICY_VERSION})
      </p>
      <dl className="mt-6 divide-y divide-gray-100 rounded-lg border border-gray-200 bg-white">
        {ROWS.map((row) => (
          <div key={row.label} className="px-4 py-3">
            <dt className="text-sm font-semibold text-gray-800">{row.label}</dt>
            <dd className="mt-1 text-sm leading-relaxed text-gray-600">{row.value}</dd>
          </div>
        ))}
      </dl>
      <p className="mt-6 text-center text-sm">
        <Link to="/signup" className="text-indigo-600 underline">
          회원가입으로 돌아가기
        </Link>
      </p>
    </section>
  )
}
