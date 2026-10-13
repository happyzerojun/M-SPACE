import { api } from './client'

/** API 명세서 3장: 학생 예약 */
export const createReservation = (body) => api.post('/reservations', body).then((r) => r.data)
export const fetchMyReservations = (scope, page = 1) =>
  api.get('/me/reservations', { params: { scope, page } }).then((r) => r.data)
export const cancelReservation = (id) => api.post(`/reservations/${id}/cancel`).then((r) => r.data)
export const checkIn = (id) => api.post(`/reservations/${id}/check-in`).then((r) => r.data)
export const finishUse = (id) => api.post(`/reservations/${id}/finish`).then((r) => r.data)
