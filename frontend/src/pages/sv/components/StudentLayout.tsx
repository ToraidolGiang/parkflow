import { Outlet } from 'react-router-dom'
import '../student.css'
import '../student-detail.css'
import '../student-route.css'

export function StudentLayout() {
  return <div className="student-app"><header className="student-header"><div className="student-brand"><img src="/assets/parkflow-brand.png" alt=""/><div><strong>ParkFlow</strong><span>Gợi ý gửi xe trong khuôn viên</span></div></div><span className="student-live">Dữ liệu mô phỏng</span></header><main className="student-main"><Outlet /></main></div>
}
