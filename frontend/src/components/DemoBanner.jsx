import { IS_DEMO, SURVEY_URL, tokenStorage } from '../api/client'

/** 설문용 데모 안내. 데이터는 방문자 브라우저에만 저장돼요 */
export default function DemoBanner() {
  if (!IS_DEMO) return null

  async function reset() {
    const { resetDemo } = await import('../api/demo/store.js')
    resetDemo()
    tokenStorage.clear()
    window.location.assign('/')
  }

  return (
    <div className="bg-amber-50 text-amber-900">
      <div className="mx-auto flex max-w-3xl flex-wrap items-center justify-between gap-x-3 gap-y-1 px-4 py-2 text-xs">
        <p>체험용 데모예요. 가짜 데이터이고 이 브라우저에만 저장돼요.</p>
        <div className="flex gap-2">
          {SURVEY_URL && (
            <a href={SURVEY_URL} target="_blank" rel="noreferrer" className="rounded bg-amber-600 px-2 py-1 font-medium text-white">
              설문 참여하기
            </a>
          )}
          <button type="button" onClick={reset} className="rounded border border-amber-400 px-2 py-1">
            데모 초기화
          </button>
        </div>
      </div>
    </div>
  )
}
