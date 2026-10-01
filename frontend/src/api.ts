export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

export interface RequestOptions {
  method?: 'GET' | 'POST'
  body?: unknown
  token?: string
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = {}
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  if (options.token) headers['Authorization'] = `Bearer ${options.token}`

  const response = await fetch(`/api/v1${path}`, {
    method: options.method ?? 'GET',
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  })

  const text = await response.text()
  // Errors arrive as RFC 9457 problem details; 401 from the resource server has no body
  const data = text ? JSON.parse(text) : undefined
  if (!response.ok) {
    throw new ApiError(response.status, data?.detail ?? `Request failed (${response.status})`)
  }
  return data as T
}
