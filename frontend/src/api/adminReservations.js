import { api } from './client'

/** API 명세서 4.1: 관리자 예약·키 */
export const fetchDashboard = (date) => api.get('/admin/dashboard', { params: { date } }).then((r) => r.data)
export const fetchDayReservations = (date) =>
  api.get('/admin/reservations', { params: { date, size: 100 } }).then((r) => r.data)
export const issueKey = (id) => api.post(`/admin/reservations/${id}/key-issue`, { identityVerified: true }).then((r) => r.data)
export const returnKey = (id) => api.post(`/admin/reservations/${id}/key-return`, {}).then((r) => r.data)
export const adminCancel = (id, reason) => api.post(`/admin/reservations/${id}/cancel`, { reason }).then((r) => r.data)
