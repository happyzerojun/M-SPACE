import { useEffect, useEffectEvent, useState } from 'react'
import { errorMessage } from '../api/client'

/**
 * key가 바뀔 때마다 load()를 다시 불러요. 늦게 도착한 예전 응답은 버려요.
 * 반환: { data, error, loading, status, setData }  (status: 에러 HTTP 상태, 404 화면 처리용)
 */
export function useAsync(load, key) {
  const [result, setResult] = useState({ key: null, data: null, error: null, status: null })
  // load는 렌더링마다 새 함수지만, 다시 부를지는 key로만 정해요 (React 19.2 useEffectEvent)
  const runLoad = useEffectEvent(load)

  useEffect(() => {
    let ignore = false
    runLoad()
      .then((data) => !ignore && setResult({ key, data, error: null, status: null }))
      .catch((err) => !ignore && setResult({ key, data: null, error: errorMessage(err), status: err.response?.status }))
    return () => {
      ignore = true
    }
  }, [key])

  const setData = (updater) =>
    setResult((prev) => ({ ...prev, data: typeof updater === 'function' ? updater(prev.data) : updater }))

  return { ...result, loading: result.key !== key, setData }
}
