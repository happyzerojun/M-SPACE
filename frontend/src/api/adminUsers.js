import { api } from './client'

/** API 명세서 4.4: 관리자 회원 관리 */
export const searchUsers = (params) => api.get('/admin/users', { params }).then((r) => r.data)
export const resetPassword = (userId, reason) =>
  api.post(`/admin/users/${userId}/password-reset`, { reason }).then((r) => r.data)
export const disableUser = (userId, reason) => api.post(`/admin/users/${userId}/disable`, { reason }).then((r) => r.data)
