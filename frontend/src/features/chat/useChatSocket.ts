import { useCallback, useEffect, useRef, useState } from 'react'
import type { SendFrame, ServerFrame } from './types'

export type SocketStatus = 'connecting' | 'open' | 'closed'

interface Handlers {
  onFrame: (frame: ServerFrame) => void
  /** The connection dropped; anything unacked may or may not have been stored. */
  onDisconnect: () => void
  /** The connection is back after a drop; frames pushed in between were missed. */
  onReconnect: () => void
}

const MAX_RETRY_DELAY_MS = 10_000

/** Owns the single WebSocket for a logged-in user and reconnects with backoff. */
export function useChatSocket(token: string, handlers: Handlers) {
  const [status, setStatus] = useState<SocketStatus>('connecting')
  const socketRef = useRef<WebSocket | null>(null)
  const handlersRef = useRef(handlers)

  useEffect(() => {
    handlersRef.current = handlers
  })

  useEffect(() => {
    let stopped = false
    let attempt = 0
    let hasOpened = false
    let retryTimer: number | undefined

    function connect() {
      const scheme = location.protocol === 'https:' ? 'wss' : 'ws'
      const socket = new WebSocket(`${scheme}://${location.host}/ws?token=${encodeURIComponent(token)}`)
      socketRef.current = socket

      socket.onopen = () => {
        attempt = 0
        setStatus('open')
        if (hasOpened) handlersRef.current.onReconnect()
        hasOpened = true
      }
      socket.onmessage = (event) => {
        handlersRef.current.onFrame(JSON.parse(event.data) as ServerFrame)
      }
      socket.onclose = () => {
        if (stopped) return
        setStatus('closed')
        handlersRef.current.onDisconnect()
        retryTimer = window.setTimeout(connect, Math.min(1000 * 2 ** attempt++, MAX_RETRY_DELAY_MS))
      }
    }

    connect()
    return () => {
      stopped = true
      window.clearTimeout(retryTimer)
      socketRef.current?.close()
    }
  }, [token])

  /** Returns false when the frame could not be handed to an open socket. */
  const send = useCallback((frame: SendFrame): boolean => {
    const socket = socketRef.current
    if (socket?.readyState !== WebSocket.OPEN) return false
    socket.send(JSON.stringify(frame))
    return true
  }, [])

  return { status, send }
}
