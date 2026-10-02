import { Route, Routes } from 'react-router-dom'

/** Khung trắng: các màn hình được thêm trong giờ thi (xem docs/ke-hoach-ngay-thi.md). */
export default function App() {
  return (
    <Routes>
      <Route path="*" element={<main className="blank"><h1>ParkFlow</h1><p>Khung trắng, chưa có màn hình nào.</p></main>} />
    </Routes>
  )
}
