import { dateLabel, kstToday, timeOf, toMs } from '../utils/time'

/**
 * 예약 완료 안내 (NT-03). 학생이 규칙을 가장 잘 읽는 순간이라 노쇼 원칙을 여기서 알려줘요.
 * 숫자는 운영 정책(/api/policy) 값을 써요 (코드 상수 금지).
 */
export default function ReservationDone({ r, policy }) {
  const before = policy?.keyIssueBeforeMinutes ?? 10
  const grace = policy?.noShowGraceMinutes ?? 15
  const windowWeeks = (policy?.penaltyWindowDays ?? 14) / 7
  const penaltyWindow = Number.isInteger(windowWeeks) ? `${windowWeeks}주` : `${policy?.penaltyWindowDays}일`
  const rules = r.keyRequired
    ? [
        // 학생증 보관 여부(D-6)는 아직 미정이고 설문(Q13)에서 묻는 내용이라 여기서 정하지 않아요
        <>시작 <b>{before}분 전</b>부터 학과실에서 <b>실물 학생증</b>을 보여주고 키를 받아요.</>,
        <>시작 <b>{grace}분</b>이 지나도 키를 받지 않으면 <b className="text-rose-600">노쇼</b>로 처리되고, 그 시간은 다른 사람이 예약할 수 있게 풀려요.</>,
        <>최근 <b>{penaltyWindow}</b> 안에 노쇼가 <b>{policy?.penaltyThreshold ?? 2}번</b>이면 <b className="text-rose-600">{policy?.penaltyDays ?? 7}일 동안 예약할 수 없어요.</b></>,
        <>못 오게 되면 <b>시작 전에 이 화면에서 취소</b>해 주세요. 미리 취소하면 노쇼가 아니에요.</>,
        <>끝나면 종료 시각까지 학과실에 키를 반납해요. 늦으면 반납 지연으로 기록돼요.</>,
      ]
    : [
        <>키 없이 이용하는 기간이에요. 시작 <b>{before}분 전</b>부터 <b>{grace}분 후</b>까지 이 화면에서 <b>"이용 시작"</b>을 눌러요.</>,
        <><b>{grace}분</b>이 지나도 "이용 시작"을 누르지 않으면 <b className="text-rose-600">노쇼</b>로 처리되고, 그 시간은 다른 사람에게 풀려요.</>,
        <>최근 <b>{penaltyWindow}</b> 안에 노쇼가 <b>{policy?.penaltyThreshold ?? 2}번</b>이면 <b className="text-rose-600">{policy?.penaltyDays ?? 7}일 동안 예약할 수 없어요.</b></>,
        <>못 오게 되면 <b>시작 전에 취소</b>해 주세요. 다 쓰면 "이용 종료"를 누르면 남은 시간이 다른 사람에게 풀려요.</>,
      ]

  return (
    <section aria-label="예약 완료" className="overflow-hidden rounded-2xl bg-white shadow-sm ring-1 ring-indigo-100">
      <div className="bg-indigo-600 px-4 py-4 text-white">
        <p className="text-lg font-bold">✓ 예약이 완료됐어요</p>
        <p className="mt-0.5 text-sm text-indigo-100 tabular-nums">
          {r.room.name} · {dateLabel(kstToday(toMs(r.startAt)))} {timeOf(r.startAt)}~{timeOf(r.endAt)} · {r.headcount}명
        </p>
      </div>
      <div className="px-4 py-3">
        <p className="text-sm font-semibold text-gray-900">꼭 확인해 주세요</p>
        <ul className="mt-2 space-y-2 text-sm text-gray-700">
          {rules.map((rule, i) => (
            <li key={i} className="flex gap-2">
              <span aria-hidden="true" className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-indigo-50 text-xs font-semibold text-indigo-600">{i + 1}</span>
              <span>{rule}</span>
            </li>
          ))}
        </ul>
      </div>
    </section>
  )
}
