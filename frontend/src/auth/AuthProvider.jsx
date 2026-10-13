import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { fetchMe } from '../api/auth'
import { setUnauthorizedHandler, tokenStorage } from '../api/client'
import { AuthContext } from './authContext'

/**
 * 로그인 상태. 새로고침하면 저장된 토큰으로 내 정보를 다시 불러와요 (AU-03).
 * ready가 false인 동안은 로그인 여부를 아직 모르는 상태라 보호된 화면이 기다려요.
 */
export default function AuthProvider({ children }) {
  const navigate = useNavigate()
  const [state, setState] = useState(() => ({ user: null, ready: !tokenStorage.get() }))

  // 토큰이 만료돼 API가 401을 주면 로그아웃하고 로그인 화면으로 보내요 (AU-04)
  useEffect(() => {
    setUnauthorizedHandler(() => {
      setState({ user: null, ready: true })
      navigate('/login', { replace: true })
    })
    return () => setUnauthorizedHandler(() => {})
  }, [navigate])

  useEffect(() => {
    if (!tokenStorage.get()) return undefined
    let ignore = false
    fetchMe({ skipAuthRedirect: true })
      .then((user) => !ignore && setState({ user, ready: true }))
      .catch(() => {
        tokenStorage.clear()
        if (!ignore) setState({ user: null, ready: true })
      })
    return () => {
      ignore = true
    }
  }, [])

  const value = {
    ...state,
    signIn: ({ accessToken, user }) => {
      tokenStorage.set(accessToken)
      setState({ user, ready: true })
    },
    signOut: () => {
      tokenStorage.clear()
      setState({ user: null, ready: true })
      navigate('/', { replace: true })
    },
    setUser: (user) => setState((prev) => ({ ...prev, user })),
  }

  return <AuthContext value={value}>{children}</AuthContext>
}
