import { createContext } from 'react'

/** { user, ready, signIn(loginResponse), signOut(), setUser(user) } */
export const AuthContext = createContext(null)
