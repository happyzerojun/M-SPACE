import { useEffect, useState } from 'react'

/** 지금 시각(ms)을 intervalMs마다 다시 알려줘요. 렌더링 중에 Date.now()를 직접 읽지 않으려고 써요 (lint 규칙) */
export function useNow(intervalMs = 30_000) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), intervalMs)
    return () => clearInterval(timer)
  }, [intervalMs])
  return now
}
