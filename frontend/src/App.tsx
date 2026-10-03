import { Navigate, Route, Routes } from 'react-router-dom'
import { AppShell } from './shared/layout/AppShell'
import { StudentLayout } from './pages/sv/components/StudentLayout'
import { StudentFlowProvider } from './pages/sv/state/StudentFlowContext'
import { StudentSelectionPage } from './pages/sv/StudentSelectionPage'
import { StudentRecommendationPage } from './pages/sv/StudentRecommendationPage'
import { StudentRoutePage } from './pages/sv/route/StudentRoutePage'

function Screen({ title }: { title: string }) {
  return <section className="screen"><h1>{title}</h1></section>
}

function StudentRoutes() {
  return <StudentFlowProvider><Routes><Route element={<StudentLayout />}><Route index element={<StudentSelectionPage />} /><Route path="recommendation" element={<StudentRecommendationPage />} /><Route path="route" element={<StudentRoutePage />} /></Route></Routes></StudentFlowProvider>
}

export default function App() {
  return <Routes>
    <Route path="sv/*" element={<StudentRoutes />} />
    <Route element={<AppShell />}>
      <Route index element={<Screen title="Bảng điều khiển" />} />
      <Route path="forecast" element={<Screen title="Dự báo & Đề xuất" />} />
      <Route path="assistant" element={<Screen title="Trợ lý" />} />
      <Route path="import" element={<Screen title="Nhập dữ liệu" />} />
      <Route path="*" element={<Navigate replace to="/" />} />
    </Route>
  </Routes>
}
