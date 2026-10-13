const STYLES = {
  error: 'border-red-200 bg-red-50 text-red-700',
  info: 'border-indigo-200 bg-indigo-50 text-indigo-800',
  success: 'border-green-200 bg-green-50 text-green-800',
}

export default function Alert({ kind = 'error', children }) {
  if (!children) return null
  return (
    <p role={kind === 'error' ? 'alert' : 'status'} className={`rounded-md border px-3 py-2 text-sm ${STYLES[kind]}`}>
      {children}
    </p>
  )
}
