import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { request } from '../../api'

/** Suggests an account name from the email prefix, limited to what the server accepts. */
function suggestAccountName(email: string): string {
  return email
    .split('@')[0]
    .toLowerCase()
    .replace(/[^a-z0-9_]/g, '_')
    .slice(0, 20)
}

export default function SignupPage() {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [accountName, setAccountName] = useState('')
  const [accountNameEdited, setAccountNameEdited] = useState(false)
  const [displayName, setDisplayName] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  function onEmailChange(value: string) {
    setEmail(value)
    if (!accountNameEdited) setAccountName(suggestAccountName(value))
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      await request('/auth/signup', {
        method: 'POST',
        body: { email, accountName, displayName, password },
      })
      navigate('/login', { state: { signedUp: true } })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Sign-up failed')
      setBusy(false)
    }
  }

  return (
    <main className="auth">
      <form className="card" onSubmit={onSubmit}>
        <h1>Sign up</h1>
        <label>
          Gmail address
          <input
            type="email"
            value={email}
            onChange={(e) => onEmailChange(e.target.value)}
            pattern="[A-Za-z0-9._%+\-]+@[Gg][Mm][Aa][Ii][Ll]\.[Cc][Oo][Mm]"
            title="Must be a @gmail.com address"
            autoFocus
            required
          />
        </label>
        <label>
          Account name
          <input
            value={accountName}
            onChange={(e) => {
              setAccountNameEdited(true)
              setAccountName(e.target.value.toLowerCase())
            }}
            pattern="[a-z0-9_]{3,20}"
            title="3-20 characters: lowercase letters, digits or underscore"
            required
          />
        </label>
        <label>
          Display name
          <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} maxLength={50} required />
        </label>
        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            minLength={8}
            maxLength={72}
            required
          />
        </label>
        {error && <p className="error">{error}</p>}
        <button type="submit" disabled={busy}>
          Create account
        </button>
        <p className="hint">
          Already have an account? <Link to="/login">Log in</Link>
        </p>
      </form>
    </main>
  )
}
