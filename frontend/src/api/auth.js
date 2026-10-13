import { api } from './client'

/** API 명세서 1장: 회원가입·로그인·내 정보 */
export const signup = (body) => api.post('/auth/signup', body).then((r) => r.data)
export const login = (body) => api.post('/auth/login', body).then((r) => r.data)
/** 새로고침 때 로그인 복원용: 실패하면 로그인 화면으로 보내지 않고 조용히 비회원이 돼요 */
export const fetchMe = (options = {}) => api.get('/me', options).then((r) => r.data)
export const updateMe = (body) => api.patch('/me', body).then((r) => r.data)
export const changePassword = (body) => api.put('/me/password', body)
