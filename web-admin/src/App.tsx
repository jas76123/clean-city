import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from '@/auth/AuthContext'
import { ProtectedRoute } from '@/auth/ProtectedRoute'
import { LoginPage } from '@/pages/LoginPage'
import { AcceptInvitePage } from '@/pages/AcceptInvitePage'
import { ForgotPasswordPage } from '@/pages/ForgotPasswordPage'
import { ResetPasswordPage } from '@/pages/ResetPasswordPage'
import { SectionPlaceholder } from '@/pages/placeholders/SectionPlaceholder'
import { ComplaintsPage } from '@/pages/ComplaintsPage'
import { OverviewPage } from '@/pages/OverviewPage'
import { AnalyticsPage } from '@/pages/AnalyticsPage'

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/accept-invite" element={<AcceptInvitePage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />
          <Route element={<ProtectedRoute />}>
            <Route path="/" element={<Navigate to="/overview" replace />} />
            <Route path="/overview" element={<OverviewPage />} />
            <Route path="/complaints" element={<ComplaintsPage />} />
            <Route path="/announcements" element={<SectionPlaceholder title="Объявления" />} />
            <Route path="/analytics" element={<AnalyticsPage />} />
            <Route path="/settings" element={<SectionPlaceholder title="Настройки" />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}
