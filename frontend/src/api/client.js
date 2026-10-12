import axios from 'axios'

const TOKEN_KEY = 'mspace.accessToken'

/** 액세스 토큰 보관 (AU-03). 로그아웃은 이 값을 지우는 것으로 끝나요 (AU-04) */
export const tokenStorage = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
}

export const api = axios.create({ baseURL: '/api' })

// 요청마다 토큰을 자동으로 붙여요
api.interceptors.request.use((config) => {
  const token = tokenStorage.get()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// 토큰이 없거나 만료되면(401 UNAUTHORIZED) 토큰을 지우고 로그인 화면으로 보내요 (AU-04)
let handleUnauthorized = () => {}
export function setUnauthorizedHandler(handler) {
  handleUnauthorized = handler
}

api.interceptors.response.use(
  (response) => response,
  (error) => {
    // 로그인 실패(LOGIN_FAILED)도 401이지만 로그인 화면이 직접 보여줘야 해서 제외해요
    if (error.response?.status === 401 && error.response.data?.code === 'UNAUTHORIZED' && !error.config?.skipAuthRedirect) {
      tokenStorage.clear()
      handleUnauthorized()
    }
    return Promise.reject(error)
  },
)

/** 사용자에게 보여줄 에러 문장 (서버가 보낸 message, 서버가 응답하지 않으면 기본 문장) */
export function errorMessage(error) {
  return error.response?.data?.message ?? '서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.'
}

/** 입력칸별 에러 { startAt: '30분 단위로 골라 주세요.', ... } (API 명세서 0장 fieldErrors) */
export function fieldErrorsOf(error) {
  return Object.fromEntries((error.response?.data?.fieldErrors ?? []).map((e) => [e.field, e.message]))
}
