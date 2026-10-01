import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { ApiError, request, type RequestOptions } from '../../api'
import { AuthContext, type Session } from './useAuth'

interface LoginResponse {
  accessToken: string
  accountName: string
  displayName: string
}

// sessionStorage keeps the login across a reload but not across tabs, so two tabs can be two users
const STORAGE_KEY = 'im.session'

function loadSession(): Session | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as Session) : null
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(loadSession)

  const logout = useCallback(() => {
    sessionStorage.removeItem(STORAGE_KEY)
    setSession(null)
  }, [])

  const login = useCallback(async (identifier: string, password: string) => {
    const res = await request<LoginResponse>('/auth/login', {
      method: 'POST',
      body: { identifier, password },
    })
    const next = { token: res.accessToken, accountName: res.accountName, displayName: res.displayName }
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(next))
    setSession(next)
  }, [])

  const token = session?.token
  const authed = useCallback(
    async <T,>(path: string, options: RequestOptions = {}) => {
      try {
        return await request<T>(path, { ...options, token })
      } catch (e) {
        if (e instanceof ApiError && e.status === 401) logout()
        throw e
      }
    },
    [token, logout],
  )

  const value = useMemo(() => ({ session, login, logout, authed }), [session, login, logout, authed])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
