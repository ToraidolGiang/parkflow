import { Navigate, Route, Routes } from 'react-router-dom'
import { AppShell } from './shared/layout/AppShell'

function Screen({ title }: { title: string }) {
  return <section className="screen"><h1>{title}</h1></section>
}

export default function App() {
  return <Routes>
    <Route element={<AppShell />}>
      <Route index element={<Screen title="Bảng điều khiển" />} />
      <Route path="forecast" element={<Screen title="Dự báo & Đề xuất" />} />
      <Route path="sv" element={<Screen title="Sinh viên" />} />
      <Route path="assistant" element={<Screen title="Trợ lý" />} />
      <Route path="import" element={<Screen title="Nhập dữ liệu" />} />
      <Route path="*" element={<Navigate replace to="/" />} />
    </Route>
  </Routes>
}
