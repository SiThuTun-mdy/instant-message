import { useState, type FormEvent } from 'react'
import { Link, useLocation } from 'react-router'
import { useAuth } from './useAuth'

export default function LoginPage() {
  const { login } = useAuth()
  const location = useLocation()
  const justSignedUp = (location.state as { signedUp?: boolean } | null)?.signedUp
  const [identifier, setIdentifier] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      // On success the route guard in App redirects to /chat
      await login(identifier, password)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed')
      setBusy(false)
    }
  }

  return (
    <main className="auth">
      <form className="card" onSubmit={onSubmit}>
        <h1>Log in</h1>
        {justSignedUp && <p className="notice">Account created. Log in to start chatting.</p>}
        <label>
          Account name or email
          <input value={identifier} onChange={(e) => setIdentifier(e.target.value)} autoFocus required />
        </label>
        <label>
          Password
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        </label>
        {error && <p className="error">{error}</p>}
        <button type="submit" disabled={busy}>
          Log in
        </button>
        <p className="hint">
          No account? <Link to="/signup">Sign up</Link>
        </p>
      </form>
    </main>
  )
}
