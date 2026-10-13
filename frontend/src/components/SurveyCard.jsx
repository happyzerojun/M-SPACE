import { IS_DEMO, SURVEY_URL } from '../api/client'

/** 설문용 데모에서만 보여요. 체험 직후에 의견을 받으려고 예약 완료·홈 아래에 둬요 */
export default function SurveyCard() {
  if (!IS_DEMO || !SURVEY_URL) return null
  return (
    <a href={SURVEY_URL} target="_blank" rel="noreferrer"
      className="flex items-center justify-between gap-3 rounded-2xl bg-gradient-to-r from-amber-400 to-orange-400 p-4 text-white shadow-sm">
      <span>
        <span className="block font-semibold">써 보니 어땠나요?</span>
        <span className="block text-sm text-white/90">2분 설문으로 의견을 들려주세요</span>
      </span>
      <span className="shrink-0 rounded-full bg-white px-3 py-1.5 text-sm font-semibold text-orange-600">설문 참여</span>
    </a>
  )
}
