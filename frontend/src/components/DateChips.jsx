import { addDays, dateLabel } from '../utils/time'

/** 오늘부터 days일 뒤까지 날짜 고르기. 가로 스크롤 대신 4칸 격자로 줄을 바꿔요 (375px 규칙) */
export default function DateChips({ today, days, value, onChange }) {
  const dates = Array.from({ length: days + 1 }, (_, i) => addDays(today, i))
  return (
    <div className="grid grid-cols-4 gap-2" role="group" aria-label="날짜 선택">
      {dates.map((d, i) => {
        const [md, weekday] = dateLabel(d).split(' ')
        const selected = d === value
        return (
          <button key={d} type="button" onClick={() => onChange(d)} aria-pressed={selected}
            className={`rounded-xl border px-1 py-1.5 text-center transition ${
              selected ? 'border-indigo-600 bg-indigo-600 text-white shadow-sm' : 'border-gray-200 bg-white text-gray-700'
            }`}>
            <span className="block text-sm font-semibold">{i === 0 ? '오늘' : i === 1 ? '내일' : md}</span>
            <span className={`block text-[11px] ${selected ? 'text-indigo-100' : 'text-gray-400'}`}>
              {i < 2 ? md : weekday.replace(/[()]/g, '')}
            </span>
          </button>
        )
      })}
    </div>
  )
}
