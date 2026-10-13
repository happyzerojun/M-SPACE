import { api } from './client'

/** API 명세서 2장: 현황판·방·예약 규칙 (비회원 허용) */
export const fetchBoard = (date) => api.get('/board', { params: { date } }).then((r) => r.data)
export const fetchRooms = () => api.get('/rooms').then((r) => r.data)
export const fetchPolicy = () => api.get('/policy').then((r) => r.data)
