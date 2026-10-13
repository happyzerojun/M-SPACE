import { use } from 'react'
import { AuthContext } from './authContext'

export function useAuth() {
  return use(AuthContext)
}
