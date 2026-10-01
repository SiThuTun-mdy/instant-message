import { createContext, useContext } from 'react'
import type { RequestOptions } from '../../api'

export interface Session {
  token: string
  accountName: string
  displayName: string
}

export interface AuthValue {
  session: Session | null
  login: (identifier: string, password: string) => Promise<void>
  logout: () => void
  /** Calls the API as the logged-in user; an expired token logs the user out. */
  authed: <T>(path: string, options?: RequestOptions) => Promise<T>
}

export const AuthContext = createContext<AuthValue | null>(null)

export function useAuth(): AuthValue {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth must be used inside AuthProvider')
  return value
}
