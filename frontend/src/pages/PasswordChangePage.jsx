import { useState } from 'react'
import { useNavigate } from 'react-router'
import { changePassword, fetchMe } from '../api/auth'
import { errorMessage, fieldErrorsOf } from '../api/client'
import { useAuth } from '../auth/useAuth'
import Alert from '../components/Alert.jsx'
import FormField from '../components/FormField.jsx'

/** AU-05 비밀번호 변경. 임시 비밀번호 상태(AU-07)면 여기서 바꿔야 다른 기능을 쓸 수 있어요 */
export default function PasswordChangePage() {
  const { user, setUser } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ currentPassword: '', newPassword: '', confirm: '' })
  const [error, setError] = useState(null)
  const [fieldErrors, setFieldErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)

  const change = (e) => setForm({ ...form, [e.target.name]: e.target.value })

  async function submit(e) {
    e.preventDefault()
    setError(null)
    if (form.newPassword !== form.confirm) {
      setFieldErrors({ confirm: '새 비밀번호가 서로 달라요.' })
      return
    }
    setSubmitting(true)
    setFieldErrors({})
    try {
      await changePassword({ currentPassword: form.currentPassword, newPassword: form.newPassword })
      setUser(await fetchMe())
      navigate(user.mustChangePassword ? '/' : '/me', { replace: true })
    } catch (err) {
      setError(errorMessage(err))
      setFieldErrors(fieldErrorsOf(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="mx-auto max-w-sm">
      <h1 className="text-xl font-bold">비밀번호 변경</h1>
      {user.mustChangePassword && (
        <div className="mt-4">
          <Alert kind="info">임시 비밀번호로 로그인했어요. 다른 기능을 쓰기 전에 새 비밀번호로 바꿔 주세요.</Alert>
        </div>
      )}
      <form onSubmit={submit} className="mt-6 space-y-4" noValidate>
        <FormField label={user.mustChangePassword ? '임시 비밀번호' : '현재 비밀번호'} name="currentPassword"
          type="password" autoComplete="current-password"
          value={form.currentPassword} onChange={change} error={fieldErrors.currentPassword} />
        <FormField label="새 비밀번호" name="newPassword" type="password" autoComplete="new-password"
          value={form.newPassword} onChange={change} error={fieldErrors.newPassword} hint="영문과 숫자를 모두 넣어 8자 이상" />
        <FormField label="새 비밀번호 확인" name="confirm" type="password" autoComplete="new-password"
          value={form.confirm} onChange={change} error={fieldErrors.confirm} />
        <Alert>{error}</Alert>
        <button type="submit" disabled={submitting}
          className="w-full rounded-md bg-indigo-600 py-2.5 font-medium text-white disabled:opacity-50">
          비밀번호 바꾸기
        </button>
      </form>
    </section>
  )
}
