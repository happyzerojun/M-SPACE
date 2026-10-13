import { addDays, dateLabel } from '../utils/time'

/** 오늘부터 days일 뒤까지 날짜 고르기. 375px에서는 줄바꿈해서 가로 스크롤이 생기지 않아요 */
export default function DateChips({ today, days, value, onChange }) {
  const dates = Array.from({ length: days + 1 }, (_, i) => addDays(today, i))
  return (
    <div className="flex flex-wrap gap-1.5" role="group" aria-label="날짜 선택">
      {dates.map((d, i) => (
        <button key={d} type="button" onClick={() => onChange(d)} aria-pressed={d === value}
          className={`rounded-full border px-3 py-1 text-sm ${d === value ? 'border-indigo-600 bg-indigo-600 text-white' : 'border-gray-300 bg-white text-gray-700'}`}>
          {i === 0 ? '오늘' : i === 1 ? '내일' : dateLabel(d)}
        </button>
      ))}
    </div>
  )
}
