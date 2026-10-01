import { BrowserRouter, Navigate, Route, Routes } from 'react-router'
import { AuthProvider, useAuth } from './features/auth/AuthContext'
import LoginPage from './features/auth/LoginPage'
import SignupPage from './features/auth/SignupPage'
import ChatPage from './features/chat/ChatPage'

function AppRoutes() {
  const { session } = useAuth()
  // The key gives each login a fresh chat page, so no state leaks between accounts
  const chat = session ? <ChatPage key={session.accountName} session={session} /> : <Navigate to="/login" replace />
  const toChat = <Navigate to="/chat" replace />

  return (
    <Routes>
      <Route path="/login" element={session ? toChat : <LoginPage />} />
      <Route path="/signup" element={session ? toChat : <SignupPage />} />
      <Route path="/chat" element={chat} />
      <Route path="/chat/:accountName" element={chat} />
      <Route path="*" element={toChat} />
    </Routes>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </BrowserRouter>
  )
}
