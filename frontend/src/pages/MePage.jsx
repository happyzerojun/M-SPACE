import { useState } from 'react'
import { Link } from 'react-router'
import { updateMe } from '../api/auth'
import { errorMessage, fieldErrorsOf } from '../api/client'
import { useAuth } from '../auth/useAuth'
import Alert from '../components/Alert.jsx'
import FormField from '../components/FormField.jsx'

/** AU-05 내 정보 수정. 학번은 바꿀 수 없어요 */
export default function MePage() {
  const { user, setUser, signOut } = useAuth()
  const [form, setForm] = useState({ name: user.name, phone: user.phone })
  const [message, setMessage] = useState(null)
  const [error, setError] = useState(null)
  const [fieldErrors, setFieldErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)

  const change = (e) => setForm({ ...form, [e.target.name]: e.target.value })

  async function submit(e) {
    e.preventDefault()
    setSubmitting(true)
    setMessage(null)
    setError(null)
    setFieldErrors({})
    try {
      const updated = await updateMe(form)
      setUser(updated)
      setForm({ name: updated.name, phone: updated.phone })
      setMessage('저장했어요.')
    } catch (err) {
      setError(errorMessage(err))
      setFieldErrors(fieldErrorsOf(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="mx-auto max-w-sm">
      <h1 className="text-xl font-bold">내 정보</h1>
      <dl className="mt-4 rounded-md border border-gray-200 bg-white px-3 py-2 text-sm">
        <dt className="text-gray-500">학번</dt>
        <dd className="font-medium">{user.studentNo}</dd>
      </dl>
      <form onSubmit={submit} className="mt-4 space-y-4" noValidate>
        <FormField label="이름" name="name" value={form.name} onChange={change} error={fieldErrors.name} />
        <FormField label="대표 전화번호" name="phone" type="tel" inputMode="tel"
          value={form.phone} onChange={change} error={fieldErrors.phone} />
        <Alert>{error}</Alert>
        <Alert kind="success">{message}</Alert>
        <button type="submit" disabled={submitting}
          className="w-full rounded-md bg-indigo-600 py-2.5 font-medium text-white disabled:opacity-50">
          저장
        </button>
      </form>
      <div className="mt-6 flex flex-col gap-2">
        <Link to="/me/password" className="rounded-md border border-gray-300 py-2.5 text-center text-sm">
          비밀번호 변경
        </Link>
        <button type="button" onClick={signOut} className="rounded-md py-2.5 text-sm text-gray-600">
          로그아웃
        </button>
      </div>
    </section>
  )
}
