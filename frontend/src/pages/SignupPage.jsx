import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { login, signup } from '../api/auth'
import { errorMessage, fieldErrorsOf, IS_DEMO } from '../api/client'
import { useAuth } from '../auth/useAuth'
import Alert from '../components/Alert.jsx'
import FormField from '../components/FormField.jsx'
import { PRIVACY_SUMMARY } from '../utils/privacyPolicy'

/** AU-01 회원가입. 가입하면 바로 로그인해요. 이메일은 받지 않아요 (D-2) */
export default function SignupPage() {
  const { signIn } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ studentNo: '', name: '', phone: '', password: '', privacyAgreed: false })
  const [error, setError] = useState(null)
  const [fieldErrors, setFieldErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)

  const change = (e) =>
    setForm({ ...form, [e.target.name]: e.target.type === 'checkbox' ? e.target.checked : e.target.value })

  async function submit(e) {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    setFieldErrors({})
    try {
      await signup(form)
      signIn(await login({ studentNo: form.studentNo, password: form.password }))
      navigate('/', { replace: true })
    } catch (err) {
      setError(errorMessage(err))
      setFieldErrors(fieldErrorsOf(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="mx-auto max-w-sm">
      <h1 className="text-xl font-bold">회원가입</h1>
      <p className="mt-2 text-sm text-gray-600">예약할 때마다 쓰던 학번·이름·전화번호를 한 번만 등록해요.</p>
      {IS_DEMO && (
        <div className="mt-3">
          <Alert kind="info">체험용 데모예요. 실제 학번·전화번호 대신 아무 숫자나 넣어 주세요. (예: 20991234, 010-0000-0000)</Alert>
        </div>
      )}
      <form onSubmit={submit} className="mt-6 space-y-4" noValidate>
        <FormField label="학번" name="studentNo" inputMode="numeric" autoComplete="username"
          value={form.studentNo} onChange={change} error={fieldErrors.studentNo} hint="숫자 8~10자리" />
        <FormField label="이름" name="name" autoComplete="name" value={form.name} onChange={change} error={fieldErrors.name} />
        <FormField label="대표 전화번호" name="phone" type="tel" inputMode="tel" autoComplete="tel"
          value={form.phone} onChange={change} error={fieldErrors.phone} hint="010-1234-5678" />
        <FormField label="비밀번호" name="password" type="password" autoComplete="new-password"
          value={form.password} onChange={change} error={fieldErrors.password} hint="영문과 숫자를 모두 넣어 8자 이상" />
        {/* 개인정보 수집·이용 동의 (NF-11): 항목·목적·보관 기간을 체크 바로 위에 보여줘요 */}
        <fieldset className="rounded-md border border-gray-200 bg-white p-3">
          <legend className="px-1 text-sm font-medium text-gray-700">개인정보 수집·이용 동의 (필수)</legend>
          <dl className="space-y-1.5 text-xs text-gray-600">
            {PRIVACY_SUMMARY.map((row) => (
              <div key={row.label}>
                <dt className="inline font-semibold text-gray-700">{row.label}: </dt>
                <dd className="inline">{row.value}</dd>
              </div>
            ))}
          </dl>
          <p className="mt-2 text-xs text-gray-500">
            동의하지 않으면 온라인 예약은 할 수 없고, 학과실 방문으로 이용할 수 있어요.{' '}
            <Link to="/privacy" target="_blank" rel="noreferrer" className="text-indigo-600 underline">
              처리방침 전체 보기
            </Link>
          </p>
          <label className="mt-3 flex items-center gap-2 text-sm">
            <input id="field-privacyAgreed" type="checkbox" name="privacyAgreed" checked={form.privacyAgreed}
              onChange={change} aria-invalid={Boolean(fieldErrors.privacyAgreed)}
              aria-describedby={fieldErrors.privacyAgreed ? 'field-privacyAgreed-error' : undefined}
              className="size-4 accent-indigo-600" />
            위 내용을 확인했고 동의해요.
          </label>
          {fieldErrors.privacyAgreed && (
            <p id="field-privacyAgreed-error" className="mt-1 text-xs text-red-600">{fieldErrors.privacyAgreed}</p>
          )}
        </fieldset>
        <Alert>{error}</Alert>
        <button type="submit" disabled={submitting}
          className="w-full rounded-md bg-indigo-600 py-2.5 font-medium text-white disabled:opacity-50">
          {submitting ? '가입 중…' : '가입하기'}
        </button>
      </form>
      <p className="mt-4 text-xs text-gray-500">
        키를 받을 때 학생증의 학번·이름이 가입 정보와 같은지 확인해요. 학생증 사진은 받지 않아요.
      </p>
      <p className="mt-4 text-center text-sm text-gray-600">
        이미 가입했나요? <Link to="/login" className="text-indigo-600 underline">로그인</Link>
      </p>
    </section>
  )
}
