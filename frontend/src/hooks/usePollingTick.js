import { useEffect, useState } from 'react'

/**
 * intervalMs마다 1씩 오르는 값. useAsync의 key에 넣으면 주기적으로 다시 불러와요.
 * 탭이 안 보이면 멈추고, 다시 보이면 바로 한 번 갱신해요 (현황판 30초 폴링, CLAUDE.md 7장)
 */
export function usePollingTick(intervalMs = 30_000) {
  const [tick, setTick] = useState(0)
  useEffect(() => {
    let timer = null
    const start = () => {
      clearInterval(timer)
      timer = setInterval(() => setTick((t) => t + 1), intervalMs)
    }
    const onVisibility = () => {
      if (document.hidden) {
        clearInterval(timer)
      } else {
        setTick((t) => t + 1)
        start()
      }
    }
    if (!document.hidden) start()
    document.addEventListener('visibilitychange', onVisibility)
    return () => {
      clearInterval(timer)
      document.removeEventListener('visibilitychange', onVisibility)
    }
  }, [intervalMs])
  return tick
}
