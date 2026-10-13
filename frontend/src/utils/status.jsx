/** 예약 상태 표시 (displayStatus 기준, docs/예약-상태-설계.md) */
const STATUS = {
  RESERVED: { label: '예약됨', className: 'bg-indigo-100 text-indigo-800' },
  IN_USE: { label: '이용 중', className: 'bg-green-100 text-green-800' },
  OVERDUE: { label: '반납 지연', className: 'bg-red-100 text-red-700' },
  RETURNED: { label: '반납 완료', className: 'bg-gray-100 text-gray-600' },
  COMPLETED: { label: '이용 완료', className: 'bg-gray-100 text-gray-600' },
  CANCELLED: { label: '취소', className: 'bg-gray-100 text-gray-500 line-through' },
  NO_SHOW: { label: '노쇼', className: 'bg-amber-100 text-amber-800' },
}

export function StatusBadge({ status }) {
  const s = STATUS[status] ?? { label: status, className: 'bg-gray-100' }
  return <span className={`whitespace-nowrap rounded px-1.5 py-0.5 text-xs font-medium ${s.className}`}>{s.label}</span>
}
