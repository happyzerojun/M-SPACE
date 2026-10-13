import { Navigate, useSearchParams } from 'react-router'

/** 예전 예약 주소(/reserve?roomId&date&start)는 방 화면으로 옮겼어요. 공유된 링크가 깨지지 않게 넘겨줘요 */
export default function ReservePage() {
  const [params] = useSearchParams()
  const roomId = params.get('roomId')
  if (!roomId) return <Navigate to="/" replace />
  const next = new URLSearchParams(params)
  next.delete('roomId')
  return <Navigate to={`/rooms/${roomId}?${next}`} replace />
}
