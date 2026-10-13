import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { login } from '../api/auth'
import { DEMO_STUDENT, errorMessage, fieldErrorsOf, IS_DEMO } from '../api/client'
import { useAuth } from '../auth/useAuth'
import Alert from '../components/Alert.jsx'
import FormField from '../components/FormField.jsx'
import { safeNext } from '../utils/format'

/** AU-03 로그인. 비밀번호 분실은 학과실에서 초기화해요 (AU-07) */
export default function LoginPage() {
  const { signIn } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [form, setForm] = useState({ studentNo: '', password: '' })
  const [error, setError] = useState(null)
  const [fieldErrors, setFieldErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)

  const change = (e) => setForm({ ...form, [e.target.name]: e.target.value })

  async function submit(e) {
    e.preventDefault()
    await signInWith(form)
  }

  async function signInWith(credentials) {
    setSubmitting(true)
    setError(null)
    setFieldErrors({})
    try {
      const res = await login(credentials)
      signIn(res)
      navigate(res.user.mustChangePassword ? '/me/password' : safeNext(params.get('next')), { replace: true })
    } catch (err) {
      setError(errorMessage(err))
      setFieldErrors(fieldErrorsOf(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="mx-auto max-w-sm">
      <h1 className="text-xl font-bold">로그인</h1>
      {IS_DEMO && (
        <div className="mt-4 rounded-lg border border-indigo-200 bg-indigo-50 p-4">
          <p className="text-sm text-indigo-900">회원가입 없이 바로 체험해 볼 수 있어요.</p>
          <button type="button" disabled={submitting} onClick={() => signInWith(DEMO_STUDENT)}
            className="mt-3 w-full rounded-md bg-indigo-600 py-2.5 font-medium text-white disabled:opacity-50">
            데모 학생으로 바로 시작하기
          </button>
          <p className="mt-2 text-center text-xs text-indigo-800">또는 아래에서 직접 가입한 학번으로 로그인해요</p>
        </div>
      )}
      <form onSubmit={submit} className="mt-6 space-y-4" noValidate>
        <FormField label="학번" name="studentNo" inputMode="numeric" autoComplete="username"
          value={form.studentNo} onChange={change} error={fieldErrors.studentNo} />
        <FormField label="비밀번호" name="password" type="password" autoComplete="current-password"
          value={form.password} onChange={change} error={fieldErrors.password} />
        <Alert>{error}</Alert>
        <button type="submit" disabled={submitting}
          className="w-full rounded-md bg-indigo-600 py-2.5 font-medium text-white disabled:opacity-50">
          {submitting ? '로그인 중…' : '로그인'}
        </button>
      </form>
      <p className="mt-6 text-center text-sm text-gray-600">
        처음이신가요? <Link to="/signup" className="text-indigo-600 underline">회원가입</Link>
      </p>
      <p className="mt-2 text-center text-xs text-gray-500">비밀번호를 잊었으면 학생증을 가지고 학과실에 문의해 주세요.</p>
    </section>
  )
}
