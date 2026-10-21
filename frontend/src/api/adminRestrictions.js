import { api } from './client'

/** API 명세서 4.4: 노쇼 누적 이용 제한 조회·해제 (NS-03) */
export const fetchRestrictions = (active, page = 1) =>
  api.get('/admin/restrictions', { params: { active, page } }).then((r) => r.data)
export const liftRestriction = (id, reason) => api.post(`/admin/restrictions/${id}/lift`, { reason }).then((r) => r.data)
