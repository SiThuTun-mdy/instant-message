import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ApiError } from '../../api'
import { useAuth, type Session } from '../auth/AuthContext'
import type { ChatMessage, ServerFrame, User } from './types'
import { useChatSocket } from './useChatSocket'

const ERROR_TEXT: Record<string, string> = {
  recipient_not_found: 'That user no longer exists.',
  body_too_long: 'Message is too long (2000 characters max).',
  invalid_recipient: 'You cannot message yourself.',
}

/** Server history plus anything local it does not contain yet, oldest first. */
function mergeHistory(history: ChatMessage[], local: ChatMessage[]): ChatMessage[] {
  const known = new Set(history.map((m) => m.id))
  return [...history, ...local.filter((m) => !known.has(m.id))].sort((a, b) =>
    a.sentAt.localeCompare(b.sentAt),
  )
}

export default function ChatPage({ session }: { session: Session }) {
  const { authed, logout } = useAuth()
  const navigate = useNavigate()
  const peer = useParams().accountName?.toLowerCase()

  const [peers, setPeers] = useState<User[]>([])
  const [unread, setUnread] = useState<Set<string>>(new Set())
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<User[]>([])
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [loadError, setLoadError] = useState('')
  const [sendError, setSendError] = useState('')
  const [draft, setDraft] = useState('')
  // Bumped after a reconnect to refetch whatever was pushed while offline
  const [syncEpoch, setSyncEpoch] = useState(0)

  const peerRef = useRef(peer)
  useEffect(() => {
    peerRef.current = peer
  })

  const refreshPeers = useCallback(() => {
    authed<User[]>('/conversations')
      .then((list) =>
        // Keep people picked from search who have no messages yet
        setPeers((prev) => [
          ...list,
          ...prev.filter((p) => !list.some((l) => l.accountName === p.accountName)),
        ]),
      )
      .catch(() => {})
  }, [authed])

  useEffect(refreshPeers, [refreshPeers, syncEpoch])

  // Conversation history: on opening a chat and again after each reconnect
  useEffect(() => {
    if (!peer) return
    let cancelled = false
    authed<ChatMessage[]>(`/conversations/${encodeURIComponent(peer)}/messages?limit=100`)
      .then((newestFirst) => {
        if (cancelled) return
        setLoadError('')
        setMessages((local) => mergeHistory(newestFirst.reverse(), local))
      })
      .catch((e) => {
        if (cancelled) return
        setLoadError(e instanceof ApiError && e.status === 404 ? 'No such user.' : 'Could not load messages.')
      })
    return () => {
      cancelled = true
    }
  }, [authed, peer, syncEpoch])

  // Switching conversation starts from an empty list
  const [shownPeer, setShownPeer] = useState(peer)
  if (shownPeer !== peer) {
    setShownPeer(peer)
    setMessages([])
    setLoadError('')
    setSendError('')
    setDraft('')
    if (peer) setUnread((prev) => new Set([...prev].filter((name) => name !== peer)))
  }

  const { status, send } = useChatSocket(session.token, {
    onFrame(frame: ServerFrame) {
      if (frame.type === 'ack') {
        setMessages((prev) =>
          prev.map((m) =>
            m.id === frame.clientMessageId ? { ...m, status: undefined, sentAt: frame.sentAt } : m,
          ),
        )
      } else if (frame.type === 'error') {
        setMessages((prev) => prev.map((m) => (m.id === frame.clientMessageId ? { ...m, status: 'failed' } : m)))
        setSendError(ERROR_TEXT[frame.code] ?? 'Message could not be sent.')
      } else if (frame.from === peerRef.current) {
        const { type: _type, ...message } = frame
        setMessages((prev) => (prev.some((m) => m.id === message.id) ? prev : [...prev, message]))
      } else {
        setUnread((prev) => new Set(prev).add(frame.from))
        refreshPeers()
      }
    },
    onDisconnect() {
      // Unknown outcome: if the server did store one, the history refetch on reconnect restores it
      setMessages((prev) => prev.map((m) => (m.status === 'pending' ? { ...m, status: 'failed' } : m)))
    },
    onReconnect() {
      setSyncEpoch((n) => n + 1)
    },
  })

  // User search, debounced
  useEffect(() => {
    const q = query.trim()
    if (!q) return
    let cancelled = false
    const timer = window.setTimeout(() => {
      authed<User[]>(`/users/search?q=${encodeURIComponent(q)}`)
        .then((found) => !cancelled && setResults(found))
        .catch(() => {})
    }, 300)
    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [authed, query])

  const bottomRef = useRef<HTMLDivElement>(null)
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: 'end' })
  }, [messages])

  function openChat(user: User) {
    setPeers((prev) => (prev.some((p) => p.accountName === user.accountName) ? prev : [...prev, user]))
    setQuery('')
    navigate(`/chat/${user.accountName}`)
  }

  /** Sends a new message, or resends a failed one under its original id so it cannot duplicate. */
  function deliver(message: ChatMessage) {
    setSendError('')
    const sent = send({ type: 'send', clientMessageId: message.id, to: message.to, body: message.body })
    const next: ChatMessage = { ...message, status: sent ? 'pending' : 'failed' }
    setMessages((prev) =>
      prev.some((m) => m.id === next.id) ? prev.map((m) => (m.id === next.id ? next : m)) : [...prev, next],
    )
    if (!sent) setSendError('Not connected. Retry once the connection is back.')
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault()
    const body = draft.trim()
    if (!body || !peer) return
    deliver({
      id: crypto.randomUUID(),
      from: session.accountName,
      to: peer,
      body,
      sentAt: new Date().toISOString(),
    })
    setDraft('')
  }

  const searching = query.trim() !== ''
  const listed = searching ? results : peers
  const peerUser = peers.find((p) => p.accountName === peer)

  return (
    <div className="chat">
      <aside className="sidebar">
        <header>
          <div>
            <strong>{session.displayName}</strong>
            <span className="muted">@{session.accountName}</span>
          </div>
          <button type="button" className="link" onClick={logout}>
            Log out
          </button>
        </header>
        <input
          type="search"
          placeholder="Find people by account name"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <ul>
          {listed.map((user) => (
            <li key={user.accountName}>
              {searching ? (
                <button type="button" className="row" onClick={() => openChat(user)}>
                  <UserLabel user={user} />
                </button>
              ) : (
                <Link className={user.accountName === peer ? 'row active' : 'row'} to={`/chat/${user.accountName}`}>
                  <UserLabel user={user} />
                  {unread.has(user.accountName) && <span className="dot" aria-label="New messages" />}
                </Link>
              )}
            </li>
          ))}
          {listed.length === 0 && (
            <li className="muted empty">{searching ? 'No one found.' : 'Search for someone to start a chat.'}</li>
          )}
        </ul>
      </aside>

      <section className="conversation">
        {status !== 'open' && (
          <div className="banner" role="status">
            {status === 'connecting' ? 'Connecting…' : 'Connection lost. Reconnecting…'}
          </div>
        )}
        {!peer ? (
          <p className="muted placeholder">Pick a conversation or search for someone.</p>
        ) : (
          <>
            <header>
              <strong>{peerUser?.displayName ?? peer}</strong>
              <span className="muted">@{peer}</span>
            </header>
            <div className="messages">
              {loadError && <p className="error">{loadError}</p>}
              {!loadError && messages.length === 0 && <p className="muted placeholder">No messages yet.</p>}
              {messages.map((m) => (
                <div key={m.id} className={m.from === session.accountName ? 'bubble mine' : 'bubble'}>
                  <p>{m.body}</p>
                  <small>
                    {m.status === 'pending' && 'Sending…'}
                    {m.status === 'failed' && (
                      <>
                        Not sent ·{' '}
                        <button type="button" className="link" onClick={() => deliver(m)}>
                          Retry
                        </button>
                      </>
                    )}
                    {!m.status && new Date(m.sentAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                  </small>
                </div>
              ))}
              <div ref={bottomRef} />
            </div>
            {sendError && <p className="error send-error">{sendError}</p>}
            <form className="composer" onSubmit={onSubmit}>
              <input
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                placeholder={`Message @${peer}`}
                maxLength={2000}
                disabled={!!loadError}
                autoFocus
              />
              <button type="submit" disabled={!draft.trim() || !!loadError}>
                Send
              </button>
            </form>
          </>
        )}
      </section>
    </div>
  )
}

function UserLabel({ user }: { user: User }) {
  return (
    <span>
      <strong>{user.displayName}</strong>
      <span className="muted">@{user.accountName}</span>
    </span>
  )
}
