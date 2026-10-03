import { Navigate, Route, Routes } from 'react-router-dom'
import { AppShell } from './shared/layout/AppShell'
import { StudentLayout } from './pages/sv/components/StudentLayout'
import { StudentFlowProvider } from './pages/sv/state/StudentFlowContext'
import { StudentSelectionPage } from './pages/sv/StudentSelectionPage'
import { StudentRecommendationPage } from './pages/sv/StudentRecommendationPage'
import { StudentRoutePage } from './pages/sv/route/StudentRoutePage'
import { ForecastPage } from './pages/forecast/ForecastPage'
import { ImportPage } from './pages/import/ImportPage'
import { AssistantPage } from './pages/assistant/AssistantPage'
import { RulesPage } from './pages/rules/RulesPage'
import { SettingsPage } from './pages/settings/SettingsPage'
import { DashboardPage } from './pages/dashboard/DashboardPage'

function StudentRoutes() {
  return <StudentFlowProvider><Routes><Route element={<StudentLayout />}><Route index element={<StudentSelectionPage />} /><Route path="recommendation" element={<StudentRecommendationPage />} /><Route path="route" element={<StudentRoutePage />} /></Route></Routes></StudentFlowProvider>
}

export default function App() {
  return <Routes>
    <Route path="sv/*" element={<StudentRoutes />} />
    <Route element={<AppShell />}>
      <Route index element={<DashboardPage />} />
      <Route path="forecast" element={<ForecastPage />} />
      <Route path="assistant" element={<AssistantPage />} />
      <Route path="import" element={<ImportPage />} />
      <Route path="rules" element={<RulesPage />} />
      <Route path="settings" element={<SettingsPage />} />
      <Route path="*" element={<Navigate replace to="/" />} />
    </Route>
  </Routes>
}
