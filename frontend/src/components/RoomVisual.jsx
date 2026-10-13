import { roomGradient } from '../utils/slots'

/** 방 대표 이미지 자리. 실제 사진이 없어서 방마다 색과 큰 번호로 구분해요 */
export default function RoomVisual({ room, size = 'md' }) {
  if (size === 'thumb') {
    // 목록용 작은 정사각형 (숙박 앱 목록의 사진 자리)
    return (
      <div aria-hidden="true" className={`flex h-20 w-20 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br ${roomGradient(room.id)} text-3xl font-black text-white shadow-sm`}>
        {room.name.replace(/\D/g, '')}
      </div>
    )
  }
  const tall = size === 'lg' ? 'h-36' : 'h-24'
  return (
    <div className={`relative flex ${tall} items-end overflow-hidden bg-gradient-to-br ${roomGradient(room.id)} p-4 text-white`}>
      <span aria-hidden="true" className="absolute -right-2 -top-6 text-[7rem] font-black leading-none text-white/20">
        {room.name.replace(/\D/g, '')}
      </span>
      <div className="relative">
        <p className={`${size === 'lg' ? 'text-2xl' : 'text-xl'} font-bold drop-shadow-sm`}>{room.name}</p>
        <p className="mt-0.5 inline-flex items-center gap-1 rounded-full bg-white/25 px-2 py-0.5 text-xs font-medium backdrop-blur">
          <PeopleIcon /> 최대 {room.capacity}명
        </p>
      </div>
    </div>
  )
}

export function PeopleIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 20 20" className="h-3.5 w-3.5 fill-current">
      <path d="M7 9a3 3 0 1 0 0-6 3 3 0 0 0 0 6Zm6 0a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5ZM1 16c0-2.8 2.7-5 6-5s6 2.2 6 5v1H1v-1Zm13 1v-1c0-1.4-.5-2.7-1.4-3.7 2.9.1 5.4 1.9 5.4 4.2v.5h-4Z" />
    </svg>
  )
}
