/** 라벨 + 입력칸 + 입력칸별 에러 (API fieldErrors). 375px에서도 한 줄 전체를 써요 */
export default function FormField({ label, name, error, hint, ...inputProps }) {
  const id = `field-${name}`
  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium text-gray-700">
        {label}
      </label>
      <input
        id={id}
        name={name}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? `${id}-error` : undefined}
        className={`mt-1 block w-full rounded-md border px-3 py-2 text-base focus:outline-none focus:ring-2 focus:ring-indigo-500 ${
          error ? 'border-red-400' : 'border-gray-300'
        }`}
        {...inputProps}
      />
      {hint && !error && <p className="mt-1 text-xs text-gray-500">{hint}</p>}
      {error && (
        <p id={`${id}-error`} className="mt-1 text-xs text-red-600">
          {error}
        </p>
      )}
    </div>
  )
}
