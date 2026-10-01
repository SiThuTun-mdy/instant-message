export interface User {
  accountName: string
  displayName: string
}

export interface ChatMessage {
  id: string
  from: string
  to: string
  body: string
  sentAt: string
  /** Only set on messages this client sent: absent once the server has acked. */
  status?: 'pending' | 'failed'
}

export interface SendFrame {
  type: 'send'
  clientMessageId: string
  to: string
  body: string
}

export type ServerFrame =
  | { type: 'ack'; clientMessageId: string; sentAt: string }
  | ({ type: 'message' } & Omit<ChatMessage, 'status'>)
  | { type: 'error'; clientMessageId: string | null; code: string }
